package com.kh.back.chatbot.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 요청
@Getter
@Setter
@NoArgsConstructor
public class ChatbotRequest {
    private String message;
}

