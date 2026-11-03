package com.kh.back.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotConversationDto {

    private Long conversationId;
    private Long userId;
    private String title;
    private Long revision;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ConversationResponse toResponse() {
        ZoneId zone = ZoneId.of("Asia/Seoul");

        // DB의 한국 시간 값을 시간대가 포함된 응답으로 변환한다.
        return ConversationResponse.builder()
                .conversationId(conversationId)
                .title(title)
                .createdAt(createdAt.atZone(zone).toOffsetDateTime())
                .updatedAt(updatedAt.atZone(zone).toOffsetDateTime())
                .build();
    }

    @Builder
    public record ConversationResponse(
            Long conversationId,
            String title,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }
}