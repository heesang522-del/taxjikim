package com.kh.back.comment.dto;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.kh.back.common.util.DateTimeUtil;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CommentDto {
    private Long commentId;
    private Long postId;
    private Long userId;
    private Long parentCommentId;
    private String content;
    private String writer;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // DB DELETED_AT 컬럼 그대로. null이 아니면 삭제된 댓글이고 content/writer는 내려주지 않는다.
    // 부모가 삭제됐어도 살아있는 답글이 있으면 부모 자리를 유지한 채 이 값만 채워서 내려준다.
    private LocalDateTime deletedAt;
    // 누가 지웠는지(SELF/ADMIN)만 구분하는 용도. DELETED_BY_TYPE을 기록하기 전에 삭제된 기존 댓글은
    // deletedAt이 있어도 이 값이 null일 수 있어, 삭제 여부 판단에는 쓰지 않는다.
    private String deletedByType;


    @JsonGetter("createdAt")
    public String getCreatedAtJson() {
        return DateTimeUtil.toSeoulString(createdAt);
    }

    @JsonGetter("updatedAt")
    public String getUpdatedAtJson() {
        return DateTimeUtil.toSeoulString(updatedAt);
    }

    @JsonGetter("deletedAt")
    public String getDeletedAtJson() {
        return DateTimeUtil.toSeoulString(deletedAt);
    }
}
