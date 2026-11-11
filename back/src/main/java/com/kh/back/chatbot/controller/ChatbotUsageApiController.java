package com.kh.back.chatbot.controller;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.chatbot.dto.ChatbotUsageDto;
import com.kh.back.chatbot.service.ChatbotUsageService;
import com.kh.back.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chatbot/usage")
@RequiredArgsConstructor
public class ChatbotUsageApiController {

    private final ChatbotUsageService chatbotUsageService;

    @GetMapping
    public ApiResponse<ChatbotUsageDto> usage( @AuthenticationPrincipal CustomUserDetails principal) {
        chatbotUsageService.checkReadRate(principal.getUserId());
        return ApiResponse.ok(
                chatbotUsageService.getUsage(principal)
        );
    }
} // 로그인한 사용자의 오늘 챗봇 사용량과 남은 질문 횟수를 조회하는 api
