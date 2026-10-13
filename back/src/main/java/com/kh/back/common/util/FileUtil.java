package com.kh.back.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** 회원 프로필과 게시글 첨부파일의 로컬 저장·정리를 담당한다. */
@Slf4j
@Component
public class FileUtil {
    private static final Path UPLOAD_BASE_DIR = Path.of("uploads").toAbsolutePath().normalize();

    public static Path getUploadDirectory(String subDir) {
        Path directory = UPLOAD_BASE_DIR.resolve(subDir).normalize();
        if (!directory.startsWith(UPLOAD_BASE_DIR)) {
            throw new IllegalArgumentException("업로드 하위 폴더가 올바르지 않습니다.");
        }
        return directory;
    }

    public SavedFile save(MultipartFile file, String subDir, String webPrefix) throws IOException {
        return save(file, subDir, webPrefix, null);
    }

    public SavedFile save(MultipartFile file, String subDir, String webPrefix,
                          String oldWebPath) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        Path directory = getUploadDirectory(subDir);
        Path oldTarget = oldWebPath == null || oldWebPath.isBlank()
                ? null : resolveStoredPath(directory, oldWebPath);
        String originalName = file.getOriginalFilename();
        if (originalName == null) {
            originalName = "";
        }
        // 원본 이름은 메타데이터로 보관하고, 디스크에는 UUID와 확장자로 저장한다.
        String baseName = originalName.replace('\\', '/');
        baseName = baseName.substring(baseName.lastIndexOf('/') + 1);
        int dotIndex = baseName.lastIndexOf('.');
        String extension = dotIndex > 0 ? baseName.substring(dotIndex) : "";
        String saveName = UUID.randomUUID() + extension;
        Path target = directory.resolve(saveName);
        Files.createDirectories(directory);
        try {
            file.transferTo(target);
        } catch (IOException exception) {
            // 저장 도중 실패하면 일부만 기록된 파일도 정리한다.
            try {
                Files.deleteIfExists(target);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }

        // DB 성공 시 기존 파일을, DB 롤백 시 새 파일을 삭제한다.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_COMMITTED) {
                        removeFile(oldTarget);
                    } else if (status == STATUS_ROLLED_BACK) {
                        removeFile(target);
                    }
                }
            });
        } else {
            // 트랜잭션이 없으면 파일 저장 성공 직후 교체 전 파일을 정리한다.
            removeFile(oldTarget);
        }
        return new SavedFile(originalName, saveName,
                webPrefix.replaceAll("/+$", "") + "/" + saveName);
    }

    public void delete(String webPath, String subDir) throws IOException {
        if (webPath == null || webPath.isBlank()) {
            return;
        }
        Path directory = getUploadDirectory(subDir);
        Path target = resolveStoredPath(directory, webPath);
        // 프로필 삭제도 커밋 후 수행하여 DB 롤백 시 기존 파일을 유지한다.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_COMMITTED) {
                        removeFile(target);
                    }
                }
            });
        } else {
            Files.deleteIfExists(target);
        }
    }

    private Path resolveStoredPath(Path directory, String webPath) {
        String fileName = webPath.substring(webPath.lastIndexOf('/') + 1);
        Path target = directory.resolve(fileName).normalize();
        if (fileName.isBlank() || !directory.equals(target.getParent())) {
            throw new IllegalArgumentException("파일 경로가 올바르지 않습니다.");
        }
        return target;
    }

    private void removeFile(Path target) {
        if (target == null) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            // 커밋 후 삭제 실패는 DB를 되돌릴 수 없으므로 정리할 경로를 로그에 남긴다.
            log.warn("파일 정리 실패: {}", target, exception);
        }
    }
}
