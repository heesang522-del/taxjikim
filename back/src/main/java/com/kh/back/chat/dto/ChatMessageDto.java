package com.kh.back.chat.dto;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.kh.back.common.util.DateTimeUtil;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ChatMessageDto {
    private Long messageId;
    private Long roomId;
    private Long senderId; // 회원 또는 관리자
    private String content;
    private boolean isRead; // 안 읽은 메시지 배지
    private LocalDateTime createdAt;

    @JsonGetter("createdAt")
    public String getCreatedAtJson() {
        return DateTimeUtil.toSeoulString(createdAt);
    }
}
