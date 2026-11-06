package com.kh.back.chatbot.controller;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.chatbot.dto.ChatbotAsyncRequest;
import com.kh.back.chatbot.dto.ChatbotConversationDto.ConversationResponse;
import com.kh.back.chatbot.dto.ChatbotMessageDto.MessageResponse;
import com.kh.back.chatbot.dto.ChatbotReply;
import com.kh.back.chatbot.dto.ChatbotRequest;
import com.kh.back.chatbot.dto.ChatbotSseEvent;
import com.kh.back.chatbot.service.ChatbotService;
import com.kh.back.chatbot.service.ChatbotSseService;
import com.kh.back.chatbot.validator.ChatbotValidator;
import com.kh.back.common.dto.ApiResponse;
import com.kh.back.common.exception.ApiException;
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
    } // 로그인한 사용자의 대화방 목록을 조회해서 응답하는 api

    @PostMapping("/conversations")
    public ApiResponse<ConversationResponse> create( @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return ApiResponse.ok(
                chatbotService.createConversation(principal)
        );
    } // 로그인한 사용자의 새 대화방을 만드는 api

    @GetMapping("/conversations/{conversationId}/messages")
    public ApiResponse<List<MessageResponse>> messages(@AuthenticationPrincipal CustomUserDetails principal,
                                                       @PathVariable("conversationId") Long conversationId
    ) {
        return ApiResponse.ok(
                chatbotService.getMessages(
                        principal,
                        conversationId
                )
        );
    } // 로그인한 사용자가 선택한 대화방의 질문 답변 목록을 조회하는 api

    @PostMapping("/conversations/{conversationId}/messages")
    public ApiResponse<ChatbotReply> ask(@AuthenticationPrincipal CustomUserDetails principal,
                                         @PathVariable("conversationId") Long conversationId,
                                         @RequestBody ChatbotRequest request
    ) {
        return ApiResponse.ok(
                chatbotService.ask(
                        principal,
                        conversationId,
                        request.getMessage()
                )
        );
    } // 선택한 대화방에 질문을 보내고 ai 답변을 받아 반환하는 api

    @DeleteMapping("/conversations/{conversationId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal CustomUserDetails principal,
                                    @PathVariable("conversationId") Long conversationId
    ) {
        chatbotService.deleteConversation(
                principal,
                conversationId
        );

        return ApiResponse.ok(null);
    } // 로그인한 사용자가 선택한 대화방을 삭제하는 api

    @GetMapping(
            value = "/subscribe",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE // "text/event-stream"으로 연결을 유지하면서 서버가 이벤트를 보내는 sse 형식
    )  // 로그인한 사용자가 서버에서 챗봇 결과를 받을수 있도록 sse 연결을 여는 api
    public SseEmitter subscribe( @AuthenticationPrincipal CustomUserDetails principal
    ) { // 현재 로그인한 사용자 정보를 받고, sse 응답을 전송할 sse emitter 를 반환,

        SseEmitter emitter = sseEmitterRegistry.subscribe(
                principal.getUserId()
        ); // 기존 공통 registry에 사용자 번호를 전달, registry는  새 sse 연결을 만들고 해당 사용자 번호에 등록, 이후 서비스가 사용자 번호로 데이터를 보내면 등록된 연결로 전달

        try {
            // 구독 완료 신호를 받은 프론트가 질문을 전송한다.
            emitter.send(
                    SseEmitter.event()
                            .name("chatbot-ready")
                            .data(Map.of("ready", true))
            ); // 방금 만든 연결에 준비완료 이벤트를 보냄 , 이벤트이름, 전달데이터인 {"ready": true}
               // 프론트는 이 이벤트를 받은뒤 질문 전송, 결과를 받을 연결이 준비되기 전에 질문부터 보내는 상황을 줄이기 위한 처리

        } catch (IOException | IllegalStateException exception) {
            emitter.completeWithError(exception);
        } // 전송중 연결 오류가 발생하거나 이미 종료된 연결이라면 , 해당 sse 응답을 오류로 종료

        return emitter;
    } // spring 에 sse 응답 객체를 반환, 메서드는 끝나지만 연결은 유지되며, 이후 chatbot_done 이나 chatbot-failed 이벤트를 받을수 있음.

    @PostMapping("/conversations/{conversationId}/messages/async")  //질문을 비동기로 접수하기 전에 전달받은 값이올바른지 검사하는 부분
    public ResponseEntity<ApiResponse<ChatbotSseEvent>> askAsync(@AuthenticationPrincipal CustomUserDetails principal,
                                                                 @PathVariable("conversationId") Long conversationId,
                                                                 @RequestBody ChatbotAsyncRequest request
    ) { // 요청 접수 정보, 프로젝트 공통 응답 형식, 응답 본문과 http 상태를 함께 지정하는 객체 , 뒤쪽 코드에서 접수 응답을 반환하고 최종 답변은 sse로 전달
        String message = request.getMessage() == null
                ? ""
                : request.getMessage().strip(); // 질문이 null이면 빈 문자열로 바꾸고 값이 있으면 앞뒤 공백을 제거

        // 기존 Validator로 요청 입력을 검증한다.
        chatbotValidator.validateConversationId(conversationId);
        chatbotValidator.validateQuestion(message);

        if (request.getRequestId() == null) { // requestID 가 없으면 http 400 bad request 로 거절 , 이 식별자는 나중에 도착한 sse 결과가 어떤 질문에 대한 것인지 구분하는 값
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "요청 식별자가 필요합니다.",
                    Map.of( // 오류 응담의 필드별 안내에 사용할 정보 , requestId 필드에 문제가 있다
                            "requestId",
                            "요청 식별자가 필요합니다."
                    )
            );
        }


        String requestId =
                request.getRequestId().toString();

        chatbotSseService.submitQuestion(
                principal, // 질문한 로그인 사용자 정보
                conversationId, // 질문을 보낼 대화방 번호
                requestId, // 이번 요청을 구분하는 식별자
                message   // 앞뒤 공백 제거와 검증을 마친 질문
        ); // 요청 식별자를 문자열로 바꾸고 , 챗봇 서비스에 질문 처리를 맡기는 부분

        ChatbotSseEvent accepted = ChatbotSseEvent.builder()
                .requestId(requestId)
                .conversationId(conversationId)
                .accepted(true)
                .build();

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(accepted));
    } // "질문 처리 요청을 접수했다"는 응답을 만들어 프론트에 보내는 부분
}