package com.kh.back.chatbot.dto;

// AI 답변과 오늘 남은 질문 수를 반환한다.
public record ChatbotReply(String reply, int remaining) {
}