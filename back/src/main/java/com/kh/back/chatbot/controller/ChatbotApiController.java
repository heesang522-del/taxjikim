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
    public ApiResponse<List<ConversationResponse>> conversations(
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return ApiResponse.ok(
                chatbotService.getConversations(principal)
        );
    }

    @PostMapping("/conversations")
    public ApiResponse<ConversationResponse> create(
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return ApiResponse.ok(
                chatbotService.createConversation(principal)
        );
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ApiResponse<List<MessageResponse>> messages(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable("conversationId") Long conversationId
    ) {
        return ApiResponse.ok(
                chatbotService.getMessages(
                        principal,
                        conversationId
                )
        );
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ApiResponse<ChatbotReply> ask(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable("conversationId") Long conversationId,
            @RequestBody ChatbotRequest request
    ) {
        chatbotValidator.validateConversationId(
                conversationId
        );

        // 일반 API도 동일한 ask를 사용하되 중간 답변을 전송하지 않는다.
        return ApiResponse.ok(
                chatbotService.ask(
                        principal,
                        conversationId,
                        request.getMessage(),
                        delta -> {
                        },
                        new ChatbotService.QuestionControl()
                )
        );
    }

    @DeleteMapping("/conversations/{conversationId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable("conversationId") Long conversationId
    ) {
        chatbotService.deleteConversation(
                principal,
                conversationId
        );

        return ApiResponse.ok(null);
    }

    @GetMapping(
            value = "/subscribe",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter subscribe(
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        SseEmitter emitter =
                sseEmitterRegistry.subscribe(
                        principal.getUserId()
                );

        try {
            emitter.send(
                    SseEmitter.event()
                            .name("chatbot-ready")
                            .data(Map.of("ready", true))
            );

        } catch (IOException | IllegalStateException exception) {
            emitter.completeWithError(exception);
        }

        return emitter;
    }

    @PostMapping("/conversations/{conversationId}/messages/async")
    public ResponseEntity<ApiResponse<ChatbotSseEvent>> askAsync(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable("conversationId") Long conversationId,
            @RequestBody ChatbotAsyncRequest request
    ) {
        String message = request.getMessage() == null
                ? ""
                : request.getMessage().strip();

        chatbotValidator.validateConversationId(
                conversationId
        );

        chatbotValidator.validateQuestion(message);

        if (request.getRequestId() == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "요청 식별자가 필요합니다.",
                    Map.of(
                            "requestId",
                            "요청 식별자가 필요합니다."
                    )
            );
        }

        String requestId =
                request.getRequestId().toString();

        chatbotSseService.submitQuestion(
                principal,
                conversationId,
                requestId,
                message
        );

        ChatbotSseEvent accepted =
                ChatbotSseEvent.builder()
                        .requestId(requestId)
                        .conversationId(conversationId)
                        .accepted(true)
                        .build();

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(accepted));
    }

    @PostMapping("/conversations/{conversationId}/messages/cancel")
    public ApiResponse<ChatbotSseEvent> cancel(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable("conversationId") Long conversationId,
            @RequestBody ChatbotAsyncRequest request
    ) {
        chatbotValidator.validateConversationId(
                conversationId
        );

        if (request.getRequestId() == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "요청 식별자가 필요합니다.",
                    Map.of(
                            "requestId",
                            "요청 식별자가 필요합니다."
                    )
            );
        }

        return ApiResponse.ok(
                chatbotSseService.cancelQuestion(
                        principal,
                        conversationId,
                        request.getRequestId().toString()
                )
        );
    }
}