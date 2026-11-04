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
@AllArgsConstructor
@NoArgsConstructor
public class ChatbotMessageDto {

    private Long messageId; // 메세지 id
    private Long conversationId; // 대화 id
    private String role; //역할
    private String content; // 제목
    private String status; // 상태
    private LocalDateTime createdAt; // 생성시간

    public MessageResponse toResponse() { // db에서 조회한 메세지 정보를 프론트에 전달할 응답 객체로 바꾸는 메서드
        // DB의 한국 시각에 시간대를 적용해 응답한다.
        OffsetDateTime responseCreatedAt = createdAt == null
                ? null
                : createdAt.atZone(ZoneId.of("Asia/Seoul"))
                .toOffsetDateTime(); // 생성 시간이 있으면 한국 시간대를 적용하고 없으면 null을 저장한다

        return MessageResponse.builder() // 응답객체 만들기 시작
                .messageId(messageId) // 메세지 번호 설정
                .role(role) // 질문(user)또는 답변 (assistant) 설정
                .content(content) // 메세지 내용 설정
                .status(status) // 처리 상태 설정
                .createdAt(responseCreatedAt) // 생성 시각 설정
                .build(); // 설정한 값으로 객체 완성
    }

    @Builder
    public record MessageResponse(
            Long messageId,
            String role,
            String content,
            String status,
            OffsetDateTime createdAt // 날짜 시각이 어느 시간대 기준인지 프론트에 명확하게 전달하기 위해서 사용
    ) {
    }
}