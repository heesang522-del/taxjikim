package com.kh.back.comment.controller;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.comment.dto.CommentDto;
import com.kh.back.comment.service.CommentServiceImpl;
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
    private final CommentServiceImpl commentService;

    // 작성자(userId)는 클라이언트가 보낸 값이 아니라 JwtAuthenticationFilter가
    // 쿠키의 토큰을 검증해 넘겨준 로그인 사용자에서 가져온다. userId를 이 타입에
    // 아예 두지 않아, 다른 사람인 척 요청을 보내는 걸 막는다.
    // parentCommentId는 신원이 아니라 "어느 댓글에 답글을 다는지" 가리키는 참조값이라
    // 클라이언트가 보낸 값을 그대로 받고, 유효성은 Service에서 검증한다.
    public record CreateCommentRequest(String content, Long parentCommentId) {}

    public record UpdateCommentRequest(String content) {}

    @GetMapping // 게시글 댓글 불러오기
    public ApiResponse<List<CommentDto>> list(@PathVariable Long postId) {
        return ApiResponse.ok(commentService.findByPostId(postId));
    }

    @PostMapping
    public ApiResponse<Void> create(@PathVariable Long postId, @RequestBody CreateCommentRequest request,
                                   @AuthenticationPrincipal CustomUserDetails principal) {
        Long userId = requireUserId(principal);
        // 알림에 표시할 작성자 닉네임은 인증된 로그인 정보에서 가져온다 (재조회 없이 재사용).
        commentService.create(postId, userId, principal.getUserDto().getNickname(), request.content(),
                request.parentCommentId());
        return ApiResponse.ok(null);
    }

    @PatchMapping("/{commentId}")
    public ApiResponse<Void> update(@PathVariable Long postId, @PathVariable Long commentId,
                                   @RequestBody UpdateCommentRequest request,
                                   @AuthenticationPrincipal CustomUserDetails principal) {
        commentService.update(postId, commentId, requireUserId(principal), request.content());
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
