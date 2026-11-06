package com.kh.back.chatbot.service;

import com.kh.back.auth.security.CustomUserDetails;

public interface ChatbotSseService {

    void submitQuestion(
            CustomUserDetails principal,
            Long conversationId,
            String requestId,
            String message
    );
}