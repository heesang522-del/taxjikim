package com.kh.back.common.validation;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.common.exception.ApiException;

import com.kh.back.post.dto.ChunkDto;
import com.kh.back.post.dto.FileMetadataDto;
import com.kh.back.post.dto.PostDto;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
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

@Component
public class PostValidator {

    private static final int MAX_FILE_COUNT = 5;

    private static final Set<String> BOARD_TYPES = Set.of(
            "INFO",
            "TIP",
            "NOTICE",
            "FAQ",
            "QNA"
    );

    private static final String MEDIA_NAME_PATTERN =
            ".*\\.(jpg|jpeg|png|gif|webp|mp4|webm|mov)$";

    private static final String SAVED_FILE_NAME_PATTERN =
            "(?i)[0-9a-f]{8}-[0-9a-f]{4}-"
                    + "[0-9a-f]{4}-[0-9a-f]{4}-"
                    + "[0-9a-f]{12}\\."
                    + "(jpg|jpeg|png|gif|webp|mp4|webm|mov)";

    /**
     * 현재 로그인한 사용자의 실제 회원 번호를 반환한다.
     */
    public Long requireLogin() {

        Authentication authentication =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal()
                instanceof CustomUserDetails details)) {

            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED",
                    "로그인이 필요합니다."
            );
        }

        Long userId = details.getUserId();

        if (userId == null || userId <= 0) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED",
                    "회원 인증 정보를 확인할 수 없습니다."
            );
        }

        return userId;
    }

    /**
     * 서버에서 인증한 관리자 권한을 확인한다.
     */
    public boolean isAdmin() {

        requireLogin();

        Authentication authentication =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        return authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        "ROLE_ADMIN".equals(
                                authority.getAuthority()
                        )
                );
    }

    /**
     * 비로그인도 허용하는 목록 조회에서 쓴다. 로그인하지 않았으면 null을 반환한다.
     */
    public Long currentUserIdOrNull() {

        Authentication authentication =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        if (authentication == null
                || !(authentication.getPrincipal()
                instanceof CustomUserDetails details)) {
            return null;
        }

        return details.getUserId();
    }

    /**
     * 비로그인도 허용하는 목록 조회에서 쓴다. 로그인하지 않았거나 관리자가 아니면 false를 반환한다.
     */
    public boolean hasAdminRole() {

        Authentication authentication =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        if (authentication == null
                || !(authentication.getPrincipal()
                instanceof CustomUserDetails)) {
            return false;
        }

        return authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        "ROLE_ADMIN".equals(
                                authority.getAuthority()
                        )
                );
    }

    /**
     * 관리자와 일반 사용자 모두 게시글을 작성할 수 있다.
     * 공지사항과 FAQ는 관리자만 작성할 수 있다.
     */
    public Long requireCreate(PostDto post) {

        Long userId = requireLogin();

        // 한국어 게시판 이름도 여기에서 영문 코드로 변환된다.
        validatePost(post);

        requireBoardPermission(post.getBoardType());

        return userId;
    }

    /**
     * 관리자 또는 실제 작성자만 게시글을 변경할 수 있다.
     *
     * 반드시 DB에서 조회한 게시글을 전달한다.
     */
    public Long requireChange(PostDto post) {

        Long userId = requireLogin();

        if (post == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "POST_NOT_FOUND",
                    "게시글을 찾을 수 없습니다."
            );
        }

        // 관리자는 다른 사용자의 게시글도 변경할 수 있다.
        if (isAdmin()) {
            return userId;
        }

        if (!Objects.equals(userId, post.getUserId())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "ACCESS_DENIED",
                    "본인이 작성한 글만 변경할 수 있습니다."
            );
        }

        requireBoardPermission(post.getBoardType());

        return userId;
    }

    /**
     * 게시글 입력값을 검사한다.
     */
    public void validatePost(PostDto post) {

        if (post == null) {
            throw invalid("게시글 입력값이 없습니다.");
        }

        String title = post.getTitle();

        if (title == null
                || title.isBlank()
                || title.codePointCount(0, title.length()) > 255) {

            throw invalid(
                    "title",
                    "제목은 1~255자로 입력해주세요."
            );
        }

        String content = post.getContent();

        if (content == null || content.isBlank()) {
            throw invalid(
                    "content",
                    "본문을 입력해주세요."
            );
        }

        // MySQL TEXT 컬럼의 저장 크기를 초과하지 않도록 검사한다.
        if (content.getBytes(StandardCharsets.UTF_8).length > 65535) {
            throw invalid(
                    "content",
                    "본문이 저장 가능한 크기를 초과했습니다."
            );
        }

        // 프론트는 수정하지 않고 서버에서 한국어 값을 변환한다.
        post.setBoardType(
                normalizeBoardType(post.getBoardType())
        );
    }

    /**
     * 게시글 수정 권한과 입력값을 검사한다.
     */
    public void validateUpdate(
            PostDto request,
            PostDto existing
    ) {

        requireChange(existing);

        validatePost(request);

        // 기존 데이터도 비교 전에 같은 기준으로 정규화한다.
        String existingBoardType =
                normalizeBoardType(existing.getBoardType());

        if (!Objects.equals(
                request.getBoardType(),
                existingBoardType
        )) {
            throw invalid(
                    "boardType",
                    "게시글 수정 시 게시판 종류를 변경할 수 없습니다."
            );
        }
    }

    /**
     * 기존 게시글 대상 업로드는 관리자 또는 작성자만 허용한다.
     * 새 게시글 작성 중 업로드는 로그인한 사용자에게 허용한다.
     */
    public Long requireUpload(
            ChunkDto dto,
            PostDto targetPost
    ) {

        Long userId = requireLogin();

        if (dto == null) {
            throw invalid("업로드 입력값이 없습니다.");
        }

        if (dto.getPostId() != null) {

            requireChange(targetPost);

            if (!Objects.equals(
                    dto.getPostId(),
                    targetPost.getPostId()
            )) {
                throw invalid(
                        "postId",
                        "업로드 대상 게시글이 일치하지 않습니다."
                );
            }
        }

        String uploadId = dto.getUploadId();

        if (uploadId == null
                || !uploadId.matches("[A-Za-z0-9_-]{1,80}")) {

            throw invalid(
                    "uploadId",
                    "업로드 ID 형식이 올바르지 않습니다."
            );
        }

        if (dto.getTotalChunks() < 1
                || dto.getChunkIndex() < 0
                || dto.getChunkIndex() >= dto.getTotalChunks()) {

            throw invalid(
                    "chunkIndex",
                    "청크 번호와 전체 청크 수를 확인해주세요."
            );
        }

        if (dto.getFile() == null || dto.getFile().isEmpty()) {
            throw invalid(
                    "file",
                    "업로드 파일이 비어 있습니다."
            );
        }

        validateOriginalFileName(dto.getOriginalName());

        return userId;
    }

    /**
     * 같은 업로드 ID의 파일 정보가 중간에 바뀌지 않도록 검사한다.
     */
    public void validateUploadMetadata(
            String existing,
            String requested
    ) {

        if (!Objects.equals(existing, requested)) {
            throw invalid(
                    "uploadId",
                    "같은 업로드 ID의 파일명·청크 수·대상 글을 변경할 수 없습니다."
            );
        }
    }

    /**
     * 게시글에 연결할 임시 파일의 상태와 업로더를 검사한다.
     *
     * 반드시 DB에서 조회한 파일 정보를 전달한다.
     */
    public void requirePendingFile(FileMetadataDto file) {

        Long userId = requireLogin();

        if (file == null
                || file.getPostId() != null
                || file.getDeletedAt() != null
                || !"POST_PENDING".equals(file.getFileType())) {

            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "FILE_NOT_AVAILABLE",
                    "이미 사용되었거나 사용할 수 없는 업로드 파일입니다."
            );
        }

        if (isAdmin()) {
            return;
        }

        if (!Objects.equals(userId, file.getUserId())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "ACCESS_DENIED",
                    "본인이 업로드한 파일만 첨부할 수 있습니다."
            );
        }
    }

    /**
     * 삭제 요청 파일이 해당 게시글의 첨부파일인지 검사한다.
     */
    public List<Long> validateDeleteFiles(
            List<FileMetadataDto> existingFiles,
            List<Long> deleteFileIds
    ) {

        if (deleteFileIds == null || deleteFileIds.isEmpty()) {
            return List.of();
        }

        Set<Long> attachedIds = new HashSet<>();

        if (existingFiles != null) {
            for (FileMetadataDto file : existingFiles) {
                if (file != null && file.getFileId() != null) {
                    attachedIds.add(file.getFileId());
                }
            }
        }

        Set<Long> uniqueIds = new HashSet<>();

        for (Long fileId : deleteFileIds) {

            if (fileId == null
                    || fileId <= 0
                    || !uniqueIds.add(fileId)) {

                throw invalid(
                        "deleteFileIds",
                        "삭제할 첨부파일 ID를 확인해주세요."
                );
            }

            // 관리자도 다른 게시글의 파일을 섞어 삭제할 수 없다.
            if (!attachedIds.contains(fileId)) {
                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "ACCESS_DENIED",
                        "해당 게시글의 첨부파일만 삭제할 수 있습니다."
                );
            }
        }

        return new ArrayList<>(deleteFileIds);
    }

    /**
     * 서버가 생성한 UUID 형식의 저장 파일명인지 검사한다.
     */
    public List<String> validateSavedFileNames(
            List<String> names
    ) {

        if (names == null || names.isEmpty()) {
            return List.of();
        }

        Set<String> uniqueNames = new HashSet<>();

        for (String name : names) {

            if (name == null
                    || !name.matches(SAVED_FILE_NAME_PATTERN)) {

                throw invalid(
                        "savedFileNames",
                        "첨부파일명을 확인해주세요."
                );
            }

            if (!uniqueNames.add(name)) {
                throw invalid(
                        "savedFileNames",
                        "같은 파일을 중복 첨부할 수 없습니다."
                );
            }
        }

        return new ArrayList<>(names);
    }

    /**
     * 최종 첨부파일 개수를 검사한다.
     */
    public void validateFileCount(int count) {

        if (count < 0 || count > MAX_FILE_COUNT) {
            throw invalid(
                    "files",
                    "첨부파일은 최대 "
                            + MAX_FILE_COUNT
                            + "개까지 등록할 수 있습니다."
            );
        }
    }

    /**
     * 페이지 조회 범위를 검사한다.
     */
    public void validatePage(int offset, int size) {

        if (offset < 0 || size < 1 || size > 100) {
            throw invalid("페이지 범위를 확인해주세요.");
        }
    }

    /**
     * 병합된 파일의 기본 형식 식별 바이트를 검사한다.
     */
    public void validateMedia(
            Path file,
            String extension
    ) throws IOException {

        if (file == null || extension == null) {
            throw invalid("파일 정보가 없습니다.");
        }

        byte[] header;

        try (InputStream input = Files.newInputStream(file)) {
            header = input.readNBytes(16);
        }

        String signature = new String(
                header,
                StandardCharsets.ISO_8859_1
        );

        String normalizedExtension =
                extension.toLowerCase(Locale.ROOT);

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
                            new byte[]{
                                    (byte) 137,
                                    80,
                                    78,
                                    71,
                                    13,
                                    10,
                                    26,
                                    10
                            }
                    );

            case ".gif" ->
                    signature.startsWith("GIF87a")
                            || signature.startsWith("GIF89a");

            case ".webp" ->
                    signature.startsWith("RIFF")
                            && signature.length() >= 12
                            && signature.substring(8, 12)
                            .equals("WEBP");

            case ".mp4", ".mov" ->
                    signature.length() >= 12
                            && signature.substring(4, 8)
                            .equals("ftyp");

            case ".webm" ->
                    header.length >= 4
                            && Arrays.equals(
                            Arrays.copyOf(header, 4),
                            new byte[]{
                                    26,
                                    69,
                                    (byte) 223,
                                    (byte) 163
                            }
                    );

            default -> false;
        };

        if (!valid) {
            throw invalid(
                    "file",
                    "파일 내용이 허용된 사진·동영상 형식과 일치하지 않습니다."
            );
        }
    }

    /**
     * 기존 직접 파일 업로드 방식의 등록 검증도 유지한다.
     */
    public void validateSave(List<MultipartFile> files) {

        checkFileLimit(0, files);

        checkFileExtensions(files);
    }

    /**
     * 기존 직접 파일 업로드 방식의 수정 검증도 유지한다.
     */
    public void validateUpdate(
            List<FileMetadataDto> existingImages,
            List<Long> deleteImageIds,
            List<MultipartFile> files
    ) {

        List<FileMetadataDto> existing =
                existingImages == null
                        ? List.of()
                        : existingImages;

        List<Long> deleteIds = validateDeleteFiles(
                existing,
                deleteImageIds
        );

        int remainingCount =
                existing.size() - deleteIds.size();

        checkFileLimit(remainingCount, files);

        checkFileExtensions(files);
    }

    /**
     * 한국어 화면 이름과 영문 코드를 같은 DB 코드로 변환한다.
     */
    private String normalizeBoardType(String value) {

        if (value == null || value.isBlank()) {
            throw invalid(
                    "boardType",
                    "게시판 종류가 필요합니다."
            );
        }

        return toBoardTypeCode(value);
    }

    /**
     * 목록 조회 필터용 정규화. 값이 없으면 전체 게시판(필터 없음)으로 본다.
     */
    public String normalizeBoardTypeFilter(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return toBoardTypeCode(value);
    }

    private String toBoardTypeCode(String value) {

        String trimmed = value.trim();

        String boardType = switch (trimmed) {
            case "정보 공유" -> "INFO";
            case "팁" -> "TIP";
            case "공지" -> "NOTICE";
            default -> trimmed.toUpperCase(Locale.ROOT);
        };

        if (!BOARD_TYPES.contains(boardType)) {
            throw invalid(
                    "boardType",
                    "지원하지 않는 게시판 종류입니다."
            );
        }

        return boardType;
    }

    /**
     * 한국어로 저장된 기존 데이터도 정규화한 뒤 권한을 검사한다.
     */
    private void requireBoardPermission(String value) {

        String boardType = normalizeBoardType(value);

        boolean administratorOnly =
                "NOTICE".equals(boardType)
                        || "FAQ".equals(boardType);

        if (administratorOnly && !isAdmin()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "ACCESS_DENIED",
                    "공지사항과 FAQ는 관리자만 변경할 수 있습니다."
            );
        }
    }

    private void validateOriginalFileName(String filename) {

        if (filename == null
                || filename.isBlank()
                || filename.length() > 500) {

            throw invalid(
                    "file",
                    "원본 파일명을 확인해주세요."
            );
        }

        if (filename.contains("/") || filename.contains("\\")) {
            throw invalid(
                    "file",
                    "파일명에 경로를 포함할 수 없습니다."
            );
        }

        if (!filename.toLowerCase(Locale.ROOT)
                .matches(MEDIA_NAME_PATTERN)) {

            throw invalid(
                    "file",
                    "사진·동영상만 업로드할 수 있습니다."
            );
        }
    }

    private void checkFileLimit(
            int baseCount,
            List<MultipartFile> files
    ) {

        if (baseCount < 0) {
            throw invalid(
                    "files",
                    "기존 첨부파일 개수가 올바르지 않습니다."
            );
        }

        long newFileCount = 0;

        if (files != null) {
            for (MultipartFile file : files) {

                if (file == null) {
                    throw invalid(
                            "files",
                            "첨부파일 입력값을 확인해주세요."
                    );
                }

                if (!file.isEmpty()) {
                    newFileCount++;
                }
            }
        }

        if (baseCount + newFileCount > MAX_FILE_COUNT) {
            throw invalid(
                    "files",
                    "첨부파일은 최대 "
                            + MAX_FILE_COUNT
                            + "개까지 등록할 수 있습니다."
            );
        }
    }

    private void checkFileExtensions(
            List<MultipartFile> files
    ) {

        if (files == null) {
            return;
        }

        for (MultipartFile file : files) {

            if (file == null) {
                throw invalid(
                        "files",
                        "첨부파일 입력값을 확인해주세요."
                );
            }

            if (file.isEmpty()) {
                continue;
            }

            validateOriginalFileName(
                    file.getOriginalFilename()
            );
        }
    }

    private ApiException invalid(String message) {

        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                message
        );
    }

    private ApiException invalid(
            String field,
            String message
    ) {

        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                message,
                Map.of(field, message)
        );
    }
}