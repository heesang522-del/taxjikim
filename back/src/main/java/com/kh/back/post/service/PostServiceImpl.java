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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    private static final int PAGE_SIZE = 9;
    private static final int UPLOAD_LOCK_COUNT = 64;

    /*
     * 같은 사용자·업로드 ID에는 같은 잠금을 배정한다.
     * 서로 다른 업로드도 같은 잠금에 배정될 수 있다.
     */
    private final Semaphore[] uploadLocks = IntStream
            .range(0, UPLOAD_LOCK_COUNT)
            .mapToObj(index -> new Semaphore(1, true))
            .toArray(Semaphore[]::new);

    /**
     * 청크 저장 및 최종 파일 병합을 처리한다.
     * 명시적 트랜잭션의 커밋과 임시 파일 정리 후 잠금을 해제한다.
     */
    @Override
    public String processChunkUpload(
            CustomUserDetails principal,
            ChunkDto dto
    ) throws IOException {
        if (principal == null
                || principal.getUserId() == null) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "UNAUTHORIZED",
                    "로그인이 필요합니다."
            );
        }

        Long userId = principal.getUserId();

        if (dto == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "업로드 입력값이 없습니다."
            );
        }

        String uploadKey =
                userId + "_" + dto.getUploadId();

        Semaphore uploadLock = uploadLocks[
                Math.floorMod(
                        uploadKey.hashCode(),
                        uploadLocks.length
                )
                ];

        acquireUploadLock(uploadLock);

        AtomicReference<Path> createdFile =
                new AtomicReference<>();

        try {
            TransactionTemplate transaction =
                    new TransactionTemplate(transactionManager);

            /*
             * execute가 반환되기 전에 커밋까지 완료한다.
             * 기존 업로드 트랜잭션 구조를 유지한다.
             */
            transaction.setPropagationBehavior(
                    TransactionDefinition.PROPAGATION_REQUIRES_NEW
            );

            String savedFileName;

            try {
                savedFileName = transaction.execute(status -> {
                    try {
                        return storeChunk(
                                dto,
                                userId,
                                createdFile
                        );

                    } catch (IOException exception) {
                        throw new UncheckedIOException(
                                exception
                        );
                    }
                });

            } catch (RuntimeException exception) {
                // 이번 요청이 생성한 최종 파일만 정리한다.
                deleteCreatedFile(
                        createdFile.get(),
                        exception
                );

                if (exception instanceof UncheckedIOException io) {
                    throw io.getCause();
                }

                throw exception;
            }

            // DB 커밋이 성공한 뒤에만 임시 청크를 정리한다.
            if (savedFileName != null) {
                cleanupChunks(dto, userId);
            }

            return savedFileName;

        } finally {
            uploadLock.release();
        }
    }

    private void acquireUploadLock(
            Semaphore uploadLock
    ) {
        try {
            uploadLock.acquire();

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "UPLOAD_INTERRUPTED",
                    "업로드 대기가 중단되었습니다. 다시 시도해주세요."
            );
        }
    }

    private String storeChunk(
            ChunkDto dto,
            Long userId,
            AtomicReference<Path> createdFile
    ) throws IOException {
        PostDto targetPost = null;

        if (dto.getPostId() != null) {
            targetPost = postMapper.lockById(
                    dto.getPostId()
            );
        }

        /*
         * 기존 검증을 유지한다.
         * DTO, 게시글 존재 여부, 업로드 권한 등을 확인한다.
         */
        Long validatedUserId = postValidator.requireUpload(
                dto,
                targetPost
        );

        /*
         * 컨트롤러에서 전달한 인증 사용자와
         * 기존 검증기가 확인한 사용자가 같은지 확인한다.
         */
        if (!userId.equals(validatedUserId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "UPLOAD_USER_MISMATCH",
                    "업로드 사용자 정보가 일치하지 않습니다."
            );
        }

        Path directory = tempRoot.resolve(
                userId + "_" + dto.getUploadId()
        ).normalize();

        if (!directory.startsWith(tempRoot)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_UPLOAD_PATH",
                    "업로드 경로가 올바르지 않습니다."
            );
        }

        Files.createDirectories(directory);
        Files.createDirectories(uploadRoot);

        Path metadataPath =
                directory.resolve("upload.txt");

        String requestedMetadata =
                dto.getTotalChunks()
                        + "\n"
                        + dto.getOriginalName()
                        + "\n"
                        + String.valueOf(dto.getPostId());

        if (Files.exists(metadataPath)) {
            postValidator.validateUploadMetadata(
                    Files.readString(
                            metadataPath,
                            StandardCharsets.UTF_8
                    ),
                    requestedMetadata
            );

        } else {
            Files.writeString(
                    metadataPath,
                    requestedMetadata,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW
            );
        }

        Path chunkPath = directory.resolve(
                "chunk_" + dto.getChunkIndex()
        );

        try (
                InputStream input =
                        dto.getFile().getInputStream();

                OutputStream output = Files.newOutputStream(
                        chunkPath,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                )
        ) {
            input.transferTo(output);
        }

        for (int index = 0;
             index < dto.getTotalChunks();
             index++) {
            if (!Files.isRegularFile(
                    directory.resolve("chunk_" + index)
            )) {
                return null;
            }
        }

        String originalName = dto.getOriginalName();

        String extension = originalName.substring(
                originalName.lastIndexOf('.')
        ).toLowerCase(Locale.ROOT);

        String savedName =
                UUID.randomUUID() + extension;

        Path target = uploadRoot.resolve(
                savedName
        ).normalize();

        if (!target.startsWith(uploadRoot)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_UPLOAD_PATH",
                    "최종 파일 경로가 올바르지 않습니다."
            );
        }

        try (
                OutputStream output =
                        new BufferedOutputStream(
                                Files.newOutputStream(
                                        target,
                                        StandardOpenOption.CREATE_NEW
                                )
                        )
        ) {
            // 생성에 성공한 파일만 실패 시 정리 대상으로 기록한다.
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

        postValidator.validateMedia(
                target,
                extension
        );

        FileMetadataDto metadata = new FileMetadataDto();

        metadata.setUserId(userId);
        metadata.setOriginalFileName(originalName);
        metadata.setStoredFileName(savedName);
        metadata.setFilePath(target.toString());
        metadata.setFileExtension(extension.substring(1));
        metadata.setFileSize(Files.size(target));

        // 게시글에 연결하기 전의 임시 첨부파일 정보를 저장한다.
        postMapper.savePendingFile(metadata);

        return savedName;
    }

    private void deleteCreatedFile(
            Path target,
            RuntimeException originalException
    ) {
        if (target == null) {
            return;
        }

        try {
            Files.deleteIfExists(target);

        } catch (IOException cleanupException) {
            originalException.addSuppressed(
                    cleanupException
            );

            log.error(
                    "실패한 업로드 파일 정리 실패: {}",
                    target,
                    cleanupException
            );
        }
    }

    private void cleanupChunks(
            ChunkDto dto,
            Long userId
    ) {
        Path directory = tempRoot.resolve(
                userId + "_" + dto.getUploadId()
        ).normalize();

        try {
            for (int index = 0;
                 index < dto.getTotalChunks();
                 index++) {
                Files.deleteIfExists(
                        directory.resolve("chunk_" + index)
                );
            }

            Files.deleteIfExists(
                    directory.resolve("upload.txt")
            );

            Files.deleteIfExists(directory);

        } catch (IOException exception) {
            /*
             * DB 저장은 이미 성공했다.
             * 임시 파일 정리 실패로 업로드 실패를 응답하지 않는다.
             */
            log.warn(
                    "업로드 완료 후 임시 청크 정리 실패: {}",
                    directory,
                    exception
            );
        }
    }

    @Override
    public PostDto findById(Long postId) {
        PostDto post = postMapper.findById(postId);

        if (post == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "POST_NOT_FOUND",
                    "게시글을 찾을 수 없습니다."
            );
        }

        // 기존 비공개 Q&A 접근 제한 유지
        boolean isPrivateQna =
                "QNA".equals(post.getPostType())
                        && !post.isPublic();

        if (isPrivateQna) {
            Long currentUserId =
                    postValidator.currentUserIdOrNull();

            boolean isOwner =
                    currentUserId != null
                            && currentUserId.equals(
                            post.getUserId()
                    );

            if (!isOwner && !postValidator.hasAdminRole()) {
                throw new ApiException(
                        HttpStatus.NOT_FOUND,
                        "POST_NOT_FOUND",
                        "게시글을 찾을 수 없습니다."
                );
            }
        }

        post.setFiles(
                postMapper.findFilesByPostId(postId)
        );

        return post;
    }

    @Override
    public ApiResponse<List<PostDto>> getPostPage(
            String sort,
            String keyword,
            int page,
            String postType
    ) {
        page = Math.max(page, 1);

        int totalCount = countAll(
                keyword,
                postType
        );

        List<PostDto> posts = findPage(
                sort,
                keyword,
                page,
                postType
        );

        return ApiResponse.ok(
                posts,
                PageMeta.of(
                        page,
                        PAGE_SIZE,
                        totalCount
                )
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveWithFiles(
            PostDto post,
            List<String> savedFileNames
    ) {
        Long userId =
                postValidator.requireCreate(post);

        List<String> names =
                postValidator.validateSavedFileNames(
                        savedFileNames
                );

        postValidator.validateFileCount(
                names.size()
        );

        List<FileMetadataDto> files =
                lockPendingFiles(names);

        post.setPostId(null);
        post.setUserId(userId);

        postMapper.save(post);

        attachFiles(
                post.getPostId(),
                files
        );

        // 기존 공지 알림 기능 유지
        if ("NOTICE".equals(post.getPostType())) {
            notificationService.broadcastNoticePost(
                    post.getPostId(),
                    post.getTitle()
            );
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateWithFiles(
            PostDto post,
            List<Long> deleteFileIds,
            List<String> savedFileNames
    ) {
        postValidator.requireLogin();
        postValidator.validatePost(post);

        PostDto existing = postMapper.lockById(
                post.getPostId()
        );

        postValidator.validateUpdate(
                post,
                existing
        );

        List<FileMetadataDto> existingFiles =
                postMapper.findFilesByPostId(
                        existing.getPostId()
                );

        List<Long> deleteIds =
                postValidator.validateDeleteFiles(
                        existingFiles,
                        deleteFileIds
                );

        List<String> names =
                postValidator.validateSavedFileNames(
                        savedFileNames
                );

        postValidator.validateFileCount(
                existingFiles.size()
                        - deleteIds.size()
                        + names.size()
        );

        List<FileMetadataDto> newFiles =
                lockPendingFiles(names);

        // 수정 요청으로 작성자가 바뀌지 않게 한다.
        post.setUserId(existing.getUserId());
        postMapper.update(post);

        for (Long fileId : deleteIds) {
            int changed = postMapper.deleteFileMeta(
                    fileId,
                    existing.getPostId()
            );

            if (changed != 1) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "FILE_UPDATE_CONFLICT",
                        "첨부파일 상태가 변경되었습니다."
                );
            }
        }

        attachFiles(
                existing.getPostId(),
                newFiles
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long postId) {
        postValidator.requireLogin();

        PostDto post =
                postMapper.lockById(postId);

        postValidator.requireChange(post);

        postMapper.deleteById(postId);
    }

    private List<PostDto> findPage(
            String sort,
            String keyword,
            int page,
            String postType
    ) {
        long offsetValue =
                ((long) page - 1) * PAGE_SIZE;

        if (offsetValue > Integer.MAX_VALUE) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "페이지 범위를 초과했습니다."
            );
        }

        int offset = (int) offsetValue;

        postValidator.validatePage(
                offset,
                PAGE_SIZE
        );

        // 기존 목록 필터와 Q&A 조회 권한 전달 유지
        List<PostDto> posts = postMapper.findPage(
                sort,
                keyword,
                offset,
                PAGE_SIZE,
                postValidator.normalizePostTypeFilter(
                        postType
                ),
                postValidator.currentUserIdOrNull(),
                postValidator.hasAdminRole()
        );

        loadFileLists(posts);

        return posts;
    }

    private int countAll(
            String keyword,
            String postType
    ) {
        return postMapper.countAll(
                keyword,
                postValidator.normalizePostTypeFilter(
                        postType
                ),
                postValidator.currentUserIdOrNull(),
                postValidator.hasAdminRole()
        );
    }

    private void loadFileLists(
            List<PostDto> posts
    ) {
        for (PostDto post : posts) {
            post.setFiles(
                    postMapper.findFilesByPostId(
                            post.getPostId()
                    )
            );
        }
    }

    private List<FileMetadataDto> lockPendingFiles(
            List<String> names
    ) {
        List<String> orderedNames =
                new ArrayList<>(names);

        // 동시에 여러 파일을 연결할 때 잠금 순서를 일정하게 유지한다.
        orderedNames.sort(String::compareTo);

        List<FileMetadataDto> files =
                new ArrayList<>();

        for (String name : orderedNames) {
            FileMetadataDto file =
                    postMapper.lockPendingFile(name);

            postValidator.requirePendingFile(file);

            Path target = uploadRoot.resolve(
                    file.getStoredFileName()
            ).normalize();

            if (!target.startsWith(uploadRoot)
                    || !Files.isRegularFile(target)) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "FILE_NOT_AVAILABLE",
                        "업로드 파일을 찾을 수 없습니다."
                );
            }

            files.add(file);
        }

        return files;
    }

    private void attachFiles(
            Long postId,
            List<FileMetadataDto> files
    ) {
        for (FileMetadataDto file : files) {
            int changed = postMapper.attachPendingFile(
                    file.getFileId(),
                    postId
            );

            if (changed != 1) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "FILE_ALREADY_USED",
                        "이미 사용되었거나 연결할 수 없는 파일입니다."
                );
            }
        }
    }
}