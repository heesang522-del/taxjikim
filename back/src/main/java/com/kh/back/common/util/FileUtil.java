package com.kh.back.common.util;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Component
public class FileUtil {

    private static final String UPLOAD_BASE_DIR = File.separator + "uploads" + File.separator;

    /**
     * 범용 파일 저장 (트랜잭션 롤백/커밋 자동 동기화)
     * @param multipartFile 업로드 파일
     * @param subDir 저장 하위 폴더 (예: "profile", "board")
     * @param oldFilePath 교체 시 삭제할 기존 파일 경로 (신규 저장이면 null)
     */
    public String saveFile(MultipartFile multipartFile, String subDir, String oldFilePath) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            return null;
        }

        String projectPath = System.getProperty("user.dir");
        String uploadPath = projectPath + UPLOAD_BASE_DIR + subDir + File.separator;

        File uploadDir = new File(uploadPath);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        String savedFileName = UUID.randomUUID().toString() + "_" + multipartFile.getOriginalFilename();
        File dest = new File(uploadPath, savedFileName);

        try {
            multipartFile.transferTo(dest);
            String newFilePath = "/uploads/" + subDir + "/" + savedFileName;

            // 트랜잭션 동기화 (커밋 시 구 파일 삭제, 롤백 시 새 파일 삭제)
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_COMMITTED && oldFilePath != null) {
                            deleteFile(oldFilePath);
                        } else if (status == STATUS_ROLLED_BACK) {
                            deleteFile(newFilePath);
                        }
                    }
                });
            }

            return newFilePath;
        } catch (IOException e) {
            throw new RuntimeException("파일 저장 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 실물 파일 삭제
     */
    public void deleteFile(String dbFilePath) {
        if (dbFilePath != null && !dbFilePath.isEmpty()) {
            String projectPath = System.getProperty("user.dir");
            File fileToDelete = new File(projectPath + dbFilePath.replace("/", File.separator));
            if (fileToDelete.exists()) {
                fileToDelete.delete();
            }
        }
    }
}