package com.kh.back.notification.service;

import com.kh.back.notification.dto.NotificationPageDto;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface NotificationService {

    // 로그인한 회원의 알림 SSE 구독을 시작한다. 실제 연결 관리는 SseEmitterRegistry에 위임한다.
    SseEmitter subscribe(Long userId);

    // 공지사항 게시글 업로드 시 전체 활성 회원에게 알림 발송
    void broadcastNoticePost(Long postId, String title);

    // 로그인한 회원의 알림 목록 커서 페이징 조회. cursorId·size가 없으면 기본값으로 처음부터 조회한다.
    NotificationPageDto getNotifications(Long userId, Long cursorId, Integer size);

    // 로그인한 회원의 미읽음 알림 수 조회
    long getUnreadCount(Long userId);

    // 로그인한 회원의 미읽음 알림을 전체 읽음 처리
    void markAllAsRead(Long userId);

    // 정보공유·팁·QnA 게시글에 댓글이 달렸을 때 게시글 작성자에게 알림 발송 (본인 댓글 제외)
    void notifyPostComment(Long postId, Long postAuthorId, Long commenterId, String commenterNickname,
                            String postTitle, String commentContent);

    // 내 댓글에 답글이 달렸을 때 부모 댓글 작성자에게 알림 발송 (본인 답글 제외)
    void notifyCommentReply(Long postId, Long parentCommentAuthorId, Long replierId, String replierNickname,
                             String postTitle, String replyContent);
}
