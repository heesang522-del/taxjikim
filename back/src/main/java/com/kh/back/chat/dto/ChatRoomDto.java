package com.kh.back.chat.dto;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.kh.back.common.util.DateTimeUtil;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
public class ChatRoomDto {
    private Long roomId;
    private Long userId;    // 상담 요청 회원
    private Long adminId;   // 응대 관리자, 입장 전에는 NULL
    private String status;  // WAITING / IN_PROGRESS / CLOSED
    private LocalDateTime createdAt;
    private LocalDateTime closedAt; // 30일 삭제 배치 기준 시점

    @JsonGetter("createdAt")
    public String getCreatedAtJson() {
        return DateTimeUtil.toSeoulString(createdAt);
    }

    @JsonGetter("closedAt")
    public String getClosedAtJson() {
        return DateTimeUtil.toSeoulString(closedAt);
    }
}
