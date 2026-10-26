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

    @JsonGetter("createdAt")
    public String getCreatedAtJson() {
        return DateTimeUtil.toSeoulString(createdAt);
    }

    @JsonGetter("updatedAt")
    public String getUpdatedAtJson() {
        return DateTimeUtil.toSeoulString(updatedAt);
    }
}
