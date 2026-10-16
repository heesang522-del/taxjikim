package com.kh.back.post.dto;

import lombok.Data;
import java.util.List;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.kh.back.common.util.DateTimeUtil;

@Data
public class PostDto {
    private Long postId;
    private Long userId; // 작성자 ID (USERS.USER_ID)
    private String writer; // 작성자 닉네임 또는 이름

    // 🌟 DB 스키마(POSTS)에 맞춘 필수 필드 추가
    private String boardType;    // INFO / TIP / NOTICE / FAQ / QNA
    private boolean isPublic = true; // QNA 전용 공개 여부 (기본값 true)

    private String title;
    private String content;
    private int viewCount;
    private String answerStatus;

    // 날짜 및 페이징 관련 필드
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String thumbnailPath;

    // 🌟 통합 파일 메타데이터 필드 체계로 통일 (FILE_META 및 Controller 연동)
    private List<FileMetadataDto> files;         // 상세 조회 시 전달되는 통합 파일 리스트
    private List<String> savedFileNames;        // 등록/수정 시 업로드된 파일명 목록
    private List<Long> deleteFileIds;           // 수정 시 삭제할 파일 ID 목록

    @JsonGetter("createdAt")
    public String getCreatedAtJson() {
        return DateTimeUtil.toSeoulString(createdAt);
    }

    @JsonGetter("updatedAt")
    public String getUpdatedAtJson() {
        return DateTimeUtil.toSeoulString(updatedAt);
    }
}