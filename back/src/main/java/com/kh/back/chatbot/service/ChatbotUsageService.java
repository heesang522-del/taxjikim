package com.kh.back.chatbot.service;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.chatbot.dto.ChatbotUsageDto;

import java.time.LocalDate;

public interface ChatbotUsageService {

    void checkReadRate(Long userId);

    ChatbotUsageDto getUsage(
            CustomUserDetails principal
    );


    ChatbotUsageDto reserveUsage(
            CustomUserDetails principal,
            LocalDate usageDate
    );

    void releaseQuestionUsage(
            Long userId,
            Long conversationId,
            Long questionId
    );
}