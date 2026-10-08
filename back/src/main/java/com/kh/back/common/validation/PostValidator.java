package com.kh.back.common.validation;

import com.kh.back.post.dto.PostImageDto;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@Component
public class PostValidator {


    private static final int MAX_FILE_COUNT = 10;

    /**
     * 게시글 등록 시 파일 검증
     */
    public void validateSave(List<MultipartFile> files) {
        checkFileLimit(0, files);
        checkFileExtensions(files); // [추가] 확장자 검증 실행
    }

    /**
     * 게시글 수정 시 파일 검증
     */
    public void validateUpdate(List<PostImageDto> existingImages, List<Long> deleteImageIds, List<MultipartFile> files) {
        int deleteCount = (deleteImageIds == null) ? 0 : deleteImageIds.size();
        int remainingCount = existingImages.size() - deleteCount;

        checkFileLimit(remainingCount, files);
        checkFileExtensions(files); // [추가] 확장자 검증 실행
    }

    /**
     * 파일 개수 제한 검증 (최대 10개)
     */
    private void checkFileLimit(int baseCount, List<MultipartFile> files) {
        long newFileCount = files == null
                ? 0
                : files.stream().filter(file -> !file.isEmpty()).count();

        if (baseCount + newFileCount > MAX_FILE_COUNT) {
            throw new IllegalArgumentException("파일은 최대 10개까지 등록할 수 있습니다.");
        }
    }

    /**
     * 위험한 파일 확장자(실행 파일 등) 차단 검증
     */
    private void checkFileExtensions(List<MultipartFile> files) {
        if (files == null) return;

        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;

            String originalFilename = file.getOriginalFilename();
            if (originalFilename != null) {
                String lowerName = originalFilename.toLowerCase();
                // .exe, .bat, .sh, .jsp 등 위험한 확장자 차단
                if (lowerName.endsWith(".exe") || lowerName.endsWith(".bat") || lowerName.endsWith(".sh") || lowerName.endsWith(".jsp")) {
                    throw new IllegalArgumentException("실행 파일(.exe, .bat, .jsp 등)은 업로드할 수 없습니다.");
                }
            }
        }
    }
}
