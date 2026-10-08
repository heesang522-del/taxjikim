package com.post.post.controller;

import com.post.post.service.CloudFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class CloudFileController {

    private final CloudFileService cloudFileService;

    /**
     * API 1: 업로드 Pre-signed URL 요청
     */
    @GetMapping("/presigned-url")
    public ResponseEntity<Map<String, String>> getUploadUrl(@RequestParam("fileName") String fileName) {
        Map<String, String> response = cloudFileService.generateUploadPresignedUrl(fileName);
        return ResponseEntity.ok(response);
    }

    /**
     * API 2: S3 직통 업로드 완료 후 메타데이터 DB 저장
     */
    @PostMapping("/metadata")
    public ResponseEntity<String> saveFileMetadata(@RequestBody Map<String, Object> requestData) {
        String originalFileName = (String) requestData.get("originalFileName");
        String storedFileName = (String) requestData.get("storedFileName");
        long fileSize = Long.parseLong(requestData.get("fileSize").toString());

        cloudFileService.saveFileMetadata(originalFileName, storedFileName, fileSize);
        return ResponseEntity.ok("File metadata saved successfully");
    }

    /**
     * API 3: 다운로드 Pre-signed URL 요청
     */
    @GetMapping("/download-url")
    public ResponseEntity<String> getDownloadUrl(@RequestParam("storedFileName") String storedFileName) {
        String downloadUrl = cloudFileService.generateDownloadPresignedUrl(storedFileName);
        return ResponseEntity.ok(downloadUrl);
    }
}