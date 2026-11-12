package com.kh.back.chatbot.service.Impl;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.chatbot.dto.ChatbotReply;
import com.kh.back.chatbot.dto.ChatbotRequestKeyDto;
import com.kh.back.chatbot.dto.ChatbotSseEvent;
import com.kh.back.chatbot.dto.QuestionTaskDto;
import com.kh.back.chatbot.service.ChatbotHistoryService;
import com.kh.back.chatbot.service.ChatbotService;
import com.kh.back.chatbot.service.ChatbotSseService;
import com.kh.back.common.exception.ApiException;
import com.kh.back.common.sse.SseEmitterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;

@Slf4j
@Service
public class ChatbotSseServiceImpl implements ChatbotSseService {

    private static final long RETENTION_MS = 30 * 60 * 1000L;
    private static final int MAX_REQUESTS = 5000;

    private final ChatbotService chatbotService;
    private final ChatbotHistoryService historyService;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final TaskExecutor chatbotTaskExecutor;

    private final ConcurrentHashMap<ChatbotRequestKeyDto, QuestionTaskDto> tasks =
            new ConcurrentHashMap<>();

    public ChatbotSseServiceImpl(
            ChatbotService chatbotService,
            ChatbotHistoryService historyService,
            SseEmitterRegistry sseEmitterRegistry,
            @Qualifier("chatbotTaskExecutor")
            TaskExecutor chatbotTaskExecutor
    ) {
        this.chatbotService = chatbotService;
        this.historyService = historyService;
        this.sseEmitterRegistry = sseEmitterRegistry;
        this.chatbotTaskExecutor = chatbotTaskExecutor;
    }

    @Override
    public void submitQuestion(
            CustomUserDetails principal,
            Long conversationId,
            String requestId,
            String question
    ) {
        Long userId = principal.getUserId();
        historyService.requireConversationOwnership(userId, conversationId);

        QuestionTaskDto task = getTask(userId, conversationId, requestId);
        FutureTask<Void> future;

        synchronized (task) {
            if (task.isSubmitted()) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "CHATBOT_DUPLICATE_REQUEST",
                        "이미 접수한 요청입니다."
                );
            }

            // 취소가 먼저 접수된 요청은 실행하지 않는다.
            task.checkActive();
            task.setSubmitted(true);

            future = new FutureTask<>(() -> {
                processQuestion(
                        principal,
                        conversationId,
                        requestId,
                        question,
                        task
                );

                return null;
            });

            // 취소 요청에서 같은 실행 작업을 찾을 수 있게 등록한다.
            task.setFuture(future);
        }

        try {
            chatbotTaskExecutor.execute(future);
        } catch (TaskRejectedException exception) {
            task.cancel();
            future.cancel(false);

            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "CHATBOT_BUSY",
                    "질문 요청이 많습니다. 잠시 후 다시 시도해주세요."
            );
        }
    }

    @Override
    public ChatbotSseEvent cancelQuestion(
            CustomUserDetails principal,
            Long conversationId,
            String requestId
    ) {
        Long userId = principal.getUserId();
        historyService.requireConversationOwnership(userId, conversationId);

        QuestionTaskDto task = getTask(userId, conversationId, requestId);

        synchronized (task) {
            // 접수 이력이 있는 요청에 대해서만 취소가 확정됐다고 응답한다.
            boolean knownRequest = task.isSubmitted();
            ChatbotReply completed = task.cancel();

            if (completed != null) {
                // DB 저장이 이미 끝난 질문은 성공 결과를 반환한다.
                return ChatbotSseEvent.builder()
                        .requestId(requestId)
                        .conversationId(conversationId)
                        .cancelled(false)
                        .reply(completed.reply())
                        .remaining(completed.remaining())
                        .build();
            }

            FutureTask<Void> future = task.getFuture();

            if (future != null) {
                // DB 취소 처리 후 실행 중인 로컬 작업에도 중단을 요청한다.
                future.cancel(true);
            }

            return ChatbotSseEvent.builder()
                    .requestId(requestId)
                    .conversationId(conversationId)
                    .cancelled(knownRequest)
                    .code(knownRequest ? null : "CHATBOT_CANCELLATION_UNKNOWN")
                    .build();
        }
    }

    private QuestionTaskDto getTask(
            Long userId,
            Long conversationId,
            String requestId
    ) {
        // 접수와 취소는 같은 세 값으로 같은 작업을 찾는다.
        ChatbotRequestKeyDto key = ChatbotRequestKeyDto.builder()
                .userId(userId)
                .conversationId(conversationId)
                .requestId(requestId)
                .build();

        long oldest = System.currentTimeMillis() - RETENTION_MS;

        // 오래됐고 실행 중인 작업이 없는 항목만 정리한다.
        tasks.entrySet().removeIf(entry -> {
            QuestionTaskDto task = entry.getValue();
            FutureTask<Void> future = task.getFuture();

            return task.getCreatedAt() < oldest
                    && (future == null || future.isDone());
        });

        QuestionTaskDto existing = tasks.get(key);

        if (existing != null) {
            return existing;
        }

        if (tasks.size() >= MAX_REQUESTS) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "CHATBOT_BUSY",
                    "잠시 후 다시 시도해주세요."
            );
        }

        return tasks.computeIfAbsent(
                key,
                ignored -> QuestionTaskDto.builder().build()
        );
    }

    private void processQuestion(
            CustomUserDetails principal,
            Long conversationId,
            String requestId,
            String question,
            QuestionTaskDto task
    ) {
        try {
            // 답변 조각이 만들어지면 SSE로 전달한다.
            ChatbotReply reply = chatbotService.ask(
                    principal,
                    conversationId,
                    question,
                    delta -> sseEmitterRegistry.send(
                            principal.getUserId(),
                            "chatbot-delta",
                            ChatbotSseEvent.builder()
                                    .requestId(requestId)
                                    .conversationId(conversationId)
                                    .delta(delta)
                                    .build()
                    ),
                    task
            );

            sseEmitterRegistry.send(
                    principal.getUserId(),
                    "chatbot-done",
                    ChatbotSseEvent.builder()
                            .requestId(requestId)
                            .conversationId(conversationId)
                            .reply(reply.reply())
                            .remaining(reply.remaining())
                            .build()
            );
        } catch (RuntimeException exception) {
            String code;

            if (exception instanceof ApiException apiException) {
                code = apiException.getCode();
            } else {
                code = "CHATBOT_UNAVAILABLE";

                log.error(
                        "챗봇 SSE 처리 실패. 요청ID={}, 예외유형={}",
                        requestId,
                        exception.getClass().getSimpleName()
                );
            }

            sseEmitterRegistry.send(
                    principal.getUserId(),
                    "chatbot-failed",
                    ChatbotSseEvent.builder()
                            .requestId(requestId)
                            .conversationId(conversationId)
                            .code(code)
                            .message(
                                    "답변 처리가 중단되었습니다. "
                                            + "대화 기록과 사용량을 다시 조회해주세요."
                            )
                            .build()
            );
        }
    }
}