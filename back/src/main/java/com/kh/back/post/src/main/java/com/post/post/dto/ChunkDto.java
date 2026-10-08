package com.post.post.dto;


import org.springframework.web.multipart.MultipartFile;


public class ChunkDto {
    private MultipartFile file;
    private String uploadId; // 추가
    private String fileUid;  // 기존에 쓰던 것과 병행하거나 통일
    private int chunkIndex;
    private int totalChunks;
    private String originalName;

    // Getter & Setter 추가
    public String getUploadId() {
        return uploadId != null ? uploadId : fileUid; // 둘 중 하나로 호환되도록 처리 가능
    }
    public void setUploadId(String uploadId) { this.uploadId = uploadId; }

    public String getFileUid() { return fileUid; }
    public void setFileUid(String fileUid) { this.fileUid = fileUid; }

    // ... 나머지 필드의 Getter / Setter 생략하지 않고 유지 ...
    public MultipartFile getFile() { return file; }
    public void setFile(MultipartFile file) { this.file = file; }
    public int getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }
    public int getTotalChunks() { return totalChunks; }
    public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
}