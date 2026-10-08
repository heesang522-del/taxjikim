package com.post.post.service;

import com.post.post.dto.FileMetaDataDto;
import com.post.post.mapper.FileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CloudFileService {

    private final S3Presigner s3Presigner;
    private final FileMapper fileMapper;

    // 🌟 application.properties의 aws.s3.bucket-name과 정확히 일치시킴
    @Value("${aws.s3.bucket}")
    private String bucketName;

    /**
     * 1. 업로드용 Pre-signed URL 생성
     */
    public Map<String, String> generateUploadPresignedUrl(String originalFileName) {
        String storedFileName = UUID.randomUUID() + "_" + originalFileName;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(storedFileName)
                .contentType("application/octet-stream")
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .putObjectRequest(putObjectRequest)
                .build();

        String presignedUrl = s3Presigner.presignPutObject(presignRequest).url().toString();

        Map<String, String> result = new HashMap<>();
        result.put("presignedUrl", presignedUrl);
        result.put("storedFileName", storedFileName);
        return result;
    }

    /**
     * 2. 다운로드용 Pre-signed URL 생성
     */
    public String generateDownloadPresignedUrl(String storedFileName) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(storedFileName)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    /**
     * 3. 업로드 완료 후 메타데이터 DB 저장
     */
    @Transactional
    public void saveFileMetadata(String originalFileName, String storedFileName, long fileSize) {
        FileMetaDataDto meta = FileMetaDataDto.builder()
                .originalFileName(originalFileName)
                .storedFileName(storedFileName)
                .filePath("s3://" + bucketName + "/" + storedFileName)
                .fileSize(fileSize)
                .isDeleted("N")
                .build();

        fileMapper.insertFileMeta(meta);
    }
}