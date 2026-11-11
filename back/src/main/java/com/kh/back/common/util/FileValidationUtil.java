package com.kh.back.common.util;

import com.kh.back.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 파일 업로드 검증 중 특정 기능(게시글 등)과 무관하게 공통으로 적용되는 부분만 모은 유틸이다.
 * 허용 확장자·최대 개수 같은 정책값은 호출하는 쪽에서 파라미터로 넘긴다 — 이 클래스는 어떤
 * 기능의 정책인지 모른다. 게시글 소유권 확인처럼 도메인 지식이 필요한 검증은 그대로
 * PostValidator에 남아 이 유틸을 호출하는 방식으로 쓴다.
 */
public final class FileValidationUtil {

    private FileValidationUtil() {
    }

    /** 청크 업로드 ID 형식을 검사한다. */
    public static void validateUploadId(String uploadId) {

        if (uploadId == null || !uploadId.matches("[A-Za-z0-9_-]{1,80}")) {
            throw invalid("uploadId", "업로드 ID 형식이 올바르지 않습니다.");
        }
    }

    /** 청크 번호와 전체 청크 수가 서로 맞는지 검사한다. */
    public static void validateChunkIndex(int chunkIndex, int totalChunks) {

        if (totalChunks < 1 || chunkIndex < 0 || chunkIndex >= totalChunks) {
            throw invalid("chunkIndex", "청크 번호와 전체 청크 수를 확인해주세요.");
        }
    }

    /** 같은 업로드 ID의 파일 정보가 중간에 바뀌지 않았는지 검사한다. */
    public static void validateUploadMetadata(String existing, String requested) {

        if (!Objects.equals(existing, requested)) {
            throw invalid("uploadId", "같은 업로드 ID의 파일명·청크 수·대상 글을 변경할 수 없습니다.");
        }
    }

    /** 업로드 파일이 비어 있지 않은지 검사한다. */
    public static void requireNonEmpty(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw invalid("file", "업로드 파일이 비어 있습니다.");
        }
    }

    /**
     * 원본 파일명의 길이·경로 포함 여부·확장자를 검사한다.
     * allowedExtensionPattern은 소문자 파일명 전체에 적용하는 정규식이다. 예: ".*\\.(jpg|png)$"
     */
    public static void validateFileName(String filename, String allowedExtensionPattern) {

        if (filename == null || filename.isBlank() || filename.length() > 500) {
            throw invalid("file", "원본 파일명을 확인해주세요.");
        }

        if (filename.contains("/") || filename.contains("\\")) {
            throw invalid("file", "파일명에 경로를 포함할 수 없습니다.");
        }

        if (!filename.toLowerCase(Locale.ROOT).matches(allowedExtensionPattern)) {
            throw invalid("file", "사진·동영상·PDF만 업로드할 수 있습니다.");
        }
    }

    /** 목록의 각 파일(비어있지 않은 것만)에 validateFileName을 적용한다. */
    public static void validateFileExtensions(List<MultipartFile> files, String allowedExtensionPattern) {

        if (files == null) {
            return;
        }

        for (MultipartFile file : files) {

            if (file == null) {
                throw invalid("files", "첨부파일 입력값을 확인해주세요.");
            }

            if (file.isEmpty()) {
                continue;
            }

            validateFileName(file.getOriginalFilename(), allowedExtensionPattern);
        }
    }

    /** 최종 첨부파일 개수가 범위 안인지 검사한다. */
    public static void validateFileCount(int count, int maxCount) {

        if (count < 0 || count > maxCount) {
            throw invalid("files", "첨부파일은 최대 " + maxCount + "개까지 등록할 수 있습니다.");
        }
    }

    /** 기존 첨부 개수 + 새로 올리는(비어있지 않은) 파일 수가 최대 개수를 넘지 않는지 검사한다. */
    public static void validateFileCountWithNewFiles(int baseCount, List<MultipartFile> newFiles, int maxCount) {

        if (baseCount < 0) {
            throw invalid("files", "기존 첨부파일 개수가 올바르지 않습니다.");
        }

        long newFileCount = 0;

        if (newFiles != null) {
            for (MultipartFile file : newFiles) {

                if (file == null) {
                    throw invalid("files", "첨부파일 입력값을 확인해주세요.");
                }

                if (!file.isEmpty()) {
                    newFileCount++;
                }
            }
        }

        if (baseCount + newFileCount > maxCount) {
            throw invalid("files", "첨부파일은 최대 " + maxCount + "개까지 등록할 수 있습니다.");
        }
    }

    /**
     * 서버가 생성한 형식의 저장 파일명인지, 중복은 없는지 검사한다.
     * savedNamePattern은 소문자·대문자 구분 없이 전체 파일명에 적용하는 정규식이다.
     */
    public static List<String> validateSavedFileNames(List<String> names, String savedNamePattern) {

        if (names == null || names.isEmpty()) {
            return List.of();
        }

        Set<String> uniqueNames = new HashSet<>();

        for (String name : names) {

            if (name == null || !name.matches(savedNamePattern)) {
                throw invalid("savedFileNames", "첨부파일명을 확인해주세요.");
            }

            if (!uniqueNames.add(name)) {
                throw invalid("savedFileNames", "같은 파일을 중복 첨부할 수 없습니다.");
            }
        }

        return new ArrayList<>(names);
    }

    /**
     * 병합된 파일의 내용이 확장자가 주장하는 형식(매직바이트)과 실제로 맞는지 검사한다.
     * jpg·png·gif·webp·mp4·mov·webm·pdf만 지원한다 — 다른 확장자는 전부 불일치로 처리한다.
     */
    public static void validateMediaSignature(Path file, String extension) throws IOException {

        if (file == null || extension == null) {
            throw invalid("파일 정보가 없습니다.");
        }

        byte[] header;

        try (InputStream input = Files.newInputStream(file)) {
            header = input.readNBytes(16);
        }

        String signature = new String(header, StandardCharsets.ISO_8859_1);
        String normalizedExtension = extension.toLowerCase(Locale.ROOT);

        boolean valid = switch (normalizedExtension) {

            case ".jpg", ".jpeg" ->
                    header.length >= 3
                            && (header[0] & 255) == 255
                            && (header[1] & 255) == 216
                            && (header[2] & 255) == 255;

            case ".png" ->
                    header.length >= 8
                            && Arrays.equals(
                            Arrays.copyOf(header, 8),
                            new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10}
                    );

            case ".gif" ->
                    signature.startsWith("GIF87a") || signature.startsWith("GIF89a");

            case ".webp" ->
                    signature.startsWith("RIFF")
                            && signature.length() >= 12
                            && signature.substring(8, 12).equals("WEBP");

            case ".mp4", ".mov" ->
                    signature.length() >= 12 && signature.substring(4, 8).equals("ftyp");

            case ".webm" ->
                    header.length >= 4
                            && Arrays.equals(
                            Arrays.copyOf(header, 4),
                            new byte[]{26, 69, (byte) 223, (byte) 163}
                    );

            case ".pdf" ->
                    header.length >= 4
                            && signature.startsWith("%PDF");

            default -> false;
        };

        if (!valid) {
            throw invalid("file", "파일 내용이 허용된 사진·동영상·PDF 형식과 일치하지 않습니다.");
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, Map.of(field, message));
    }
}
