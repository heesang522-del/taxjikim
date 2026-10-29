package com.kh.back.post.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.kh.back.common.dto.ApiResponse;
import com.kh.back.common.exception.ApiException;

import com.kh.back.post.dto.ChunkDto;
import com.kh.back.post.dto.PostDto;
import com.kh.back.post.service.PostService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/posts/community")
@RequiredArgsConstructor
public class PostApiController {

    private final PostService postService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping
    public ApiResponse<List<PostDto>> getPostPage(@RequestParam(defaultValue = "latest") String sort,

                                                  @RequestParam(defaultValue = "") String keyword,

                                                  @RequestParam(defaultValue = "1") int page,

                                                  @RequestParam(required = false) String postType) {

        return postService.getPostPage(sort, keyword, page, postType);
    }

    @GetMapping("/{postId}")
    public ApiResponse<PostDto> getPost(@PathVariable Long postId) {

        return ApiResponse.ok(postService.findById(postId));
    }


    @PostMapping(value = "/upload-chunk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> uploadChunk(@ModelAttribute ChunkDto dto) throws IOException {

        String savedFileName = postService.processChunkUpload(dto);

        Map<String, Object> result = new HashMap<>();

        result.put("completed", savedFileName != null);

        result.put("savedFileName", savedFileName);

        return ApiResponse.ok(result);
    }

    @PostMapping(consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    public ApiResponse<Void> createPost(@RequestParam("title") String title, @RequestParam("content") String content, @RequestParam(value = "postType", defaultValue = "INFO") String postType,

                                        @RequestParam(value = "isPublic", defaultValue = "true") boolean isPublic,

                                        @RequestParam(value = "savedFileNamesJson", required = false) String savedFileNamesJson) {

        PostDto post = makePost(title, content, postType, isPublic);

        postService.saveWithFiles(post, parseFileNames(savedFileNamesJson));

        return ApiResponse.ok(null);
    }

    @PostMapping(value = {"/{postId}", "/{postId}/update"}, consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    public ApiResponse<Void> updatePost(@PathVariable Long postId,

                                        @RequestParam("title") String title,

                                        @RequestParam("content") String content,

                                        @RequestParam(value = "postType", defaultValue = "INFO") String postType,

                                        @RequestParam(value = "isPublic", defaultValue = "true") boolean isPublic,

                                        @RequestParam(value = "deleteFileIds", required = false) List<Long> deleteFileIds,

                                        @RequestParam(value = "savedFileNamesJson", required = false) String savedFileNamesJson) {

        PostDto post = makePost(title, content, postType, isPublic);

        post.setPostId(postId);

        postService.updateWithFiles(post, deleteFileIds, parseFileNames(savedFileNamesJson));

        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{postId}")
    public ApiResponse<Void> deletePost(@PathVariable Long postId) {

        postService.deleteById(postId);

        return ApiResponse.ok(null);
    }

    private PostDto makePost(String title, String content, String postType, boolean isPublic) {

        PostDto post = new PostDto();

        post.setTitle(title);
        post.setContent(content);
        post.setPostType(postType);
        post.setPublic(isPublic);

        // 사용자 ID는 서비스에서 인증 정보로 지정한다.
        return post;
    }

    private List<String> parseFileNames(String savedFileNamesJson) {

        if (savedFileNamesJson == null || savedFileNamesJson.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(savedFileNamesJson);

            if (root == null || !root.isArray()) {
                throw invalid("첨부파일 목록은 배열이어야 합니다.");
            }

            List<String> names = new ArrayList<>();

            for (JsonNode node : root) {

                if (!node.isTextual()) {
                    throw invalid("첨부파일명은 문자열이어야 합니다.");
                }

                names.add(node.textValue());
            }

            return names;

        } catch (JsonProcessingException e) {
            throw invalid("첨부파일 목록의 형식을 확인해주세요.");
        }
    }

    private ApiException invalid(String message) {

        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
}