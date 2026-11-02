package com.kh.back.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotMessageDto {

    // user: 사용자 질문, assistant: AI 답변
    private String role;

    // 질문 또는 답변 내용
    private String content;
}