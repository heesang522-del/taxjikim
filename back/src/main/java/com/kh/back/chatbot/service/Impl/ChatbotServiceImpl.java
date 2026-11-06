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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Service
public class ChatbotServiceImpl implements ChatbotService {

    private static final int CONTEXT_MESSAGES = 10;

    // AI 스트림의 무응답 시간과 전체 처리 시간을 제한한다.
    private static final Duration STREAM_IDLE_TIMEOUT =
            Duration.ofSeconds(45);

    private static final Duration STREAM_TOTAL_TIMEOUT =
            Duration.ofSeconds(150);

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
        // 대화방 10개 제한과 저장은 기존 기록 서비스에서 처리한다.
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
        // 기존 일반 API에서는 중간 출력 없이 최종 답변만 반환한다.
        return askStream(
                principal,
                conversationId,
                message,
                delta -> {
                    // 일반 응답 API는 중간 답변을 전송하지 않는다.
                }
        );
    }

    @Override
    public ChatbotReply askStream(
            CustomUserDetails principal,
            Long conversationId,
            String message,
            Consumer<String> onDelta
    ) {
        Long userId = principal.getUserId();

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

        chatbotValidator.validateQuestion(question);

        // 질문을 저장하거나 AI에 전달하기 전에 개인정보를 마스킹한다.
        String maskedQuestion = PiiMasker.mask(question);

        // 질문과 사용량을 먼저 저장하고 완료된 이전 대화를 가져온다.
        var reservation = historyService.beginQuestion(
                principal,
                conversationId,
                maskedQuestion
        );

        try {
            List<Message> context = buildContext(
                    reservation.history(),
                    maskedQuestion
            );

            String userContext = PiiMasker.mask(
                    userContextProvider.build(userId)
            );

            StreamingPiiBuffer buffer =
                    new StreamingPiiBuffer(onDelta);

            // AI가 생성한 조각을 받는 즉시 버퍼에 전달한다.
            // 버퍼는 현재 마스킹 규칙으로 검사할 수 있는 구간만 내보낸다.
            chatClient.prompt()
                    .system(system -> system.param(
                            "userContext",
                            userContext
                    ))
                    .messages(context)
                    .stream()
                    .content()
                    .timeout(STREAM_IDLE_TIMEOUT)
                    .doOnNext(buffer::append)
                    .blockLast(STREAM_TOTAL_TIMEOUT);

            // 생성이 끝났으므로 마지막 미완성 구간도 검사해서 전달한다.
            String maskedReply = buffer.finish();

            if (maskedReply.isBlank()) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "CHATBOT_EMPTY_RESPONSE",
                        "AI 답변이 비어 있습니다. 질문 기록은 저장되었습니다."
                );
            }

            // 중간 답변은 화면에만 표시하고 완성된 답변을 한 번 저장한다.
            historyService.completeQuestion(
                    userId,
                    conversationId,
                    reservation.questionId(),
                    maskedReply
            );

            return ChatbotReply.builder()
                    .reply(maskedReply)
                    .remaining(reservation.usage().remaining())
                    .build();

        } catch (RuntimeException exception) {
            markFailed(
                    userId,
                    conversationId,
                    reservation.questionId()
            );

            if (exception instanceof ApiException apiException) {
                throw apiException;
            }

            log.warn(
                    "챗봇 스트리밍 처리 실패. 질문ID={}, 예외유형={}",
                    reservation.questionId(),
                    exception.getClass().getSimpleName()
            );

            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "CHATBOT_UNAVAILABLE",
                    "답변 생성 또는 저장에 실패했습니다. 질문 기록을 확인해주세요."
            );
        }
    }

    private List<Message> buildContext(
            List<ChatbotMessageDto> history,
            String question
    ) {
        List<Message> context = new ArrayList<>();

        // 최근 완료 메시지만 사용해 이전 질문과 답변의 순서를 유지한다.
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

        context.add(new UserMessage(question));

        return context;
    }

    private void markFailed(
            Long userId,
            Long conversationId,
            Long questionId
    ) {
        try {
            // AI 처리와 별도 트랜잭션으로 실패 상태를 기록한다.
            historyService.failQuestion(
                    userId,
                    conversationId,
                    questionId
            );

        } catch (RuntimeException exception) {
            log.error(
                    "챗봇 실패 상태 저장 실패. 질문ID={}, 예외유형={}",
                    questionId,
                    exception.getClass().getSimpleName()
            );
        }
    }

    private static final class StreamingPiiBuffer {

        private static final int MAX_PENDING_LENGTH = 8192;

        private final StringBuilder pending = new StringBuilder();
        private final StringBuilder answer = new StringBuilder();
        private final Consumer<String> onDelta;

        private StreamingPiiBuffer(Consumer<String> onDelta) {
            this.onDelta = onDelta;
        }

        private void append(String chunk) {
            if (chunk == null || chunk.isEmpty()) {
                return;
            }

            pending.append(chunk);

            int cutoff = 0;
            int previous = -1;

            for (int index = 0; index < pending.length(); index++) {
                char current = pending.charAt(index);

                if (Character.isWhitespace(current)) {
                    // 현재 PiiMasker의 숫자 패턴은 공백을 사이에 두고
                    // 이어질 수 있으므로 숫자나 하이픈 뒤에서는 보류한다.
                    // 이메일은 공백을 넘지 않으므로 단어가 끝난 뒤 검사한다.
                    if (previous >= 0
                            && !Character.isDigit(previous)
                            && previous != '-') {
                        cutoff = index + 1;
                    }

                } else {
                    previous = current;
                }
            }

            if (cutoff > 0) {
                emit(cutoff);
            }

            // 구분 없는 긴 출력은 그대로 노출하지 않고 실패 처리한다.
            if (pending.length() > MAX_PENDING_LENGTH) {
                throw new IllegalStateException(
                        "챗봇 개인정보 검사 버퍼 한도를 초과했습니다."
                );
            }
        }

        private String finish() {
            if (!pending.isEmpty()) {
                emit(pending.length());
            }

            return answer.toString();
        }

        private void emit(int end) {
            String masked = PiiMasker.mask(
                    pending.substring(0, end)
            );

            pending.delete(0, end);

            // 저장할 내용과 화면에 전송할 내용에 같은 마스킹 결과를 사용한다.
            answer.append(masked);
            onDelta.accept(masked);
        }
    }
}