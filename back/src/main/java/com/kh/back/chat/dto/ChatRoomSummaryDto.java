package com.kh.back.chat.dto;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.kh.back.common.util.DateTimeUtil;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// 관리자용 상담방 목록(대기/진행중/종료)에 쓰는 요약 정보
@Getter
@Setter
public class ChatRoomSummaryDto {
    private Long roomId;
    private Long userId;
    private String nickname;      // 상담 요청 회원 닉네임
    private String lastMessage;   // 가장 최근 메시지 미리보기
    private Long adminId;
    private String status;
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
