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

    private Long conversationId; // 채팅방 고유 번호
    private Long userId; // 채팅방을 만든 사용자 번호
    private String title; // 채팅방 제목
    private Long revision; // 채팅방이 변경될때 증가하는 버전 번호
    private LocalDateTime createdAt; // 채팅방 생성 시각
    private LocalDateTime updatedAt; // 채팅방 마지막 변경 시각

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

    public record RenameRequest(String title) {
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
