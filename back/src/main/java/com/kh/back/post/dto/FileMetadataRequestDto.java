package com.kh.back.post.dto;

public record FileMetadataRequestDto(String originalFileName, String storedFileName, Long fileSize) {
}
