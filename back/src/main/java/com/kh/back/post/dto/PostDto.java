package com.post.post.dto;

import lombok.Data;
import java.util.List;
import java.time.LocalDateTime;

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
    private LocalDateTime createAt;
    private LocalDateTime updatedAt;
    private String formattedCreateAt;
    private String thumbnailPath;

    // 이미지 리스트 필드
    private List<PostImageDto> images;
    private List<String> savedFileNames;
    private List<Long> deleteImageIds;
}