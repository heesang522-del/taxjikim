package com.kh.back.chatbot.service;

import com.kh.back.auth.security.CustomUserDetails;

public interface ChatbotSseService {

    void submitQuestion(
            CustomUserDetails principal,
            Long conversationId,
            String requestId,
            String message
    );
}   // 챗봇 질문을 비동기로 처리하도록 요청하는 서비스 인터페이스