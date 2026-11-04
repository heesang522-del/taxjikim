package com.kh.back.chatbot.service.Impl;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.chatbot.dto.ChatbotConversationDto.ConversationResponse;
import com.kh.back.chatbot.dto.ChatbotMessageDto;
import com.kh.back.chatbot.dto.ChatbotMessageDto.MessageResponse;
import com.kh.back.chatbot.dto.ChatbotReply;
import com.kh.back.chatbot.service.ChatbotHistoryService;
import com.kh.back.chatbot.service.ChatbotService;
import com.kh.back.chatbot.service.UserContextProvider;
import com.kh.back.chatbot.util.PiiMasker;
import com.kh.back.chatbot.validator.ChatbotValidator;
import com.kh.back.common.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ChatbotServiceImpl implements ChatbotService {

    private static final int CONTEXT_MESSAGES = 10;

    private final ChatClient chatClient;
    private final ChatbotHistoryService historyService;
    private final UserContextProvider userContextProvider;
    private final ChatbotValidator chatbotValidator;
    private final boolean enabled;

    public ChatbotServiceImpl(
            ChatClient.Builder builder,
            ChatbotHistoryService historyService,
            UserContextProvider userContextProvider,
            ChatbotValidator chatbotValidator,
            @Value("classpath:prompts/Chatbot-System.st")
            Resource systemPrompt,
            @Value("${spring.ai.openai.api-key:no-key}")
            String apiKey
    ) {
        this.historyService = historyService;
        this.userContextProvider = userContextProvider;
        this.chatbotValidator = chatbotValidator;

        this.chatClient = builder
                .defaultSystem(
                        systemPrompt,
                        StandardCharsets.UTF_8
                )
                .build();

        this.enabled = apiKey != null
                && !apiKey.isBlank()
                && !"no-key".equals(apiKey.trim());

        if (!enabled) {
            log.warn("API 키가 없어 챗봇 호출을 비활성화합니다.");
        }
    }

    @Override
    public List<ConversationResponse> getConversations(
            CustomUserDetails principal
    ) {
        return historyService.getConversations(
                principal.getUserId()
        );
    }

    @Override
    public ConversationResponse createConversation(
            CustomUserDetails principal
    ) {
        return historyService.createConversation(
                principal.getUserId()
        );
    }

    @Override
    public List<MessageResponse> getMessages(
            CustomUserDetails principal,
            Long conversationId
    ) {
        return historyService.getSnapshot(
                        principal.getUserId(),
                        conversationId
                )
                .messages()
                .stream()
                .map(ChatbotMessageDto::toResponse)
                .toList();
    }

    @Override
    public void deleteConversation(
            CustomUserDetails principal,
            Long conversationId
    ) {
        historyService.deleteConversation(
                principal.getUserId(),
                conversationId
        );
    }

    @Override
    public ChatbotReply ask(
            CustomUserDetails principal,
            Long conversationId,
            String message
    ) {
        Long userId = principal.getUserId();

        // 서버 설정에 따른 사용 가능 여부는 서비스에서 확인한다.
        if (!enabled) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "CHATBOT_DISABLED",
                    "챗봇이 설정되지 않은 서버입니다."
            );
        }

        // 공백 정리는 서비스에서 하고 입력 조건 검사는 Validator에 위임한다.
        String question = message == null
                ? ""
                : message.strip();

        chatbotValidator.validateQuestion(question);

        String maskedQuestion = PiiMasker.mask(question);

        // 별도 트랜잭션으로 질문과 사용량을 먼저 저장한다.
        var reservation = historyService.beginQuestion(
                principal,
                conversationId,
                maskedQuestion
        );

        String reply;

        try {
            List<Message> context = new ArrayList<>();
            List<ChatbotMessageDto> history = reservation.history();

            // 완료된 이전 대화 중 최근 10개 메시지만 AI에 전달한다.
            int start = Math.max(
                    0,
                    history.size() - CONTEXT_MESSAGES
            );

            for (int index = start; index < history.size(); index++) {
                ChatbotMessageDto item = history.get(index);
                String content = PiiMasker.mask(item.getContent());

                if ("USER".equals(item.getRole())) {
                    context.add(new UserMessage(content));
                } else if ("ASSISTANT".equals(item.getRole())) {
                    context.add(new AssistantMessage(content));
                }
            }

            context.add(new UserMessage(maskedQuestion));

            String userContext = PiiMasker.mask(
                    userContextProvider.build(userId)
            );

            reply = chatClient.prompt()
                    .system(system -> system.param(
                            "userContext",
                            userContext
                    ))
                    .messages(context)
                    .call()
                    .content();
        } catch (RuntimeException exception) {
            markFailed(
                    userId,
                    conversationId,
                    reservation.questionId()
            );

            log.warn(
                    "챗봇 호출 실패. 질문ID={}, 예외유형={}",
                    reservation.questionId(),
                    exception.getClass().getSimpleName()
            );

            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "CHATBOT_UNAVAILABLE",
                    "답변 생성에 실패했습니다. 질문 기록은 저장되었습니다."
            );
        }

        // 외부 AI 응답 확인과 실패 상태 저장은 서비스 흐름에 유지한다.
        if (reply == null || reply.isBlank()) {
            markFailed(
                    userId,
                    conversationId,
                    reservation.questionId()
            );

            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "CHATBOT_EMPTY_RESPONSE",
                    "AI 답변이 비어 있습니다. 질문 기록은 저장되었습니다."
            );
        }

        String maskedReply;

        try {
            maskedReply = PiiMasker.mask(reply);

            // 답변 저장과 질문 완료 처리를 같은 트랜잭션에서 수행한다.
            historyService.completeQuestion(
                    userId,
                    conversationId,
                    reservation.questionId(),
                    maskedReply
            );
        } catch (RuntimeException exception) {
            markFailed(
                    userId,
                    conversationId,
                    reservation.questionId()
            );

            throw exception;
        }

        return ChatbotReply.builder()
                .reply(maskedReply)
                .remaining(reservation.usage().remaining())
                .build();
    }

    private void markFailed(
            Long userId,
            Long conversationId,
            Long questionId
    ) {
        try {
            // 기존 작업과 별도 트랜잭션으로 실패 상태를 저장한다.
            historyService.failQuestion(
                    userId,
                    conversationId,
                    questionId
            );
        } catch (RuntimeException exception) {
            // 상태 저장 실패가 원래 오류를 덮어쓰지 않도록 한다.
            log.error(
                    "챗봇 실패 상태 저장 실패. 질문ID={}, 예외유형={}",
                    questionId,
                    exception.getClass().getSimpleName()
            );
        }
    }
}