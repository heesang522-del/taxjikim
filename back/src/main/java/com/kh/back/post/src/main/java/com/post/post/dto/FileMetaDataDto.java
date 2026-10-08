package com.post.post.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor  // 🌟 기본 생성자 추가
@AllArgsConstructor // 모든 필드 생성자
@Builder
public class FileMetaDataDto {
    private Long fileId;
    private String originalFileName;
    private String storedFileName;
    private String filePath;
    private Long fileSize;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
    private String isDeleted;
}