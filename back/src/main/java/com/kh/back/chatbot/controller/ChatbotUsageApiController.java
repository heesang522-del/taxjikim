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
    public ApiResponse<ChatbotUsageDto> usage(
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return ApiResponse.ok(
                chatbotUsageService.getUsage(principal)
        );
    }
}