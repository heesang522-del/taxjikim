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
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostApiController {

    private final PostService postService;

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    @GetMapping
    public ApiResponse<Map<String, Object>> getPostPage(
            @RequestParam(defaultValue = "latest")
            String sort,

            @RequestParam(defaultValue = "")
            String keyword,

            @RequestParam(defaultValue = "1")
            int page
    ) {

        int size = 9;

        page = Math.max(page, 1);

        long offsetValue = ((long) page - 1) * size;

        if (offsetValue > Integer.MAX_VALUE) {
            throw invalid("페이지 범위를 초과했습니다.");
        }

        int totalCount = postService.countAll(keyword);

        int totalPages =
                (int) Math.ceil((double) totalCount / size);

        Map<String, Object> result = new HashMap<>();

        result.put(
                "posts",
                postService.findPage(
                        sort,
                        keyword,
                        (int) offsetValue,
                        size
                )
        );

        result.put("page", page);
        result.put("totalPages", totalPages);
        result.put("hasNext", page < totalPages);

        return ApiResponse.ok(result);
    }

    @PostMapping(
            value = "/upload-chunk",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ApiResponse<Map<String, Object>> uploadChunk(
            @ModelAttribute ChunkDto dto
    ) throws IOException {

        String savedFileName =
                postService.processChunkUpload(dto);

        Map<String, Object> result = new HashMap<>();

        result.put(
                "completed",
                savedFileName != null
        );

        result.put(
                "savedFileName",
                savedFileName
        );

        return ApiResponse.ok(result);
    }

    @PostMapping(
            consumes = {
                    MediaType.MULTIPART_FORM_DATA_VALUE,
                    MediaType.APPLICATION_FORM_URLENCODED_VALUE
            }
    )
    public ApiResponse<Void> createPost(
            @RequestParam("title")
            String title,

            @RequestParam("content")
            String content,

            @RequestParam(
                    value = "boardType",
                    defaultValue = "INFO"
            )
            String boardType,

            @RequestParam(
                    value = "isPublic",
                    defaultValue = "true"
            )
            boolean isPublic,

            @RequestParam(
                    value = "savedFileNamesJson",
                    required = false
            )
            String savedFileNamesJson,

            @RequestParam(
                    value = "savedFileNames",
                    required = false
            )
            String savedFileNamesAlt
    ) {

        PostDto post = makePost(
                title,
                content,
                boardType,
                isPublic
        );

        postService.saveWithFiles(
                post,
                parseFileNames(
                        savedFileNamesJson,
                        savedFileNamesAlt
                )
        );

        return ApiResponse.ok(null);
    }

    @PostMapping(
            value = {
                    "/{postId}",
                    "/{postId}/update"
            },
            consumes = {
                    MediaType.MULTIPART_FORM_DATA_VALUE,
                    MediaType.APPLICATION_FORM_URLENCODED_VALUE
            }
    )
    public ApiResponse<Void> updatePost(
            @PathVariable Long postId,

            @RequestParam("title")
            String title,

            @RequestParam("content")
            String content,

            @RequestParam(
                    value = "boardType",
                    defaultValue = "INFO"
            )
            String boardType,

            @RequestParam(
                    value = "isPublic",
                    defaultValue = "true"
            )
            boolean isPublic,

            @RequestParam(
                    value = "deleteFileIds",
                    required = false
            )
            List<Long> deleteFileIds,

            @RequestParam(
                    value = "savedFileNamesJson",
                    required = false
            )
            String savedFileNamesJson,

            @RequestParam(
                    value = "savedFileNames",
                    required = false
            )
            String savedFileNamesAlt
    ) {

        PostDto post = makePost(
                title,
                content,
                boardType,
                isPublic
        );

        post.setPostId(postId);

        postService.updateWithFiles(
                post,
                deleteFileIds,
                parseFileNames(
                        savedFileNamesJson,
                        savedFileNamesAlt
                )
        );

        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{postId}")
    public ApiResponse<Void> deletePost(
            @PathVariable Long postId
    ) {

        postService.deleteById(postId);

        return ApiResponse.ok(null);
    }

    private PostDto makePost(
            String title,
            String content,
            String boardType,
            boolean isPublic
    ) {

        PostDto post = new PostDto();

        post.setTitle(title);
        post.setContent(content);
        post.setBoardType(boardType);
        post.setPublic(isPublic);

        // 사용자 ID는 서비스에서 인증 정보로 지정한다.
        return post;
    }

    private List<String> parseFileNames(
            String primary,
            String alternative
    ) {

        String value =
                primary != null && !primary.isBlank()
                        ? primary
                        : alternative;

        if (value == null || value.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(value);

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

        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                message
        );
    }
}