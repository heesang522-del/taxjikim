package com.kh.back.post.controller;

import com.kh.back.common.dto.ApiResponse;
import com.kh.back.post.dto.ChunkDto;
import com.kh.back.post.dto.PostDto;
import com.kh.back.post.service.PostService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/posts")
public class PostApiController {

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

        return ApiResponse.ok(result); // success -> ok 로 변경
    }

    /**
     * 2. 대용량 파일 청크 업로드 API
     */
    @PostMapping("/upload-chunk")
    public ResponseEntity<Map<String, Object>> uploadChunk(ChunkDto dto) {
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
     * 4. 게시글 수정 API
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
        return ApiResponse.ok(null); // success -> ok 로 변경
    }

    /**
     * 5. 게시글 삭제 API
     */
    @DeleteMapping("/{postId}")
    public ApiResponse<Void> deletePost(@PathVariable Long postId) {
        postService.deleteById(postId);
        return ApiResponse.ok(null); // success -> ok 로 변경
    }
}