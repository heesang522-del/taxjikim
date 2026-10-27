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
    // null이면 삭제 안 됨, 'SELF'/'ADMIN'이면 삭제됨(DB DELETED_BY_TYPE 컬럼). 삭제된 경우 content/writer는 내려주지 않는다.
    // 부모가 삭제됐어도 살아있는 답글이 있으면 부모 자리를 유지한 채 이 값만 채워서 내려준다.
    private String deletedByType;


    @JsonGetter("createdAt")
    public String getCreatedAtJson() {
        return DateTimeUtil.toSeoulString(createdAt);
    }

    @JsonGetter("updatedAt")
    public String getUpdatedAtJson() {
        return DateTimeUtil.toSeoulString(updatedAt);
    }
}
