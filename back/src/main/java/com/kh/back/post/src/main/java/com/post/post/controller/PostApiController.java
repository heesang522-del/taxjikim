package com.post.post.controller;

import com.post.common.dto.ApiResponse;
import com.post.post.dto.ChunkDto;
import com.post.post.dto.PostDto;
import com.post.post.service.PostService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/posts")
public class PostApiController {

    // 임시 청크가 저장될 폴더 경로 (환경에 맞게 수정)
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

        return ApiResponse.success(result);
    }

    /**
     * 2. 대용량 파일 청크 업로드 API
     */
    @PostMapping("/upload-chunk")
    public ResponseEntity<Map<String, Object>> uploadChunk(ChunkDto dto) {
        Map<String, Object> response = new HashMap<>();

        try {
            // 🌟 컨트롤러에서 직접 병합하던 로직을 PostService로 위임합니다.
            // PostService 내부에서 마지막 청크일 경우 병합 + 임시폴더 삭제 + file_meta DB 저장을 모두 수행합니다.
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
     * 3. 게시글 신규 등록 API (multipart/form-data 및 폼 전송 모두 허용)
     */
    @PostMapping(consumes = {org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE, org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    public ResponseEntity<?> createPost(
            @RequestParam("title") String title,
            @RequestParam(value = "place", required = false) String place,
            @RequestParam("content") String content,
            @RequestParam(value = "transportCost", defaultValue = "0") Long transportCost,
            @RequestParam(value = "foodCost", defaultValue = "0") Long foodCost,
            @RequestParam(value = "otherCost", defaultValue = "0") Long otherCost,
            @RequestParam(value = "savedFileNamesJson", required = false) String savedFileNamesJson,
            @RequestParam(value = "savedFileNames", required = false) String savedFileNamesAlt
    ) {
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
            postDto.setTitle(title);
            postDto.setPlace(place);
            postDto.setContent(content);
            postDto.setTransportCost(transportCost);
            postDto.setFoodCost(foodCost);
            postDto.setOtherCost(otherCost);
            postDto.setWriter("익명");

            postService.saveWithFiles(postDto, savedFileNames);

            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * 4. 게시글 수정 API (multipart/form-data 및 폼 전송 모두 허용)
     */
    @PostMapping(value = {"/{postId}", "/{postId}/update"}, consumes = {org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE, org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    public ApiResponse<Void> updatePost(
            @PathVariable Long postId,
            @RequestParam("title") String title,
            @RequestParam(value = "place", required = false) String place,
            @RequestParam("content") String content,
            @RequestParam(value = "transportCost", defaultValue = "0") Long transportCost,
            @RequestParam(value = "foodCost", defaultValue = "0") Long foodCost,
            @RequestParam(value = "otherCost", defaultValue = "0") Long otherCost,
            @RequestParam(value = "deleteImageIds", required = false) List<Long> deleteImageIds,
            @RequestParam(value = "savedFileNamesJson", required = false) String savedFileNamesJson,
            @RequestParam(value = "savedFileNames", required = false) String savedFileNamesAlt
    ) throws IOException {

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
        postDto.setTitle(title);
        postDto.setPlace(place);
        postDto.setContent(content);
        postDto.setTransportCost(transportCost);
        postDto.setFoodCost(foodCost);
        postDto.setOtherCost(otherCost);

        postService.updateWithFiles(postDto, deleteImageIds, savedFileNames);
        return ApiResponse.success(null);
    }
    /**
     * 5. 게시글 삭제 API
     */
    @DeleteMapping("/{postId}")
    public ApiResponse<Void> deletePost(@PathVariable Long postId) {
        postService.deleteById(postId);
        return ApiResponse.success(null);
    }
}