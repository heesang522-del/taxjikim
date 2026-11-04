package com.kh.back.chatbot.controller;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.chatbot.dto.ChatbotConversationDto.ConversationResponse;
import com.kh.back.chatbot.dto.ChatbotMessageDto;
import com.kh.back.chatbot.dto.ChatbotReply;
import com.kh.back.chatbot.dto.ChatbotRequest;
import com.kh.back.chatbot.service.ChatbotService;
import com.kh.back.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chatbot/conversations")
@RequiredArgsConstructor
public class ChatbotApiController {

    private final ChatbotService chatbotService;

    @GetMapping
    public ApiResponse<List<ConversationResponse>> conversations(
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return ApiResponse.ok(
                chatbotService.getConversations(principal)
        );
    }

    @PostMapping
    public ApiResponse<ConversationResponse> create(
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return ApiResponse.ok(
                chatbotService.createConversation(principal)
        );
    }

    @GetMapping("/{conversationId}/messages")
    public ApiResponse<List<ChatbotMessageDto>> messages(
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

    @PostMapping("/{conversationId}/messages")
    public ApiResponse<ChatbotReply> ask(
            @AuthenticationPrincipal CustomUserDetails principal,
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
    }

    @DeleteMapping("/{conversationId}")
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

    @DeleteMapping("/{conversationId}/messages/{messageId}")
    public ApiResponse<Void> deleteLatestExchange(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable("conversationId") Long conversationId,
            @PathVariable("messageId") Long messageId
    ) {
        // 메시지 삭제도 다른 기능과 동일하게 인증 정보를 서비스에 전달한다.
        chatbotService.deleteLatestExchange(
                principal,
                conversationId,
                messageId
        );

        return ApiResponse.ok(null);
    }
}