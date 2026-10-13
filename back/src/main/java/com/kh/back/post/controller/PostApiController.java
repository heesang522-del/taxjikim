package com.kh.back.post.controller;

import com.kh.back.common.dto.ApiResponse;
import com.kh.back.common.dto.PageMeta;
import com.kh.back.common.dto.PageRequestDto;
import com.kh.back.common.dto.PageResponseDto;
import com.kh.back.post.dto.ChunkDto;
import com.kh.back.post.dto.ChunkUploadResponseDto;
import com.kh.back.post.dto.PostDto;
import com.kh.back.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostApiController {
    private final PostService postService;

    @GetMapping
    public ApiResponse<List<PostDto>> getPosts(@RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "") String keyword, @RequestParam(defaultValue = "1") int page) {
        PageRequestDto request = new PageRequestDto();
        request.setPage(page);
        request.setSize(9);
        PageResponseDto<PostDto> result = postService.getPostPage(request, sort, keyword);
        return ApiResponse.ok(result.getContent(), PageMeta.of(result.getPage(), result.getSize(), result.getTotalCount()));
    }

    @GetMapping("/{postId}")
    public ApiResponse<PostDto> getPost(@PathVariable Long postId) {
        return ApiResponse.ok(postService.findById(postId));
    }

    @PostMapping("/upload-chunks")
    public ApiResponse<ChunkUploadResponseDto> uploadChunk(ChunkDto request) throws IOException {
        String savedFileName = postService.processChunkUpload(request);
        return ApiResponse.ok(new ChunkUploadResponseDto(savedFileName != null, savedFileName));
    }

    @PostMapping(consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    public ResponseEntity<ApiResponse<Void>> createPost(@RequestParam String title,
            @RequestParam(required = false) String place, @RequestParam String content,
            @RequestParam(defaultValue = "0") Long transportCost, @RequestParam(defaultValue = "0") Long foodCost,
            @RequestParam(defaultValue = "0") Long otherCost,
            @RequestParam(value = "savedFileNamesJson", required = false) String savedFileNamesJson,
            @RequestParam(value = "savedFileNames", required = false) String savedFileNames) {
        postService.createPost(title, place, content, transportCost, foodCost, otherCost, savedFileNamesJson, savedFileNames);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(null));
    }

    @PatchMapping(value = "/{postId}", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    public ApiResponse<Void> updatePost(@PathVariable Long postId, @RequestParam String title,
            @RequestParam(required = false) String place, @RequestParam String content,
            @RequestParam(defaultValue = "0") Long transportCost, @RequestParam(defaultValue = "0") Long foodCost,
            @RequestParam(defaultValue = "0") Long otherCost, @RequestParam(required = false) List<Long> deleteImageIds,
            @RequestParam(value = "savedFileNamesJson", required = false) String savedFileNamesJson,
            @RequestParam(value = "savedFileNames", required = false) String savedFileNames) {
        postService.updatePost(postId, title, place, content, transportCost, foodCost, otherCost, deleteImageIds, savedFileNamesJson, savedFileNames);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{postId}")
    public ApiResponse<Void> deletePost(@PathVariable Long postId) {
        postService.deleteById(postId);
        return ApiResponse.ok(null);
    }
}
