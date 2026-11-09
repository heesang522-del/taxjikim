package com.kh.back.report.dto;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.kh.back.common.util.DateTimeUtil;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ReportDto {
    private Long reportId;
    private String targetType; // POST / COMMENT
    private Long targetId;     // targetType=POST면 POST_ID, COMMENT면 COMMENT_ID
    private Long targetUserId; // 피신고 회원(글 작성자). 신고 시점의 작성자를 그대로 남긴다.
    private String reasonDetail;
    private String status;     // PENDING / DONE
    private String result;     // DELETE / REJECT, 처리 전이면 null
    private Long processedBy;
    private LocalDateTime processedAt;
    private Long reporterId;
    private String reporterNickname;
    private String targetUserNickname; // 피신고 회원(글·댓글 작성자)의 닉네임
    private LocalDateTime createdAt;

    // 신고 대상 표시용 조인 결과. targetType에 해당하는 쪽만 값이 채워진다.
    private String postTitle;
    private LocalDateTime postDeletedAt;
    private String postDeletedByType; // SELF / ADMIN

    private String commentContent;
    private LocalDateTime commentDeletedAt;
    private String commentDeletedByType; // SELF / ADMIN

    @JsonGetter("createdAt")
    public String getCreatedAtJson() {
        return DateTimeUtil.toSeoulString(createdAt);
    }

    @JsonGetter("processedAt")
    public String getProcessedAtJson() {
        return DateTimeUtil.toSeoulString(processedAt);
    }

    @JsonGetter("postDeletedAt")
    public String getPostDeletedAtJson() {
        return DateTimeUtil.toSeoulString(postDeletedAt);
    }

    @JsonGetter("commentDeletedAt")
    public String getCommentDeletedAtJson() {
        return DateTimeUtil.toSeoulString(commentDeletedAt);
    }
}
