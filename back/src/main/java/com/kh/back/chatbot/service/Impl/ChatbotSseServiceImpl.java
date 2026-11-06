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
            @Qualifier("chatbotTaskExecutor") // 질문 처리 작업을 별도 스레드에서 실행할 도구
            TaskExecutor chatbotTaskExecutor // @Qualifier 는 여러 실행 도구중 chatbotAsyncConfig 에서 chatbotTaskExecutor 라는 이름으로 등록한 객체를 선택
    ) {
        this.chatbotService = chatbotService; // 전달받은 객체를 이 클래스의 필드에 저장
        this.sseEmitterRegistry = sseEmitterRegistry;
        this.chatbotTaskExecutor = chatbotTaskExecutor;
    }

    @Override
    public void submitQuestion(
            CustomUserDetails principal,
            Long conversationId,
            String requestId,
            String message
    ) { // 인터페이스에 선언한 질문 접수 기능을 실제로 구현하는 메서드 시작부분
        try {
            chatbotTaskExecutor.execute(() ->
                    processQuestion(
                            principal,
                            conversationId,
                            requestId,
                            message
                    )
            ); // 질문처리 작업을 별도 스레드에서 실행하도록 맡기는 부분

        } catch (TaskRejectedException exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "CHATBOT_BUSY",
                    "질문 요청이 많습니다. 잠시 후 다시 시도해주세요."
            );
        }
    } // 실행 도구가 질문처리 작업을 받아주지 못햇을떄 오류로 안내하는 부분

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
            ); // ai 답변을 스트리밍으로 생성하면서 , 중간 답변이 나올떄마다 sse로 전송하는 부분

            ChatbotSseEvent completed = ChatbotSseEvent.builder()
                    .requestId(requestId)
                    .conversationId(conversationId)
                    .reply(reply.reply())
                    .remaining(reply.remaining())
                    .build();  // 답변처리가 왈료됐다는 sse 이벤트 데이터를 만드는 부분

            sseEmitterRegistry.send(
                    principal.getUserId(),
                    "chatbot-done",
                    completed
            ); // 로그인한 사용자에게 답변처리가 완료됐다는 sse 이벤트를 보내는 부분

        } catch (ApiException exception) {
            sendFailure(
                    principal,
                    conversationId,
                    requestId,
                    exception.getCode(),
                    exception.getMessage()
            ); // 질문 처리중 오류가 발생했을떄, 실패 결과를 sse로 전달하는 부분
               // 사용횟수초과 , 대화방 접근 불가, ai 응답 실패등
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
 // 중간 답변 조각을 sse로 보내기 위해 이벤트 객체를 만드는 부분

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
                .build(); //질문 처리 실패 정보를 sse로 보내기 위해 이벤트 객체를 만드는 부분

        sseEmitterRegistry.send(
                principal.getUserId(),
                "chatbot-failed",
                failed
        );
    } // builder로 위 정보를 담은 객체를 만들고  failed 변수에 저장 , 여기서 message 는 사용자의 질문이 아니라 오류 안내 문구
}