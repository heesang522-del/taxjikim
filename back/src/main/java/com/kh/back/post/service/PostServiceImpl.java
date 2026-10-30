package com.kh.back.post.service;

import com.kh.back.common.dto.ApiResponse;
import com.kh.back.common.dto.PageMeta;
import com.kh.back.common.exception.ApiException;
import com.kh.back.common.validation.PostValidator;
import com.kh.back.notification.service.NotificationService;
import com.kh.back.post.dto.ChunkDto;
import com.kh.back.post.dto.FileMetadataDto;
import com.kh.back.post.dto.PostDto;
import com.kh.back.post.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {
    private final PostMapper postMapper;
    private final PostValidator postValidator;
    private final NotificationService notificationService;

    private final Path tempRoot = Path.of(System.getProperty("user.dir"), "uploads", "temp").toAbsolutePath().normalize();
    private final Path uploadRoot = Path.of(System.getProperty("user.dir"), "uploads", "files").toAbsolutePath().normalize();

    // 목록 응답 한 페이지에 보여줄 글 수. 클라이언트가 바꿀 수 없는 고정값이라 상수로 둔다.
    private static final int PAGE_SIZE = 9;

    // 단일 서버에서 청크 병합이 동시에 실행되지 않게 한다.
    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized String processChunkUpload(ChunkDto dto) throws IOException {
        postValidator.requireLogin();

        PostDto targetPost = null;
        if (dto != null && dto.getPostId() != null) {
            targetPost = postMapper.lockById(dto.getPostId());
        }

        Long userId = postValidator.requireUpload(dto, targetPost);

        // 같은 업로드 ID라도 사용자별로 청크 폴더를 분리한다.
        Path directory = tempRoot.resolve(userId + "_" + dto.getUploadId()).normalize();
        Files.createDirectories(directory);
        Files.createDirectories(uploadRoot);

        Path metadataPath = directory.resolve("upload.txt");
        String requestedMetadata = dto.getTotalChunks() + "\n" + dto.getOriginalName() + "\n" + String.valueOf(dto.getPostId());

        if (Files.exists(metadataPath)) {
            postValidator.validateUploadMetadata(Files.readString(metadataPath, StandardCharsets.UTF_8), requestedMetadata);
        } else {
            Files.writeString(metadataPath, requestedMetadata, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        }

        Path chunkPath = directory.resolve("chunk_" + dto.getChunkIndex());
        try (InputStream input = dto.getFile().getInputStream();
             OutputStream output = Files.newOutputStream(chunkPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            input.transferTo(output);
        }

        for (int index = 0; index < dto.getTotalChunks(); index++) {
            if (!Files.isRegularFile(directory.resolve("chunk_" + index))) {
                return null;
            }
        }

        String originalName = dto.getOriginalName();
        String extension = originalName.substring(originalName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        String savedName = UUID.randomUUID() + extension;
        Path target = uploadRoot.resolve(savedName);

        boolean completed = false;
        try {
            try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(target, StandardOpenOption.CREATE_NEW))) {
                for (int index = 0; index < dto.getTotalChunks(); index++) {
                    Files.copy(directory.resolve("chunk_" + index), output);
                }
            }

            postValidator.validateMedia(target, extension);

            FileMetadataDto metadata = new FileMetadataDto();
            // 업로더는 요청값이 아닌 인증 정보로 지정한다.
            metadata.setUserId(userId);
            metadata.setOriginalFileName(originalName);
            metadata.setStoredFileName(savedName);
            metadata.setFilePath(target.toString());
            metadata.setFileExtension(extension.substring(1));
            metadata.setFileSize(Files.size(target));

            // 게시글에 연결되기 전에는 POST_PENDING으로 저장한다.
            // 최종 글 저장 시 소유권을 검사한 뒤 POST로 전환한다.
            postMapper.savePendingFile(metadata);

            for (int index = 0; index < dto.getTotalChunks(); index++) {
                Files.deleteIfExists(directory.resolve("chunk_" + index));
            }
            Files.deleteIfExists(metadataPath);
            Files.deleteIfExists(directory);

            completed = true;
            return savedName;
        } finally {
            if (!completed) {
                Files.deleteIfExists(target);
            }
        }
    }

    @Override
    public PostDto findById(Long postId) {
        PostDto post = postMapper.findById(postId);
        if (post == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "게시글을 찾을 수 없습니다.");
        }

        // QNA 비공개 글은 작성자 본인과 관리자만 볼 수 있다. 존재 여부가 드러나지 않도록 글이 없을 때와 같은 404로 응답한다.
        boolean isPrivateQna = "QNA".equals(post.getPostType()) && !post.isPublic();
        if (isPrivateQna) {
            Long currentUserId = postValidator.currentUserIdOrNull();
            boolean isOwner = currentUserId != null && currentUserId.equals(post.getUserId());
            if (!isOwner && !postValidator.hasAdminRole()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "게시글을 찾을 수 없습니다.");
            }
        }

        post.setFiles(postMapper.findFilesByPostId(postId));
        return post;
    }

    @Override
    public ApiResponse<List<PostDto>> getPostPage(String sort, String keyword, int page, String postType) {
        page = Math.max(page, 1);

        int totalCount = countAll(keyword, postType);
        List<PostDto> posts = findPage(sort, keyword, page, postType);

        return ApiResponse.ok(posts, PageMeta.of(page, PAGE_SIZE, totalCount));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveWithFiles(PostDto post, List<String> savedFileNames) {
        Long userId = postValidator.requireCreate(post);

        List<String> names = postValidator.validateSavedFileNames(savedFileNames);
        postValidator.validateFileCount(names.size());

        List<FileMetadataDto> files = lockPendingFiles(names);

        // 작성자 ID와 게시글 ID를 클라이언트가 지정하지 못하게 한다.
        post.setPostId(null);
        post.setUserId(userId);
        postMapper.save(post);

        attachFiles(post.getPostId(), files);

        if ("NOTICE".equals(post.getPostType())) {
            notificationService.broadcastNoticePost(post.getPostId(), post.getTitle());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateWithFiles(PostDto post, List<Long> deleteFileIds, List<String> savedFileNames) {
        postValidator.requireLogin();
        postValidator.validatePost(post);

        PostDto existing = postMapper.lockById(post.getPostId());
        postValidator.validateUpdate(post, existing);

        List<FileMetadataDto> existingFiles = postMapper.findFilesByPostId(existing.getPostId());
        List<Long> deleteIds = postValidator.validateDeleteFiles(existingFiles, deleteFileIds);
        List<String> names = postValidator.validateSavedFileNames(savedFileNames);
        postValidator.validateFileCount(existingFiles.size() - deleteIds.size() + names.size());

        List<FileMetadataDto> newFiles = lockPendingFiles(names);

        // 게시글 작성자는 변경하지 않는다. 수정 SQL에서도 USER_ID를 갱신하지 않는다.
        post.setUserId(existing.getUserId());
        postMapper.update(post);

        for (Long fileId : deleteIds) {
            int changed = postMapper.deleteFileMeta(fileId, existing.getPostId());
            if (changed != 1) {
                throw new ApiException(HttpStatus.CONFLICT, "FILE_UPDATE_CONFLICT", "첨부파일 상태가 변경되었습니다.");
            }
        }

        attachFiles(existing.getPostId(), newFiles);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long postId) {
        postValidator.requireLogin();

        PostDto post = postMapper.lockById(postId);
        postValidator.requireChange(post);

        postMapper.deleteById(postId);
    }

    private List<PostDto> findPage(String sort, String keyword, int page, String postType) {
        long offsetValue = ((long) page - 1) * PAGE_SIZE;
        if (offsetValue > Integer.MAX_VALUE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "페이지 범위를 초과했습니다.");
        }

        int offset = (int) offsetValue;
        postValidator.validatePage(offset, PAGE_SIZE);

        List<PostDto> posts = postMapper.findPage(sort, keyword, offset, PAGE_SIZE,
                postValidator.normalizePostTypeFilter(postType),
                postValidator.currentUserIdOrNull(),
                postValidator.hasAdminRole());

        loadFileLists(posts);
        return posts;
    }

    private int countAll(String keyword, String postType) {
        return postMapper.countAll(keyword, postValidator.normalizePostTypeFilter(postType),
                postValidator.currentUserIdOrNull(), postValidator.hasAdminRole());
    }

    private void loadFileLists(List<PostDto> posts) {
        for (PostDto post : posts) {
            post.setFiles(postMapper.findFilesByPostId(post.getPostId()));
        }
    }

    private List<FileMetadataDto> lockPendingFiles(List<String> names) {
        List<String> orderedNames = new ArrayList<>(names);
        // 여러 파일의 잠금 순서를 일정하게 유지한다.
        orderedNames.sort(String::compareTo);

        List<FileMetadataDto> files = new ArrayList<>();
        for (String name : orderedNames) {
            FileMetadataDto file = postMapper.lockPendingFile(name);
            // 실제 업로더 또는 관리자인지 Validator에서 검사한다.
            postValidator.requirePendingFile(file);

            Path target = uploadRoot.resolve(file.getStoredFileName()).normalize();
            if (!target.startsWith(uploadRoot) || !Files.isRegularFile(target)) {
                throw new ApiException(HttpStatus.CONFLICT, "FILE_NOT_AVAILABLE", "업로드 파일을 찾을 수 없습니다.");
            }

            files.add(file);
        }
        return files;
    }

    private void attachFiles(Long postId, List<FileMetadataDto> files) {
        for (FileMetadataDto file : files) {
            // 기존 업로드 기록을 게시글에 연결한다. 원본 파일명·크기·실제 업로더 정보는 그대로 유지한다.
            int changed = postMapper.attachPendingFile(file.getFileId(), postId);
            if (changed != 1) {
                throw new ApiException(HttpStatus.CONFLICT, "FILE_ALREADY_USED", "이미 사용되었거나 연결할 수 없는 파일입니다.");
            }
        }
    }
}
