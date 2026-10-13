package com.kh.back.post.dto;

import lombok.Data;
import java.util.List;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.kh.back.common.util.DateTimeUtil;

@Data
public class PostDto {
    private Long postId;
    private String writer; // 👈 작성자는 오직 이것만 사용합니다!
    private String title;
    private String content;
    private String place;
    private Long transportCost;
    private Long foodCost;
    private Long otherCost;
    private int viewCount;

    // 날짜 및 페이징 관련 필드
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String thumbnailPath;

    // 이미지 리스트 필드
    private List<PostImageDto> images;
    private List<String> savedFileNames;
    private List<Long> deleteImageIds;

    @JsonGetter("createdAt")
    public String getCreatedAtJson() {
        return DateTimeUtil.toSeoulString(createdAt);
    }

    @JsonGetter("updatedAt")
    public String getUpdatedAtJson() {
        return DateTimeUtil.toSeoulString(updatedAt);
    }
}
