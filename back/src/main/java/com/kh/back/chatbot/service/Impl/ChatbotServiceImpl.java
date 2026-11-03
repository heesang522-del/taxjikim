package com.kh.back.chatbot.service.Impl;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.chatbot.dto.ChatbotConversationDto.ConversationResponse;
import com.kh.back.chatbot.dto.ChatbotMessageDto;
import com.kh.back.chatbot.dto.ChatbotReply;
import com.kh.back.chatbot.dto.ChatbotUsageDto;
import com.kh.back.chatbot.service.ChatbotHistoryService;
import com.kh.back.chatbot.service.ChatbotService;
import com.kh.back.chatbot.service.ChatbotUsageService;
import com.kh.back.chatbot.service.UserContextProvider;
import com.kh.back.chatbot.util.PiiMasker;
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
import java.util.Map;

@Slf4j
@Service
public class ChatbotServiceImpl implements ChatbotService {

    private static final int MAX_LENGTH = 500;
    private static final int CONTEXT_MESSAGES = 10;

    private final ChatClient chatClient;
    private final ChatbotHistoryService historyService;
    private final ChatbotUsageService chatbotUsageService;
    private final UserContextProvider userContextProvider;
    private final boolean enabled;

    public ChatbotServiceImpl(
            ChatClient.Builder builder,
            ChatbotHistoryService historyService,
            ChatbotUsageService chatbotUsageService,
            UserContextProvider userContextProvider,
            @Value("classpath:prompts/Chatbot-System.st")
            Resource systemPrompt,
            @Value("${spring.ai.openai.api-key:no-key}")
            String apiKey
    ) {
        this.historyService = historyService;
        this.chatbotUsageService = chatbotUsageService;
        this.userContextProvider = userContextProvider;

        // 이전 대화는 DB에서 조회해 직접 전달한다.
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
        Long userId = requireUserId(principal);

        return historyService.getConversations(userId);
    }

    @Override
    public ConversationResponse createConversation(
            CustomUserDetails principal
    ) {
        Long userId = requireUserId(principal);

        return historyService.createConversation(userId);
    }

    @Override
    public List<ChatbotMessageDto> getMessages(
            CustomUserDetails principal,
            Long conversationId
    ) {
        Long userId = requireUserId(principal);

        return historyService.getSnapshot(
                userId,
                conversationId
        ).messages();
    }

    @Override
    public void deleteConversation(
            CustomUserDetails principal,
            Long conversationId
    ) {
        Long userId = requireUserId(principal);

        historyService.deleteConversation(
                userId,
                conversationId
        );
    }

    @Override
    public ChatbotReply ask(
            CustomUserDetails principal,
            Long conversationId,
            String message
    ) {
        Long userId = requireUserId(principal);

        if (!enabled) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "CHATBOT_DISABLED",
                    "챗봇이 설정되지 않은 서버입니다."
            );
        }

        String question = message == null
                ? ""
                : message.strip();

        if (question.isEmpty() || question.length() > MAX_LENGTH) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "질문은 1~500자로 입력해주세요.",
                    Map.of(
                            "message",
                            "질문은 1~500자로 입력해주세요."
                    )
            );
        }

        // 사용량 예약 전에 대화방의 존재 여부와 소유권을 확인한다.
        var snapshot = historyService.getSnapshot(
                userId,
                conversationId
        );

        // 별도 DB 트랜잭션으로 사용량을 예약한 뒤 AI를 호출한다.
        ChatbotUsageDto reservedUsage =
                chatbotUsageService.reserveUsage(principal);

        String maskedQuestion = PiiMasker.mask(question);

        List<Message> context = new ArrayList<>();
        List<ChatbotMessageDto> history = snapshot.messages();

        // 전체 기록은 DB에 유지하고 AI에는 최근 10개만 전달한다.
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

        String reply;

        try {
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
            // 외부 호출 실패도 기존 정책대로 질문 시도 횟수에 포함한다.
            log.warn(
                    "챗봇 호출 실패. 예외유형={}",
                    exception.getClass().getSimpleName()
            );

            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "CHATBOT_UNAVAILABLE",
                    "챗봇을 잠시 사용할 수 없습니다. 다시 시도해주세요."
            );
        }

        if (reply == null || reply.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "CHATBOT_EMPTY_RESPONSE",
                    "AI 답변이 비어 있습니다. 다시 시도해주세요."
            );
        }

        String maskedReply = PiiMasker.mask(reply);

        // 질문과 답변을 함께 저장하고 대화 버전 충돌을 검사한다.
        historyService.saveExchange(
                userId,
                conversationId,
                snapshot.revision(),
                maskedQuestion,
                maskedReply
        );

        return ChatbotReply.builder()
                .reply(maskedReply)
                .remaining(reservedUsage.remaining())
                .build();
    }

    private Long requireUserId(CustomUserDetails principal) {
        // 인증 객체에서 확인한 사용자 ID만 DB 처리에 사용한다.
        if (principal == null
                || principal.getUserId() == null
                || principal.getUserId() <= 0) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED",
                    "로그인이 필요합니다."
            );
        }

        return principal.getUserId();
    }
}