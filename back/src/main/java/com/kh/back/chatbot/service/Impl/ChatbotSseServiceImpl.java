package com.kh.back.chatbot.service.Impl;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.chatbot.dto.ChatbotReply;
import com.kh.back.chatbot.dto.ChatbotSseEvent;
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

@Slf4j
@Service
public class ChatbotSseServiceImpl implements ChatbotSseService {

    private final ChatbotService chatbotService;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final TaskExecutor chatbotTaskExecutor;

    public ChatbotSseServiceImpl(
            ChatbotService chatbotService,
            SseEmitterRegistry sseEmitterRegistry,
            @Qualifier("chatbotTaskExecutor")
            TaskExecutor chatbotTaskExecutor
    ) {
        this.chatbotService = chatbotService;
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
        try {
            // 스트림을 기다리는 작업은 기존 챗봇 전용 스레드에서 실행한다.
            chatbotTaskExecutor.execute(() ->
                    processQuestion(
                            principal,
                            conversationId,
                            requestId,
                            message
                    )
            );

        } catch (TaskRejectedException exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "CHATBOT_BUSY",
                    "질문 요청이 많습니다. 잠시 후 다시 시도해주세요."
            );
        }
    }

    private void processQuestion(
            CustomUserDetails principal,
            Long conversationId,
            String requestId,
            String message
    ) {
        try {
            ChatbotReply reply = chatbotService.askStream(
                    principal,
                    conversationId,
                    message,
                    delta -> sendDelta(
                            principal,
                            conversationId,
                            requestId,
                            delta
                    )
            );

            ChatbotSseEvent completed = ChatbotSseEvent.builder()
                    .requestId(requestId)
                    .conversationId(conversationId)
                    .reply(reply.reply())
                    .remaining(reply.remaining())
                    .build();

            // 완성된 답변이 DB에 저장된 뒤 완료를 알린다.
            sseEmitterRegistry.send(
                    principal.getUserId(),
                    "chatbot-done",
                    completed
            );

        } catch (ApiException exception) {
            sendFailure(
                    principal,
                    conversationId,
                    requestId,
                    exception.getCode(),
                    exception.getMessage()
            );

        } catch (RuntimeException exception) {
            log.error(
                    "챗봇 SSE 처리 실패. 요청ID={}, 예외유형={}",
                    requestId,
                    exception.getClass().getSimpleName()
            );

            sendFailure(
                    principal,
                    conversationId,
                    requestId,
                    "CHATBOT_UNAVAILABLE",
                    "답변 처리에 실패했습니다. 대화 기록을 확인해주세요."
            );
        }
    }

    private void sendDelta(
            CustomUserDetails principal,
            Long conversationId,
            String requestId,
            String delta
    ) {
        ChatbotSseEvent event = ChatbotSseEvent.builder()
                .requestId(requestId)
                .conversationId(conversationId)
                .delta(delta)
                .build();

        // 새 연결 관리 코드를 만들지 않고 기존 Registry를 재사용한다.
        sseEmitterRegistry.send(
                principal.getUserId(),
                "chatbot-delta",
                event
        );
    }

    private void sendFailure(
            CustomUserDetails principal,
            Long conversationId,
            String requestId,
            String code,
            String message
    ) {
        ChatbotSseEvent failed = ChatbotSseEvent.builder()
                .requestId(requestId)
                .conversationId(conversationId)
                .code(code)
                .message(message)
                .build();

        sseEmitterRegistry.send(
                principal.getUserId(),
                "chatbot-failed",
                failed
        );
    }
}