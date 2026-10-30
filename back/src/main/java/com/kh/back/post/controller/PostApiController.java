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
     // 게시글 조회, 저장등을 처리하는 서비스  클래스에 @RequiredArgsConstructor 가 있다면 생성자를 통해 주입됨
    private final ObjectMapper objectMapper = new ObjectMapper();
     // json 을 java 객체로 변환하거나 , java 객체를 json으로 변환하는 도구
    @GetMapping
    public ApiResponse<List<PostDto>> getPostPage(@RequestParam(defaultValue = "latest") String sort, // 게시글 목록인 List<PostDto>를 공통 응답 형식 ApiResponse로 감싸 반환 하는 메서드
                                                  @RequestParam(defaultValue = "") String keyword,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(required = false) String postType
    ) { // 정렬방식 "latest " , 검색어 ""-빈 문자열 ,   1 조회할 페이지 번호 , null - 선택 항목
        return postService.getPostPage(sort, keyword, page, postType
        ); // 전달 받은 조건으로 서비스에 게시글 목록 조회를 요청 , 그 결과를 그대로 반환
    }

    @GetMapping("/{postId}")
    public ApiResponse<PostDto> getPost( @PathVariable Long postId
    ) {
        return ApiResponse.ok(
                postService.findById(postId)
        );
    } // url 의 { postID} 값을 가져옴

    @PostMapping(
            value = "/upload-chunk",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )    // /upload-chunck로 들어오는 post 요청 처리 , 파일 전송에 사용하는 multipart/form-data
    public ApiResponse<Map<String, Object>> uploadChunk(@AuthenticationPrincipal CustomUserDetails principal,
                                                        @ModelAttribute ChunkDto dto
    ) throws IOException {
        String savedFileName = postService.processChunkUpload(principal, dto

        ); // spring security가 관리하는 현재 로그인 사용자의 인증 정보 받음 서비스에서 principal.getuserid() 로 사용자 id를 확인
            // @ModelAttribute ChunckDto dto, 요청으로 받은 파일 조각과 업로드 정보를 chunckdto에 담음 , 업로드 id , 조각 번호 , 전체 조각수 , 원본 파일명 등이 들어감
        Map<String, Object> result = new HashMap<>(); // 이름과 값을 묶어서 보관할 공간을 만듬 , String 은 항목이름, Object는 다양한 타입의 값임
        result.put("completed", savedFileName != null); // 저장 파일명이 있으면 true , 없으면 false
        result.put("savedFileName", savedFileName); // 저장 파일명을 넣음, 아직 병합이 완료되지 않았다면 null

        return ApiResponse.ok(result);
    }

    @PostMapping(
            consumes = { MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE
            } // 클래스에 지정된 주소로 들어오는 post 요청을 처리 ,  다음 두 가지 요청 형식을 허용
    )         // multipart/form-data: 파일이나 여러 폼 값을 전달하는 형식 , application/x-www-form-urlencoded: 일반 폼 값을 전달하는 형식
    public ApiResponse<Void> createPost(@RequestParam("title") String title,
                                        @RequestParam("content") String content,
                                        @RequestParam(value = "postType", defaultValue = "INFO") String postType,
                                        @RequestParam(value = "isPublic", defaultValue = "true") boolean isPublic,
                                        @RequestParam(value = "savedFileNamesJson", required = false) String savedFileNamesJson
    ) {
        PostDto post = makePost(title, content, postType, isPublic
        );  //  제목, 내용, 게시판 종류, 공개 여부를 postdto 객체에 담아 post 변수에 저장

        postService.saveWithFiles(post, parseFileNames(savedFileNamesJson)
        ); // parseFileNames(...) Json문자열로 받은 저장 파일명 목록을 List<string> 으로 변환

        return ApiResponse.ok(null);
    }

    @PostMapping(
            value = {"/{postId}","/{postId}/update"}, consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE
            }   // 기존 게시글을 수정하기 위해 게시글 번호와 수정할 내용을 받는 부분
    )
    public ApiResponse<Void> updatePost(@PathVariable Long postId,
                                        @RequestParam("title") String title,
                                        @RequestParam("content") String content,
                                        @RequestParam(value = "postType", defaultValue = "INFO") String postType,
                                        @RequestParam(value = "isPublic", defaultValue = "true") boolean isPublic,
                                        @RequestParam(value = "deleteFileIds", required = false) List<Long> deleteFileIds,
                                        @RequestParam(value = "savedFileNamesJson", required = false) String savedFileNamesJson
    ) {
        PostDto post = makePost(title, content, postType, isPublic
        );

        post.setPostId(postId); // post 객체에 수정 대상 게시글 번호를 넣음

        postService.updateWithFiles(post, deleteFileIds, parseFileNames(savedFileNamesJson)
        ); // 게시글 번호와 수정할 제목, 내용 , 공개 여부등 , 삭제할 기존 첨부파일 번호 목록, 새로 업로드한 파일명 목록을 json 문자열에서 List<String> 으로 변환한 결과 를 서비스에 정보를 전달

        return ApiResponse.ok(null);
    }// 처리가 정상적으로 끝나면 별도 결과 데이터 없이 성공 응답을 반환, 도중에 예외가 발생하면 이 성공 응답까지 실행되지 않음

    @DeleteMapping("/{postId}")
    public ApiResponse<Void> deletePost( @PathVariable Long postId
    ) { // delete/api/vl/posts/community/10처럼 들어오는 삭제 요청을 처리
        postService.deleteById(postId);

        return ApiResponse.ok(null);
    }

    private PostDto makePost(String title, String content, String postType,
                             boolean isPublic
    ) {
        PostDto post = new PostDto();

        post.setTitle(title);
        post.setContent(content);
        post.setPostType(postType);
        post.setPublic(isPublic);

        // 작성자 ID는 서비스의 인증 정보에서 지정한다.
        return post;
    }

    private List<String> parseFileNames(
            String savedFileNamesJson
    ) {
        if (savedFileNamesJson == null
                || savedFileNamesJson.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(
                    savedFileNamesJson
            );

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

        } catch (JsonProcessingException exception) {
            throw invalid(
                    "첨부파일 목록의 형식을 확인해주세요."
            );
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