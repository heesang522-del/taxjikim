package com.kh.back.chatbot.dto;

import lombok.Builder;
import lombok.Value;

// 사용자, 대화방, 요청 ID를 묶어 실시간 질문 작업을 찾는다.
@Value
@Builder
public class ChatbotRequestKeyDto {
    Long userId;
    Long conversationId;
    String requestId;
}