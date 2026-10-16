package com.kh.back.post.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.kh.back.common.util.DateTimeUtil;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileMetadataDto {
    // 14개 DB 스키마(FILE_META) 컬럼 및 서비스 연동 필드
    private Long fileId;
    private Long postId;
    private Long userId;

    private String originalFileName; // 원본 파일명
    private String storedFileName;   // 서버에 저장된 파일명 (UUID 등)
    private String filePath;         // 파일 저장 경로
    private String fileExtension;    // 🌟 확장자 (setFileExtension 제공)
    private Long fileSize;           // 파일 크기
    private String fileType;         // 🌟 파일 구분 (POST / RESOURCE 등, setFileType 제공)

    // 기존 레거시 호환 필드 (필요시 유지)
    private Long uploadId;
    private String originName;
    private String storedName;
    private String uploadPath;
    private Integer imageOrder;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private String isDeleted;

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