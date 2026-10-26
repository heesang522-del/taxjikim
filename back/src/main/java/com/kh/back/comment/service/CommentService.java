package com.kh.back.comment.service;

import com.kh.back.comment.dto.CommentDto;
import com.kh.back.comment.mapper.CommentMapper;
import com.kh.back.common.exception.ApiException;
import com.kh.back.common.validation.PostValidator;
import com.kh.back.notification.service.NotificationService;
import com.kh.back.post.dto.PostDto;
import com.kh.back.post.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final CommentMapper commentMapper;
    private final PostMapper postMapper;
    private final NotificationService notificationService;
    private final PostValidator postValidator;

    // 댓글 알림을 보낼 게시판 종류. 공지(NOTICE)는 업로드 시 전체 알림이 따로 있어 제외한다.
    private static final Set<String> COMMENT_NOTIFY_BOARD_TYPES = Set.of("INFO", "TIP", "QNA");

    private static final String BOARD_TYPE_QNA = "QNA";

    public List<CommentDto> findByPostId(Long postId) {
        requirePost(postId);
        return commentMapper.findByPostId(postId);
    }

    @Transactional
    public void create(Long postId, Long userId, String writerNickname, String content) {
        PostDto post = requirePost(postId);

        // QNA 게시판의 댓글은 1:1 문의에 대한 답변이므로 관리자만 작성할 수 있다.
        boolean isQna = BOARD_TYPE_QNA.equals(post.getBoardType());
        if (isQna && !postValidator.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "문의 답변은 관리자만 작성할 수 있습니다.");
        }

        if (content == null || content.isBlank() || content.trim().length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "댓글은 1~500자로 입력해 주세요.");
        }
        String trimmedContent = content.trim();

        // 작성자 번호는 요청 본문을 신뢰하지 않고 인증된 회원 정보에서 받는다.
        CommentDto comment = new CommentDto();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setContent(trimmedContent);
        commentMapper.insert(comment);

        // 관리자 답변(댓글)이 달리면 QNA 글의 답변 상태를 완료로 전환한다.
        if (isQna) {
            postMapper.updateAnswerStatus(postId, "DONE");
        }

        // 알림 대상 게시판이고 본인 글에 본인이 댓글을 단 게 아닐 때만 알림을 보낸다.
        if (COMMENT_NOTIFY_BOARD_TYPES.contains(post.getBoardType()) && !post.getUserId().equals(userId)) {
            notificationService.notifyPostComment(postId, post.getUserId(), userId, writerNickname,
                    post.getTitle(), trimmedContent);
        }
    }

    @Transactional
    public void delete(Long postId, Long commentId, Long userId) {
        requirePost(postId);
        // 댓글 ID와 게시글 ID, 작성자 ID를 모두 확인한 뒤 소프트 삭제한다.
        if (commentMapper.softDelete(postId, commentId, userId) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", "삭제할 수 있는 본인 댓글을 찾을 수 없습니다.");
        }
    }

    private PostDto requirePost(Long postId) {
        PostDto post = postMapper.findById(postId);
        if (post == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "게시글을 찾을 수 없습니다.");
        }
        return post;
    }
}
