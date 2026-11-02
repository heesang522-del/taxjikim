package com.kh.back.chatbot.controller;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.chatbot.dto.ChatbotMessageDto;
import com.kh.back.chatbot.dto.ChatbotReply;
import com.kh.back.chatbot.dto.ChatbotRequest;
import com.kh.back.chatbot.service.ChatbotService;
import com.kh.back.common.dto.ApiResponse;
import com.kh.back.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chatbot/messages")
@RequiredArgsConstructor
public class ChatbotApiController {

    private final ChatbotService chatbotService;

    @PostMapping
    public ApiResponse<ChatbotReply> ask(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestBody ChatbotRequest request
    ) {
        // 요청 본문 대신 인증 정보에서 사용자 번호를 가져온다.
        Long userId = requireUserId(principal);

        return ApiResponse.ok(
                chatbotService.ask(userId, request.getMessage())
        );
    }

    @GetMapping
    public ApiResponse<List<ChatbotMessageDto>> history(
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        Long userId = requireUserId(principal);

        return ApiResponse.ok(
                chatbotService.getHistory(userId)
        );
    }

    @DeleteMapping
    public ApiResponse<Void> clear(
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        Long userId = requireUserId(principal);

        // 로그인한 사용자의 대화 기록만 초기화한다.
        chatbotService.clearHistory(userId);

        return ApiResponse.ok(null);
    }

    private Long requireUserId(CustomUserDetails principal) {
        if (principal == null
                || principal.getUserId() == null
                || principal.getUserId() <= 0) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED",
                    "로그인이 필요합니다."
            );
        }

        return principal.getUserId();
    }
}