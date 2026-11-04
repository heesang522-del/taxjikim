package com.kh.back.chatbot.dto;

import lombok.Builder;

import java.time.LocalDate;

@Builder
public record ChatbotUsageDto(
        LocalDate usageDate, // 사용일시
        int dailyLimit, // 하루 제한횟수
        int used, // 사용된 횟수
        int remaining // 남은 횟수
) {
}
