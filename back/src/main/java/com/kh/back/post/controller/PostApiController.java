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

    private final ObjectMapper objectMapper = new ObjectMapper(); // json을 읽거나 변환하는 객체

    @GetMapping
    public ApiResponse<List<PostDto>> getPostPage(@RequestParam(defaultValue = "latest") String sort,

                                                  @RequestParam(defaultValue = "") String keyword,

                                                  @RequestParam(defaultValue = "1") int page,

                                                  @RequestParam(required = false) String postType) {

        return postService.getPostPage(sort, keyword, page, postType);
    } // latest 정렬방식, "" 검색어, 1 페이지 번호, null 게시판 종류 ( 받은 조건으로 서비스에 게시글 조회를 요청하고 그결과를 반환)

    @GetMapping("/{postId}")
    public ApiResponse<PostDto> getPost(@PathVariable Long postId) {

        return ApiResponse.ok(postService.findById(postId));
    } // url 의 {postId} 부분을 변수로 받음 , 서비스에 해당 번호의 게시글 조회를 요청
    // 조회한 게시글을 공통 성공 응답 형식으로 감싸 반환


    @PostMapping(value = "/upload-chunk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> uploadChunk(@ModelAttribute ChunkDto dto) throws IOException {
     // /upload-chunck로 들어오는 post 요청을 처리하고 파일을 전송하는 multipart/form-data 형식만 받음  , // 전송된 파일 조각과 업로드 정보등을 chunkdto 객체에 담음
        String savedFileName = postService.processChunkUpload(dto);
    // 서비스가 조각을 저장하고 모두 모였다면 파일을 합침 , 아직 조각이 부족하면 null , 파일 처리가 완료되면 저장된 파일명 반환
        Map<String, Object> result = new HashMap<>();
 // 응답에 넣을 이름 . 값 묶음을 만듬
        result.put("completed", savedFileName != null);  // 파일명이 있으면 완료 여부를 true로 설정하고 파일명도 함꼐 담음

        result.put("savedFileName", savedFileName);

        return ApiResponse.ok(result); // 이 데이터를 공통 성공 응답 형식으로 감싸 프론트에 반환
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
            return List.of(); // 만약 저장된 json형식 파일 이름이 null 이면 업으면, 그리고 저장된 파일이름이 비었을때 () 으로 반환
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