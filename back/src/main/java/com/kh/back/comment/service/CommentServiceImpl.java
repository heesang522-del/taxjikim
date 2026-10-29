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
public class CommentServiceImpl implements CommentService {
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
    public void create(Long postId, Long userId, String writerNickname, String content, Long parentCommentId) {
        PostDto post = requirePost(postId);

        // QNA 게시판의 댓글은 1:1 문의에 대한 답변이므로 관리자만 작성할 수 있다.
        boolean isQna = BOARD_TYPE_QNA.equals(post.getBoardType());
        if (isQna && !postValidator.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "문의 답변은 관리자만 작성할 수 있습니다.");
        }

        String trimmedContent = requireValidContent(content);

        // parentCommentId는 userId와 달리 신원 정보가 아니라 "어느 댓글에 다는지"를 가리키는
        // 참조값이라 요청값을 그대로 받되, 존재 여부·소속 글만 서버에서 검증한다.
        // parentComment는 평탄화 전 원본 부모 댓글이다(작성자 id는 답글 알림 발송에 사용).
        CommentDto parentComment = resolveParentComment(postId, parentCommentId);
        Long resolvedParentCommentId = resolveStorageParentCommentId(parentComment);

        // 작성자 번호는 요청 본문을 신뢰하지 않고 인증된 회원 정보에서 받는다.
        CommentDto comment = new CommentDto();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setParentCommentId(resolvedParentCommentId);
        comment.setContent(trimmedContent);
        commentMapper.insert(comment);

        // 관리자 답변(댓글)이 달리면 QNA 글의 답변 상태를 완료로 전환한다.
        if (isQna) {
            postMapper.updateAnswerStatus(postId, "DONE");
        }

        // 게시판 작성자 == 댓글 작성자 일 때, 중복된 알림을 보내지 않기 위해 답글에 대한 알림만 발송한다.
        boolean isReplyToPostAuthorsOwnComment = parentComment != null
                && parentComment.getUserId().equals(post.getUserId());

        // 알림 대상 게시판이고 본인 글에 본인이 댓글을 단 게 아닐 때만 알림을 보낸다.
        if (COMMENT_NOTIFY_BOARD_TYPES.contains(post.getBoardType()) && !post.getUserId().equals(userId)
                && !isReplyToPostAuthorsOwnComment) {
            notificationService.notifyPostComment(postId, post.getUserId(), userId, writerNickname,
                    post.getTitle(), trimmedContent);
        }

        // 답글이면(parentComment가 있으면) 게시판 종류와 무관하게 부모 댓글 작성자에게 알림을 보낸다.
        // 본인 댓글에 본인이 답글을 단 경우는 NotificationServiceImpl 내부에서 걸러진다.
        if (parentComment != null) {
            notificationService.notifyCommentReply(postId, parentComment.getUserId(), userId, writerNickname,
                    post.getTitle(), trimmedContent);
        }
    }

    @Transactional
    public void update(Long postId, Long commentId, Long userId, String content) {
        requirePost(postId);
        String trimmedContent = requireValidContent(content);
        // 댓글 ID와 게시글 ID, 작성자 ID를 모두 확인한 뒤 내용만 수정한다.
        if (commentMapper.update(postId, commentId, userId, trimmedContent) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", "수정할 수 있는 본인 댓글을 찾을 수 없습니다.");
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

    // 작성·수정 공통 검증: 댓글 내용은 1~500자여야 한다.
    private String requireValidContent(String content) {
        if (content == null || content.isBlank() || content.trim().length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "댓글은 1~500자로 입력해 주세요.");
        }
        return content.trim();
    }

    // 요청으로 들어온 parentCommentId가 가리키는 원본 부모 댓글을 조회·검증한다.
    // (평탄화는 저장 시에만 적용하고, 작성자 id 등 원본 정보는 그대로 보존해 알림 대상 판단에 쓴다.)
    private CommentDto resolveParentComment(Long postId, Long parentCommentId) {
        if (parentCommentId == null) {
            return null;
        }
        CommentDto parent = commentMapper.findById(parentCommentId);
        if (parent == null || !parent.getPostId().equals(postId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", "답글을 작성할 댓글을 찾을 수 없습니다.");
        }
        return parent;
    }

    // 대댓글은 1단계까지만 허용한다(스키마 제약). 답글에 또 답글을 달면
    // 원래 댓글(조부모)로 평탄화해서 저장한다.
    private Long resolveStorageParentCommentId(CommentDto parentComment) {
        if (parentComment == null) {
            return null;
        }
        return parentComment.getParentCommentId() != null ? parentComment.getParentCommentId() : parentComment.getCommentId();
    }

    private PostDto requirePost(Long postId) {
        PostDto post = postMapper.findById(postId);
        if (post == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "게시글을 찾을 수 없습니다.");
        }
        return post;
    }
}
