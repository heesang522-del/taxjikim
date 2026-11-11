package com.kh.back.chat.dto;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.kh.back.common.util.DateTimeUtil;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// 회원 상담내역 목록에 필요한 종료 상담 요약만 반환한다.
@Getter
@Setter
public class ChatHistoryRoomDto {
    private Long roomId;
    private String lastMessage;
    private LocalDateTime createdAt;
    private LocalDateTime closedAt;

    @JsonGetter("createdAt")
    public String getCreatedAtJson() {
        return DateTimeUtil.toSeoulString(createdAt);
    }

    @JsonGetter("closedAt")
    public String getClosedAtJson() {
        return DateTimeUtil.toSeoulString(closedAt);
    }
}
