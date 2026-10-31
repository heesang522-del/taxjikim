
package com.kh.back.post.service;

import com.kh.back.auth.security.CustomUserDetails;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final PostMapper postMapper;
    private final PostValidator postValidator;
    private final NotificationService notificationService;
    private final PlatformTransactionManager transactionManager;

    private final Path tempRoot = Path.of(
            System.getProperty("user.dir"),
            "uploads",
            "temp"
    ).toAbsolutePath().normalize();

    private final Path uploadRoot = Path.of(
            System.getProperty("user.dir"),
            "uploads",
            "files"
    ).toAbsolutePath().normalize();

    // 목록 응답 한 페이지에 보여줄 글 수. 클라이언트가 바꿀 수 없는 고정값이라 상수로 둔다.
    private static final int PAGE_SIZE = 9;

    // 동일 업로드 ID에 대한 동시 처리 방지
    private final Semaphore[] uploadLocks = IntStream
            .range(0, 64)
            .mapToObj(index -> new Semaphore(1, true))
            .toArray(Semaphore[]::new);

    // =====================================================
    // 파일 청크 업로드 - 보완된 부분
    // =====================================================

    @Override
    public String processChunkUpload(
            CustomUserDetails principal,
            ChunkDto dto
    ) throws IOException {

        Long authenticatedUserId = postValidator.requireLogin();

        if (principal == null
                || principal.getUserId() == null
                || !principal.getUserId().equals(authenticatedUserId)) {

            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED",
                    "로그인 정보를 확인할 수 없습니다."
            );
        }

        // 요청 형식과 청크 크기 등 업로드 검증
        postValidator.validateChunkRequest(dto);

        String uploadKey =
                principal.getUserId()
                        + "_" + dto.getUploadId();

        Semaphore uploadLock = uploadLocks[
                Math.floorMod(
                        uploadKey.hashCode(),
                        uploadLocks.length
                )
                ];

        try {
            uploadLock.acquire();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "UPLOAD_INTERRUPTED",
                    "업로드 대기가 중단되었습니다."
            );
        }

        AtomicReference<Path> createdFile =
                new AtomicReference<>();

        try {
            TransactionTemplate transaction =
                    new TransactionTemplate(transactionManager);

            transaction.setPropagationBehavior(
                    TransactionDefinition.PROPAGATION_REQUIRES_NEW
            );

            String savedFileName;

            try {
                savedFileName = transaction.execute(status -> {
                    try {
                        return storeChunk(
                                dto,
                                createdFile
                        );
                    } catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                });

            } catch (RuntimeException exception) {

                if (createdFile.get() != null) {
                    try {
                        Files.deleteIfExists(
                                createdFile.get()
                        );
                    } catch (IOException cleanupException) {
                        exception.addSuppressed(
                                cleanupException
                        );

                        log.error(
                                "업로드 실패 파일 정리 오류",
                                cleanupException
                        );
                    }
                }

                if (exception instanceof UncheckedIOException io) {
                    throw io.getCause();
                }

                throw exception;
            }

            if (savedFileName != null) {
                cleanupChunks(dto);
            }

            return savedFileName;

        } finally {
            uploadLock.release();
        }
    }

    // =====================================================
    // 청크 저장 및 파일 병합 - 보완된 부분
    // =====================================================

    private String storeChunk(
            ChunkDto dto,
            AtomicReference<Path> createdFile
    ) throws IOException {

        PostDto targetPost = null;

        if (dto.getPostId() != null) {
            targetPost = postMapper.lockById(
                    dto.getPostId()
            );
        }

        // 기존 인증 및 게시글 업로드 권한 검증 유지
        Long userId = postValidator.requireUpload(
                dto,
                targetPost
        );

        Path directory = tempRoot.resolve(
                userId + "_" + dto.getUploadId()
        ).normalize();

        postValidator.validateUploadPath(
                tempRoot,
                directory
        );

        Files.createDirectories(directory);
        Files.createDirectories(uploadRoot);

        if (!Files.isDirectory(
                directory,
                LinkOption.NOFOLLOW_LINKS
        ) || !Files.isDirectory(
                uploadRoot,
                LinkOption.NOFOLLOW_LINKS
        )) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "INVALID_UPLOAD_PATH",
                    "파일 저장 경로가 올바르지 않습니다."
            );
        }

        Path metadataPath =
                directory.resolve("upload.txt");

        String requestedMetadata =
                dto.getTotalChunks()
                        + "\n"
                        + dto.getOriginalName()
                        + "\n"
                        + String.valueOf(dto.getPostId());

        // Files.readString/writeString 기본 UTF-8 사용
        if (Files.exists(
                metadataPath,
                LinkOption.NOFOLLOW_LINKS
        )) {
            if (!Files.isRegularFile(
                    metadataPath,
                    LinkOption.NOFOLLOW_LINKS
            )) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "INVALID_UPLOAD_METADATA",
                        "업로드 정보 파일이 올바르지 않습니다."
                );
            }

            postValidator.validateUploadMetadata(
                    Files.readString(metadataPath),
                    requestedMetadata
            );

        } else {
            Files.writeString(
                    metadataPath,
                    requestedMetadata,
                    StandardOpenOption.CREATE_NEW
            );
        }

        Path chunkPath = directory.resolve(
                "chunk_" + dto.getChunkIndex()
        );

        // 청크를 바로 덮어쓰지 않고 임시 파일에 저장
        Path stagingPath = Files.createTempFile(
                directory,
                "incoming_",
                ".part"
        );

        try {
            long writtenBytes = 0;

            try (
                    InputStream input =
                            dto.getFile().getInputStream();

                    OutputStream output = Files.newOutputStream(
                            stagingPath,
                            StandardOpenOption.TRUNCATE_EXISTING
                    )
            ) {
                byte[] buffer = new byte[8192];
                int read;

                while ((read = input.read(buffer)) != -1) {
                    writtenBytes += read;

                    postValidator.validateIncomingChunkSize(
                            writtenBytes
                    );

                    output.write(buffer, 0, read);
                }
            }

            postValidator.validateChunkBytes(
                    writtenBytes,
                    dto.getFile().getSize()
            );

            if (Files.exists(
                    chunkPath,
                    LinkOption.NOFOLLOW_LINKS
            )) {
                if (!Files.isRegularFile(
                        chunkPath,
                        LinkOption.NOFOLLOW_LINKS
                ) || Files.mismatch(
                        chunkPath,
                        stagingPath
                ) != -1L) {
                    throw new ApiException(
                            HttpStatus.CONFLICT,
                            "CHUNK_CONFLICT",
                            "기존 파일 조각과 내용이 다릅니다."
                    );
                }

            } else {
                Files.move(
                        stagingPath,
                        chunkPath
                );
            }

        } finally {
            Files.deleteIfExists(stagingPath);
        }

        long totalSize = 0;

        for (int index = 0;
             index < dto.getTotalChunks();
             index++) {

            Path part = directory.resolve(
                    "chunk_" + index
            );

            if (!Files.isRegularFile(
                    part,
                    LinkOption.NOFOLLOW_LINKS
            )) {
                return null;
            }

            long partSize = Files.size(part);

            postValidator.validateTotalFileSize(
                    totalSize,
                    partSize
            );

            totalSize += partSize;
        }

        String originalName = dto.getOriginalName();

        String extension =
                postValidator.extractExtension(
                        originalName
                );

        String savedName =
                UUID.randomUUID() + extension;

        Path target = uploadRoot.resolve(
                savedName
        ).normalize();

        postValidator.validateUploadPath(
                uploadRoot,
                target
        );

        try (
                OutputStream output =
                        new BufferedOutputStream(
                                Files.newOutputStream(
                                        target,
                                        StandardOpenOption.CREATE_NEW
                                )
                        )
        ) {
            createdFile.set(target);

            for (int index = 0;
                 index < dto.getTotalChunks();
                 index++) {

                Files.copy(
                        directory.resolve("chunk_" + index),
                        output
                );
            }
        }

        long mergedSize = Files.size(target);

        postValidator.validateMergedFileSize(
                mergedSize,
                totalSize
        );

        postValidator.validateMedia(
                target,
                extension
        );

        FileMetadataDto metadata =
                FileMetadataDto.builder()
                        .userId(userId)
                        .originalFileName(originalName)
                        .storedFileName(savedName)
                        .filePath(target.toString())
                        .fileExtension(extension.substring(1))
                        .fileSize(mergedSize)
                        .build();

        // 게시글에 연결되기 전에는 POST_PENDING으로 저장한다.
        // 최종 글 저장 시 소유권을 검사한 뒤 POST로 전환한다.
        postMapper.savePendingFile(metadata);

        return savedName;
    }

    // =====================================================
    // 업로드 성공 후 임시 파일 정리
    // =====================================================

    private void cleanupChunks(ChunkDto dto) {

        Long userId =
                postValidator.currentUserIdOrNull();

        Path directory = tempRoot.resolve(
                userId + "_" + dto.getUploadId()
        ).normalize();

        try {
            for (int index = 0;
                 index < dto.getTotalChunks();
                 index++) {

                Files.deleteIfExists(
                        directory.resolve(
                                "chunk_" + index
                        )
                );
            }

            Files.deleteIfExists(
                    directory.resolve("upload.txt")
            );

            Files.deleteIfExists(directory);

        } catch (IOException exception) {
            log.warn(
                    "업로드 완료 후 임시 파일 정리 실패: {}",
                    directory,
                    exception
            );
        }
    }

    // =====================================================
    // 아래부터 기존 Q&A / 알림 / 게시글 코드 원본 유지
    // =====================================================

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
