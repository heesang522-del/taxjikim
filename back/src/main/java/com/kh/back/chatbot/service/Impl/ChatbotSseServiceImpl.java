package com.kh.back.chatbot.service.Impl;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.chatbot.dto.ChatbotReply;
import com.kh.back.chatbot.dto.ChatbotSseEvent;
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

    private static final long RETENTION_MS =
            30 * 60 * 1000L;

    private static final int MAX_REQUESTS = 5000;

    private final ChatbotService chatbotService;
    private final ChatbotHistoryService historyService;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final TaskExecutor chatbotTaskExecutor;

    private final ConcurrentHashMap<RequestKey, QuestionTask> tasks =
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
            String message
    ) {
        // 실행 전에 로그인 사용자의 대화방 소유권을 확인한다.
        historyService.getSnapshot(
                principal.getUserId(),
                conversationId
        );

        RequestKey key = new RequestKey(
                principal.getUserId(),
                conversationId,
                requestId
        );

        QuestionTask task = getTask(key);

        synchronized (task) {
            if (task.submitted) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "CHATBOT_DUPLICATE_REQUEST",
                        "이미 접수한 요청입니다."
                );
            }

            // 취소가 먼저 접수된 요청은 실행하지 않는다.
            task.control.checkActive();

            task.submitted = true;

            task.future = new FutureTask<>(() -> {
                processQuestion(
                        principal,
                        conversationId,
                        requestId,
                        message,
                        task.control
                );

                return null;
            });
        }

        try {
            chatbotTaskExecutor.execute(task.future);

        } catch (TaskRejectedException exception) {
            task.control.cancel();
            task.future.cancel(false);

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
        historyService.getSnapshot(
                principal.getUserId(),
                conversationId
        );

        QuestionTask task = getTask(
                new RequestKey(
                        principal.getUserId(),
                        conversationId,
                        requestId
                )
        );

        synchronized (task) {
            // 요청 정보를 알고 있는 경우에만 취소 확정을 안내한다.
            boolean knownRequest = task.submitted;

            ChatbotReply completed = task.control.cancel();

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

            if (task.future != null) {
                // DB 취소 처리 후 실행 중인 로컬 작업에도 중단을 요청한다.
                task.future.cancel(true);
            }

            return ChatbotSseEvent.builder()
                    .requestId(requestId)
                    .conversationId(conversationId)
                    .cancelled(knownRequest)
                    .code(
                            knownRequest
                                    ? null
                                    : "CHATBOT_CANCELLATION_UNKNOWN"
                    )
                    .build();
        }
    }

    private QuestionTask getTask(RequestKey key) {
        long oldest =
                System.currentTimeMillis() - RETENTION_MS;

        // 완료된 요청 정보를 일정 시간 보관해 늦은 취소 요청을 처리한다.
        tasks.entrySet().removeIf(entry -> {
            QuestionTask task = entry.getValue();
            FutureTask<Void> future = task.future;

            return task.createdAt < oldest
                    && (future == null || future.isDone());
        });

        QuestionTask existing = tasks.get(key);

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

        // 취소가 접수보다 먼저 도착해도 동일한 상태 객체를 사용한다.
        return tasks.computeIfAbsent(
                key,
                ignored -> new QuestionTask()
        );
    }

    private void processQuestion(
            CustomUserDetails principal,
            Long conversationId,
            String requestId,
            String message,
            ChatbotService.QuestionControl control
    ) {
        try {
            ChatbotReply reply = chatbotService.ask(
                    principal,
                    conversationId,
                    message,
                    delta -> sseEmitterRegistry.send(
                            principal.getUserId(),
                            "chatbot-delta",
                            ChatbotSseEvent.builder()
                                    .requestId(requestId)
                                    .conversationId(conversationId)
                                    .delta(delta)
                                    .build()
                    ),
                    control
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

    private record RequestKey(
            Long userId,
            Long conversationId,
            String requestId
    ) {
    }

    private static final class QuestionTask {

        private final ChatbotService.QuestionControl control =
                new ChatbotService.QuestionControl();

        private final long createdAt =
                System.currentTimeMillis();

        private volatile FutureTask<Void> future;

        private boolean submitted;
    }
}