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
@NoArgsConstructor  // 🌟 기본 생성자 추가
@AllArgsConstructor // 모든 필드 생성자
@Builder
public class FileMetadataDto {
    private Long fileId;
    private String originalFileName;
    private String storedFileName;
    private String filePath;
    private Long fileSize;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
    private String isDeleted;

    @JsonGetter("createdAt")
    public String getCreatedAtJson() {
        return DateTimeUtil.toSeoulString(createdAt);
    }

    @JsonGetter("deletedAt")
    public String getDeletedAtJson() {
        return DateTimeUtil.toSeoulString(deletedAt);
    }
}
