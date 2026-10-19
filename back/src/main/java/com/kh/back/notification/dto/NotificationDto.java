package com.kh.back.notification.dto;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.kh.back.common.util.DateTimeUtil;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class NotificationDto {
    private Long notificationId;
    private Long userId;
    private String type;        // 알림 종류, 예: NOTICE
    private String targetType;  // 알림이 가리키는 대상 종류, 예: POST
    private Long targetId;      // 대상 ID (공지사항 게시글 ID)
    private String title;       // 제목
    private String message;     // 본문 (예: 댓글 알림의 "닉네임: 내용 미리보기")
    private boolean isRead;      // 읽음 여부 (Y/N)
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;


    // Jackson 기본 직렬화 대신 한국 시간대(Asia/Seoul) 문자열로 고정 출력
    // (getCreatedAt()은 내부 로직용 LocalDateTime 그대로 유지)
    @JsonGetter("createdAt")
    public String getCreatedAtJson() {
        return DateTimeUtil.toSeoulString(createdAt);
    }

    // deletedAt도 동일하게 한국 시간대 문자열로 응답
    @JsonGetter("deletedAt")
    public String getDeletedAtJson() {
        return DateTimeUtil.toSeoulString(deletedAt);
    }
}
