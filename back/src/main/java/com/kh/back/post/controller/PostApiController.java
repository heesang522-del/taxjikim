
package com.kh.back.post.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.common.dto.ApiResponse;
import com.kh.back.common.exception.ApiException;
import com.kh.back.post.dto.ChunkDto;
import com.kh.back.post.dto.PostDto;
import com.kh.back.post.service.PostService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    // JSON을 읽거나 변환하는 객체
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 게시글 목록 조회
     *
     * 기존 기능 유지
     */
    @GetMapping
    public ApiResponse<List<PostDto>> getPostPage(
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) String postType
    ) {

        return postService.getPostPage(
                sort,
                keyword,
                page,
                postType
        );
    }

    /**
     * 게시글 상세 조회
     *
     * Q&A 비공개 게시글 권한 검사는
     * 기존 PostServiceImpl에서 처리한다.
     */
    @GetMapping("/{postId}")
    public ApiResponse<PostDto> getPost(
            @PathVariable Long postId
    ) {

        return ApiResponse.ok(
                postService.findById(postId)
        );
    }

    /**
     * 청크 파일 업로드
     *
     * 수정 사항:
     * 1. 인증된 사용자 정보를 전달받는다.
     * 2. PostService 인터페이스의 매개변수와 일치시킨다.
     *
     * 기존 API 주소 및 응답 형식은 유지한다.
     */
    @PostMapping(
            value = "/upload-chunk",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ApiResponse<Map<String, Object>> uploadChunk(
            @AuthenticationPrincipal CustomUserDetails principal,
            @ModelAttribute ChunkDto dto
    ) throws IOException {

        // 수정: 인증 사용자와 청크 정보를 함께 전달
        String savedFileName =
                postService.processChunkUpload(principal, dto);

        Map<String, Object> result = new HashMap<>();

        // 모든 청크가 병합되면 true
        result.put(
                "completed",
                savedFileName != null
        );

        // 업로드 완료 시 저장된 파일명 반환
        result.put(
                "savedFileName",
                savedFileName
        );

        return ApiResponse.ok(result);
    }

    /**
     * 게시글 작성
     *
     * 기존 기능 유지
     * Q&A, 공지사항, FAQ 등 기존 권한 검증 유지
     * 공지사항 알림 발송은 기존 서비스에서 처리
     */
    @PostMapping(
            consumes = {
                    MediaType.MULTIPART_FORM_DATA_VALUE,
                    MediaType.APPLICATION_FORM_URLENCODED_VALUE
            }
    )
    public ApiResponse<Void> createPost(
            @RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam(
                    value = "postType",
                    defaultValue = "INFO"
            ) String postType,
            @RequestParam(
                    value = "isPublic",
                    defaultValue = "true"
            ) boolean isPublic,
            @RequestParam(
                    value = "savedFileNamesJson",
                    required = false
            ) String savedFileNamesJson
    ) {

        PostDto post = makePost(
                title,
                content,
                postType,
                isPublic
        );

        postService.saveWithFiles(
                post,
                parseFileNames(savedFileNamesJson)
        );

        return ApiResponse.ok(null);
    }

    /**
     * 게시글 수정
     *
     * 기존 기능 유지
     * Q&A 권한 검사 및 첨부파일 처리 방식 변경 없음
     */
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
            @RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam(
                    value = "postType",
                    defaultValue = "INFO"
            ) String postType,
            @RequestParam(
                    value = "isPublic",
                    defaultValue = "true"
            ) boolean isPublic,
            @RequestParam(
                    value = "deleteFileIds",
                    required = false
            ) List<Long> deleteFileIds,
            @RequestParam(
                    value = "savedFileNamesJson",
                    required = false
            ) String savedFileNamesJson
    ) {

        PostDto post = makePost(
                title,
                content,
                postType,
                isPublic
        );

        post.setPostId(postId);

        postService.updateWithFiles(
                post,
                deleteFileIds,
                parseFileNames(savedFileNamesJson)
        );

        return ApiResponse.ok(null);
    }

    /**
     * 게시글 삭제
     *
     * 기존 기능 유지
     */
    @DeleteMapping("/{postId}")
    public ApiResponse<Void> deletePost(
            @PathVariable Long postId
    ) {

        postService.deleteById(postId);

        return ApiResponse.ok(null);
    }

    /**
     * 게시글 DTO 생성
     *
     * 기존 기능 유지
     */
    private PostDto makePost(
            String title,
            String content,
            String postType,
            boolean isPublic
    ) {

        PostDto post = PostDto.builder()
                .title(title)
                .content(content)
                .postType(postType)
                .isPublic(isPublic)
                .build();

        // 사용자 ID는 서비스에서 인증 정보로 지정한다.
        return post;
    }

    /**
     * 첨부파일 JSON 문자열을 목록으로 변환
     *
     * 기존 기능 유지
     */
    private List<String> parseFileNames(
            String savedFileNamesJson
    ) {

        if (savedFileNamesJson == null
                || savedFileNamesJson.isBlank()) {

            return List.of();
        }

        try {

            JsonNode root =
                    objectMapper.readTree(savedFileNamesJson);

            if (root == null || !root.isArray()) {
                throw invalid(
                        "첨부파일 목록은 배열이어야 합니다."
                );
            }

            List<String> names = new ArrayList<>();

            for (JsonNode node : root) {

                if (!node.isTextual()) {
                    throw invalid(
                            "첨부파일명은 문자열이어야 합니다."
                    );
                }

                names.add(node.textValue());
            }

            return names;

        } catch (JsonProcessingException e) {

            throw invalid(
                    "첨부파일 목록의 형식을 확인해주세요."
            );
        }
    }

    /**
     * 공통 입력값 검증 예외
     *
     * 기존 기능 유지
     */
    private ApiException invalid(String message) {

        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                message
        );
    }
}
