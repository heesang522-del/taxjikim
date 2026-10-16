package com.kh.back.comment.controller;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.comment.dto.CommentDto;
import com.kh.back.comment.service.CommentService;
import com.kh.back.common.dto.ApiResponse;
import com.kh.back.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/posts/{postId}/comments")
@RequiredArgsConstructor
public class CommentApiController {
    private final CommentService commentService;
    public record CreateCommentRequest(String content) {}

    @GetMapping
    public ApiResponse<List<CommentDto>> list(@PathVariable Long postId) {
        return ApiResponse.ok(commentService.findByPostId(postId));
    }

    @PostMapping
    public ApiResponse<Void> create(@PathVariable Long postId, @RequestBody CreateCommentRequest request,
                                   @AuthenticationPrincipal CustomUserDetails principal) {
        commentService.create(postId, requireUserId(principal), request.content());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{commentId}")
    public ApiResponse<Void> delete(@PathVariable Long postId, @PathVariable Long commentId,
                                   @AuthenticationPrincipal CustomUserDetails principal) {
        commentService.delete(postId, commentId, requireUserId(principal));
        return ApiResponse.ok(null);
    }

    private Long requireUserId(CustomUserDetails principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
        }
        return principal.getUserId();
    }
}
