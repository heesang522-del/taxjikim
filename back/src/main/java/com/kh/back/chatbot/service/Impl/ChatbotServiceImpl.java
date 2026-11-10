package com.kh.back.chatbot.service.Impl;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.chatbot.dto.ChatbotConversationDto.ConversationResponse;
import com.kh.back.chatbot.dto.ChatbotMessageDto;
import com.kh.back.chatbot.dto.ChatbotMessageDto.MessageResponse;
import com.kh.back.chatbot.dto.ChatbotReply;
import com.kh.back.chatbot.dto.QuestionTaskDto;
import com.kh.back.chatbot.service.ChatbotHistoryService;
import com.kh.back.chatbot.service.ChatbotService;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Service
public class ChatbotServiceImpl implements ChatbotService {

    private static final int CONTEXT_MESSAGES = 10;

    private static final Duration STREAM_IDLE_TIMEOUT =
            Duration.ofSeconds(45);

    private static final Duration STREAM_TOTAL_TIMEOUT =
            Duration.ofSeconds(150);

    private final ChatClient chatClient;
    private final ChatbotHistoryService historyService;
    private final UserContextProvider userContextProvider;
    private final boolean enabled;

    public ChatbotServiceImpl(
            ChatClient.Builder builder,
            ChatbotHistoryService historyService,
            UserContextProvider userContextProvider,
            @Value("classpath:prompts/Chatbot-System.st")
            Resource systemPrompt,
            @Value("${spring.ai.openai.api-key:no-key}")
            String apiKey
    ) {
        this.historyService = historyService;
        this.userContextProvider = userContextProvider;

        this.chatClient = builder
                .defaultSystem(systemPrompt, StandardCharsets.UTF_8)
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
        return historyService.getConversations(principal.getUserId());
    }

    @Override
    public ConversationResponse createConversation(
            CustomUserDetails principal
    ) {
        return historyService.createConversation(principal.getUserId());
    }

    @Override
    public List<MessageResponse> getMessages(
            CustomUserDetails principal,
            Long conversationId
    ) {
        return historyService.getSnapshot(principal.getUserId(), conversationId)
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
        historyService.deleteConversation(principal.getUserId(), conversationId);
    }

    @Override
    public ChatbotReply ask(
            CustomUserDetails principal,
            Long conversationId,
            String question,
            Consumer<String> onDelta,
            QuestionTaskDto task
    ) {
        if (!enabled) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "CHATBOT_DISABLED",
                    "챗봇이 설정되지 않은 서버입니다."
            );
        }

        Long userId = principal.getUserId();

        // 저장 및 AI 전송 전에 질문의 개인정보를 마스킹한다.
        String maskedQuestion = PiiMasker.mask(question);

        // 예약과 취소 대상 등록을 같은 질문 작업에서 처리한다.
        ChatbotHistoryService.QuestionReservation reservation =
                task.begin(historyService, principal, conversationId, maskedQuestion);

        try {
            task.checkActive();

            List<Message> context = buildContext(
                    reservation.history(),
                    maskedQuestion
            );

            String userContext = PiiMasker.mask(
                    userContextProvider.build(userId)
            );

            StreamingPiiBuffer buffer = new StreamingPiiBuffer(onDelta);

            chatClient.prompt()
                    .system(system -> system.param("userContext", userContext))
                    .messages(context)
                    .stream()
                    .content()
                    .timeout(STREAM_IDLE_TIMEOUT)
                    .doOnNext(chunk -> {
                        task.checkActive();
                        buffer.append(chunk);
                    })
                    .blockLast(STREAM_TOTAL_TIMEOUT);

            task.checkActive();

            String maskedReply = buffer.finish();

            if (maskedReply.isBlank()) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "CHATBOT_EMPTY_RESPONSE",
                        "AI 답변이 비어 있습니다."
                );
            }

            // 취소와 최종 저장 중 먼저 확정된 상태를 따른다.
            return task.complete(() -> {
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
            });
        } catch (RuntimeException exception) {
            markFailed(userId, conversationId, reservation.questionId());

            if (exception instanceof ApiException apiException) {
                throw apiException;
            }

            log.warn(
                    "챗봇 답변 처리 실패. 질문ID={}, 예외유형={}",
                    reservation.questionId(),
                    exception.getClass().getSimpleName()
            );

            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "CHATBOT_UNAVAILABLE",
                    "답변 처리에 실패했습니다. 대화 기록과 사용량을 다시 조회해주세요."
            );
        }
    }

    private List<Message> buildContext(
            List<ChatbotMessageDto> history,
            String question
    ) {
        List<Message> context = new ArrayList<>();
        int start = Math.max(0, history.size() - CONTEXT_MESSAGES);

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
            // 실패 상태와 횟수 복구를 별도 트랜잭션에서 확정한다.
            historyService.failQuestion(userId, conversationId, questionId);
        } catch (RuntimeException exception) {
            log.error(
                    "챗봇 실패 처리 및 횟수 복구 실패. 질문ID={}, 예외유형={}",
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

            // 개인정보가 답변 조각 경계에서 잘리지 않도록 내보낼 위치를 찾는다.
            for (int index = 0; index < pending.length(); index++) {
                char current = pending.charAt(index);

                if (Character.isWhitespace(current)) {
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
            String masked = PiiMasker.mask(pending.substring(0, end));
            pending.delete(0, end);

            // 화면 표시와 DB 저장에 같은 마스킹 결과를 사용한다.
            answer.append(masked);
            onDelta.accept(masked);
        }
    }
}