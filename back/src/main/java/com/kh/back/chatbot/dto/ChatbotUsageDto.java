package com.kh.back.chatbot.dto;

import lombok.Builder;

import java.time.LocalDate;

@Builder
public record ChatbotUsageDto(
        LocalDate usageDate,
        int dailyLimit,
        int used,
        int remaining
) {
}
