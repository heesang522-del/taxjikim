package com.kh.back.post.controller;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.common.dto.ApiResponse;
import com.kh.back.common.exception.ApiException;
import com.kh.back.post.dto.ChunkDto;
import com.kh.back.post.dto.PostDto;
import com.kh.back.post.service.PostService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/posts")
public class PostApiController {

    // 임시 청크가 저장될 폴더 경로
    private final String TEMP_DIR = "C:/uploads/temp/";
    // 최종 병합된 파일이 저장될 폴더 경로
    private final String UPLOAD_DIR = "C:/uploads/files/";

    private final PostService postService;

    public PostApiController(PostService postService) {
        this.postService = postService;
    }

    /**
     * 1. 비동기 게시글 목록 데이터 조회 API
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> getPostPage(
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page
    ) {
        int size = 9;
        int totalCount = postService.countAll(keyword);
        int totalPages = (int) Math.ceil((double) totalCount / size);
        if (page < 1) page = 1;
        int offset = (page - 1) * size;

        Map<String, Object> result = new HashMap<>();
        result.put("posts", postService.findPage(sort, keyword, offset, size));
        result.put("page", page);
        result.put("totalPages", totalPages);
        result.put("hasNext", page < totalPages);

        return ApiResponse.ok(result);
    }

    /**
     * 2. 대용량 파일 청크 업로드 API
     */
    @PostMapping("/upload-chunk")
    public ResponseEntity<Map<String, Object>> uploadChunk(
            @AuthenticationPrincipal CustomUserDetails principal, ChunkDto dto) {
        requireUserId(principal);
        Map<String, Object> response = new HashMap<>();

        try {
            String savedFileName = postService.processChunkUpload(dto);

            boolean completed = (savedFileName != null);

            Map<String, Object> data = new HashMap<>();
            data.put("completed", completed);
            data.put("savedFileName", savedFileName);

            response.put("success", true);
            response.put("data", data);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 3. 게시글 신규 등록 API
     */
    @PostMapping(consumes = {org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE, org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    public ResponseEntity<?> createPost(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam(value = "boardType", defaultValue = "INFO") String boardType,
            @RequestParam(value = "isPublic", defaultValue = "true") boolean isPublic,
            @RequestParam(value = "savedFileNamesJson", required = false) String savedFileNamesJson,
            @RequestParam(value = "savedFileNames", required = false) String savedFileNamesAlt
    ) {
        // 인증된 작성자가 없으면 저장하지 않고 공통 예외 처리기로 401 응답을 전달한다.
        if (principal == null || principal.getUserId() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
        }

        try {
            String jsonStr = (savedFileNamesJson != null && !savedFileNamesJson.isBlank()) ? savedFileNamesJson : savedFileNamesAlt;

            List<String> savedFileNames = new java.util.ArrayList<>();
            if (jsonStr != null && !jsonStr.isBlank() && !jsonStr.equals("[]")) {
                String cleaned = jsonStr.trim();
                if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
                    cleaned = cleaned.substring(1, cleaned.length() - 1);
                }
                if (!cleaned.isBlank()) {
                    String[] parts = cleaned.split(",");
                    for (String part : parts) {
                        String fileName = part.trim().replaceAll("^\"|\"$", "");
                        if (!fileName.isEmpty()) {
                            savedFileNames.add(fileName);
                        }
                    }
                }
            }

            PostDto postDto = new PostDto();
            // 작성자 ID는 클라이언트 입력 대신 서버가 확인한 로그인 정보에서 가져온다.
            postDto.setUserId(principal.getUserId());
            postDto.setTitle(title);
            postDto.setContent(content);
            postDto.setBoardType(boardType);
            postDto.setPublic(isPublic);
            postDto.setWriter(principal.getUserDto().getNickname());

            postService.saveWithFiles(postDto, savedFileNames);

            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * 4. 게시글 수정 API
     */
    @PostMapping(value = {"/{postId}", "/{postId}/update"}, consumes = {org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE, org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    public ApiResponse<Void> updatePost(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long postId,
            @RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam(value = "boardType", defaultValue = "INFO") String boardType,
            @RequestParam(value = "isPublic", defaultValue = "true") boolean isPublic,
            @RequestParam(value = "deleteFileIds", required = false) List<Long> deleteFileIds,
            @RequestParam(value = "savedFileNamesJson", required = false) String savedFileNamesJson,
            @RequestParam(value = "savedFileNames", required = false) String savedFileNamesAlt
    ) throws IOException {

        Long userId = requireUserId(principal);
        postService.requireAuthor(postId, userId);

        String jsonStr = (savedFileNamesJson != null && !savedFileNamesJson.isBlank()) ? savedFileNamesJson : savedFileNamesAlt;

        List<String> savedFileNames = new java.util.ArrayList<>();
        if (jsonStr != null && !jsonStr.isBlank() && !jsonStr.equals("[]")) {
            String cleaned = jsonStr.trim();
            if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
                cleaned = cleaned.substring(1, cleaned.length() - 1);
            }
            if (!cleaned.isBlank()) {
                String[] parts = cleaned.split(",");
                for (String part : parts) {
                    String fileName = part.trim().replaceAll("^\"|\"$", "");
                    if (!fileName.isEmpty()) {
                        savedFileNames.add(fileName);
                    }
                }
            }
        }

        PostDto postDto = new PostDto();
        postDto.setPostId(postId);
        postDto.setUserId(userId);
        postDto.setTitle(title);
        postDto.setContent(content);
        postDto.setBoardType(boardType);
        postDto.setPublic(isPublic);

        postService.updateWithFiles(postDto, deleteFileIds, savedFileNames);
        return ApiResponse.ok(null);
    }

    /**
     * 5. 게시글 삭제 API
     */
    @DeleteMapping("/{postId}")
    public ApiResponse<Void> deletePost(
            @AuthenticationPrincipal CustomUserDetails principal, @PathVariable Long postId) {
        postService.requireAuthor(postId, requireUserId(principal));
        postService.deleteById(postId);
        return ApiResponse.ok(null);
    }

    private Long requireUserId(CustomUserDetails principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
        }
        return principal.getUserId();
    }
}

