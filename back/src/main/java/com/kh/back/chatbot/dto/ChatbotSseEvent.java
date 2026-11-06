package com.kh.back.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotSseEvent {

    private String requestId;
    private Long conversationId;

    private Boolean accepted;
    private Boolean cancelled;

    private String delta;
    private String reply;
    private Integer remaining;

    private String code;
    private String message;
}