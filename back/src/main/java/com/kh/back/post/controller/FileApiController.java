package com.kh.back.post.controller;

import com.kh.back.common.dto.ApiResponse;
import com.kh.back.common.validation.InputValidator;
import com.kh.back.post.dto.FileMetadataRequestDto;
import com.kh.back.post.service.CloudFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileApiController {
    private final CloudFileService cloudFileService;

    @GetMapping("/upload-urls")
    public ApiResponse<Map<String, String>> getUploadUrl(@RequestParam String fileName) {
        return ApiResponse.ok(cloudFileService.generateUploadPresignedUrl(fileName));
    }

    @PostMapping("/metadata")
    public ResponseEntity<ApiResponse<Void>> saveFileMetadata(@RequestBody FileMetadataRequestDto request) {
        if (request.fileSize() == null) {
            InputValidator.reject("fileSize", "필수 입력 항목입니다.");
        }
        cloudFileService.saveFileMetadata(request.originalFileName(), request.storedFileName(), request.fileSize());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(null));
    }

    @GetMapping("/download-urls")
    public ApiResponse<String> getDownloadUrl(@RequestParam String storedFileName) {
        return ApiResponse.ok(cloudFileService.generateDownloadPresignedUrl(storedFileName));
    }
}
