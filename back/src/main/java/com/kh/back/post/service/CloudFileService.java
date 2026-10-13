package com.kh.back.post.service;

import java.util.Map;

// 호출자는 업무 인터페이스를 사용하고 구현체가 처리 흐름을 담당한다.
public interface CloudFileService {
    Map<String, String> generateUploadPresignedUrl(String originalFileName);

    String generateDownloadPresignedUrl(String storedFileName);

    void saveFileMetadata(String originalFileName, String storedFileName, long fileSize);
}
