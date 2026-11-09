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
            String question,
            Consumer<String> onDelta,
            QuestionControl control
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

        // 예약과 취소 대상 등록을 하나의 상태 객체에서 처리한다.
        ChatbotHistoryService.QuestionReservation reservation =
                control.begin(
                        historyService,
                        principal,
                        conversationId,
                        maskedQuestion
                );

        try {
            control.checkActive();

            List<Message> context = buildContext(
                    reservation.history(),
                    maskedQuestion
            );

            String userContext = PiiMasker.mask(
                    userContextProvider.build(userId)
            );

            StreamingPiiBuffer buffer =
                    new StreamingPiiBuffer(onDelta);

            chatClient.prompt()
                    .system(system -> system.param(
                            "userContext",
                            userContext
                    ))
                    .messages(context)
                    .stream()
                    .content()
                    .timeout(STREAM_IDLE_TIMEOUT)
                    .doOnNext(chunk -> {
                        control.checkActive();
                        buffer.append(chunk);
                    })
                    .blockLast(STREAM_TOTAL_TIMEOUT);

            control.checkActive();

            String maskedReply = buffer.finish();

            if (maskedReply.isBlank()) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "CHATBOT_EMPTY_RESPONSE",
                        "AI 답변이 비어 있습니다."
                );
            }

            // 취소와 최종 저장 중 먼저 확정된 상태를 따른다.
            return control.complete(() -> {
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
            markFailed(
                    userId,
                    conversationId,
                    reservation.questionId()
            );

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
            // 실패 상태와 횟수 복구를 별도 트랜잭션에서 확정한다.
            historyService.failQuestion(
                    userId,
                    conversationId,
                    questionId
            );
        } catch (RuntimeException exception) {
            // 복구 실패를 성공으로 안내하지 않고 재조회할 수 있게 기록한다.
            log.error(
                    "챗봇 실패 처리 및 횟수 복구 실패. 질문ID={}, 예외유형={}",
                    questionId,
                    exception.getClass().getSimpleName()
            );
        }
    }

    private static final class StreamingPiiBuffer { // 이 대화방에서 이 질문의 답변 생성을 멈춰 달라는 요청을 처리하는 부분

        private static final int MAX_PENDING_LENGTH = 8192; // 개인정보 검사를 위해 잠시 모아두는 답변 조각의 길이 제한

        private final StringBuilder pending = new StringBuilder(); // ai 답변을 조금씩 받아 처리하는데 쓰는 변수
        private final StringBuilder answer = new StringBuilder();
        private final Consumer<String> onDelta;

        private StreamingPiiBuffer(Consumer<String> onDelta) {
            this.onDelta = onDelta;
        } // streamingpiibuffer 를 만들떄 답변 조각을 어디로 보낼지 정해주는 생성자

        private void append(String chunk) {
            if (chunk == null || chunk.isEmpty()) { // 답변조각이 아예 없는경우, 답변조각이 빈 문자열 ""인 경우
                return;
            } // append() 는 ai 가 보낸 답변 조각을 버퍼에 추가하는 메서드, 맨처음에 처리할 내용이 있는지 확인

            pending.append(chunk); // 새 답변 조각을 모아두고, 어디까지 검사해서 내보낼수 있는지 찾을 준비

            int cutoff = 0; // 이벤에 내보낼 부분의 끝 위치, 처음에는 내보낼 위치를 아직 못찾았으므로 0으로 시작
            int previous = -1; // 앞에서 마지막으로 본 공백이 아닌 문자를 기억하는 변수 , -1 은 아직 그런 문자를 읽지 않았다는 표시

            for (int index = 0; index < pending.length(); index++) {
                char current = pending.charAt(index); // pending에 모아둔 글을 맨 앞부터 한칸씩 읽는 반복문 부분

                if (Character.isWhitespace(current)) { // 공백을 만났을때, 그 지접까지 개인정보를 검사해서 내보내도 될지 판단하는 부분
                    if (previous >= 0
                            && !Character.isDigit(previous)
                            && previous != '-') {
                        cutoff = index + 1;
                    } // 현재 문자가 공백이 아니면 , previous = current 로 마지막 글자를 기억하고,
                    //   현재 문자가 공백이면 , 마지막 글자를 확인, previous >=0은 앞에서 글자를 하나라도 일었다는 뜻이며,
                    //  그 글자가 숫자도 아니고 - 도 아니면 , cutoff = index +1 로 공백까지 포함해서 내보낼 위치를 기록.
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
        } // ai가 답변을 다 보내면 호출하는 마무리 메서드

        private void emit(int end) {
            String masked = PiiMasker.mask(
                    pending.substring(0, end)
            );

            pending.delete(0, end);

            // 화면 표시와 DB 저장에 같은 마스킹 결과를 사용한다.
            answer.append(masked);
            onDelta.accept(masked);
        }
    }
}