package com.kh.back.post.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostImageDto {

    private Long uploadId;
    private Long postId;
    private String originName;
    private String storedName;
    private String uploadPath;
    private Long fileSize;      // 🌟 이 필드를 추가해 주세요!
    private Integer imageOrder;
}