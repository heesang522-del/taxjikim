package com.kh.back.chatbot.controller;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.chatbot.dto.ChatbotAsyncRequest;
import com.kh.back.chatbot.dto.ChatbotConversationDto.ConversationResponse;
import com.kh.back.chatbot.dto.ChatbotMessageDto.MessageResponse;
import com.kh.back.chatbot.dto.ChatbotSseEvent;
import com.kh.back.chatbot.service.ChatbotService;
import com.kh.back.chatbot.service.ChatbotSseService;
import com.kh.back.chatbot.validator.ChatbotValidator;
import com.kh.back.common.dto.ApiResponse;
import com.kh.back.common.sse.SseEmitterRegistry;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/chatbot")
@RequiredArgsConstructor
public class ChatbotApiController {

    private final ChatbotService chatbotService;
    private final ChatbotSseService chatbotSseService;
    private final ChatbotValidator chatbotValidator;
    private final SseEmitterRegistry sseEmitterRegistry;

    @GetMapping("/conversations")
    public ApiResponse<List<ConversationResponse>> conversations( @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return ApiResponse.ok(
                chatbotService.getConversations(principal)
        );
    } // 로그인한 사용자의 챗봇 대화방 목록을 가져오는 부분 , 챗봇 화면 왼쪽 의 대화 목록을 불러올떄 사용

    @PostMapping("/conversations")
    public ApiResponse<ConversationResponse> create( @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return ApiResponse.ok(
                chatbotService.createConversation(principal)
        );
    } // 로그인한 사용자의 새 챗봇 대화방을 만드는 부분, 화면에서 + 새 대화 버튼 클릭시 호출됨

    @GetMapping("/conversations/{conversationId}/messages")
    public ApiResponse<List<MessageResponse>> messages( @AuthenticationPrincipal CustomUserDetails principal,
                                                        @PathVariable("conversationId") Long conversationId
    ) {
        return ApiResponse.ok(
                chatbotService.getMessages(principal, conversationId)
        );
    } // 로그인한 사용자가 특정 대화방에 저장된 질문과 답변을 불러오는 부분 , 화면에서 이전 대화방을 선택할떄 호출

    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<ApiResponse<ChatbotSseEvent>> ask(@AuthenticationPrincipal CustomUserDetails principal,
                                                            @PathVariable("conversationId") Long conversationId,
                                                            @RequestBody ChatbotAsyncRequest request
    ) {
        String question = chatbotValidator.validateAndNormalizeQuestion(
                conversationId,
                request.getMessage()
        ); // 로그인한 사용자가 보낸 챗봇 질문을 받아 검사하는 부분

        chatbotValidator.validateRequestId(request.getRequestId());
        String requestId = request.getRequestId().toString(); // 질문 요청의 id를 확인, 서비스에서 쓸 문자열로 바꾸는 부분

        chatbotSseService.submitQuestion(
                principal,
                conversationId,
                requestId,
                question
        ); // 검증을 통과한 질문을 답변 생성 작업으로 등록하는 부분

        ChatbotSseEvent accepted = ChatbotSseEvent.builder()
                .requestId(requestId)
                .conversationId(conversationId)
                .accepted(true)
                .build(); // 브라우저에 이 질문을 접수했다고 알려주는 부분

        return ResponseEntity
                .status(HttpStatus.ACCEPTED) // http 상태를 202 accepted 로 설정
                .body(ApiResponse.ok(accepted)); // 접수 정보를 응답의 data 에 넣음
    }

    @DeleteMapping("/conversations/{conversationId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal CustomUserDetails principal,
                                    @PathVariable("conversationId") Long conversationId
    ) {
        chatbotService.deleteConversation(principal, conversationId);

        return ApiResponse.ok(null);
    } // 로그인한 사용자의 챗봇 대화방 하나를 삭제하는 부분

    @GetMapping( value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE // 챗봇 답변을 실시간으로 받을 연결을 여는 부분
    )
    public SseEmitter subscribe( @AuthenticationPrincipal CustomUserDetails principal
    ) {
        SseEmitter emitter = sseEmitterRegistry.subscribe(
                principal.getUserId()
        ); // 브라우저가 질문을 보내기 전에 서버의 답변을 받을 통로를 열어두는 부분

        try {
            emitter.send( // 서버가 브라우저에 실시간 연결 준비됐다고 알려주는 부분
                    SseEmitter.event()
                            .name("chatbot-ready")
                            .data(Map.of("ready", true))
            );
        } catch (IOException | IllegalStateException exception) {
            emitter.completeWithError(exception);
        }

        return emitter; // spring 에 sse 응답 객체를 돌려줌, 정상이라면 메서드가 끝난 뒤에도 연결이 열려 있어 서버가 그 연결로 답변 조각을 계속 보낼수 있음.
    }

    @PostMapping("/conversations/{conversationId}/messages/cancel") // 실시간 답변을 생성중인 질문 한건을 취소하는 api
    public ApiResponse<ChatbotSseEvent> cancel(@AuthenticationPrincipal CustomUserDetails principal,
                                               @PathVariable("conversationId") Long conversationId,
                                               @RequestBody ChatbotAsyncRequest request
    ) {
        chatbotValidator.validateConversationId(conversationId); // 대화방 번호가 올바른지, 요청 id가 들어있는지 확인 검사 통과하지 못하면 400 오류 반환
        chatbotValidator.validateRequestId(request.getRequestId());

        return ApiResponse.ok(
                chatbotSseService.cancelQuestion( // 로그인한 사용자가 해당 대화방의 소유자인지 확인, 사용자 id, 대화방 번호 요청 id로 취소할 작업을 찾음
                        principal,
                        conversationId,
                        request.getRequestId().toString() // .toString()은 요청 id를 서비스에서 사용하는 문자열로 변환
                )
        );
    }
}