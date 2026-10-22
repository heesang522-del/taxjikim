package com.kh.back.post.dto;

import lombok.Getter;
import lombok.Setter;

import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class ChunkDto {

    private MultipartFile file;

    private String uploadId;

    private String fileUid;

    private int chunkIndex;

    private int totalChunks;

    private String originalName;

    // 기존 게시글에 첨부할 때 전달한다.
    // 새 글 작성 중에는 null이다.
    private Long postId;

    public String getUploadId() {
        return uploadId != null ? uploadId : fileUid;
    }
}