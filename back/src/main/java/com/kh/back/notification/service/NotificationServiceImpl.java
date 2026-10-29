package com.kh.back.notification.service;

import com.kh.back.common.sse.SseEmitterRegistry;
import com.kh.back.notification.dto.NotificationDto;
import com.kh.back.notification.dto.NotificationPageDto;
import com.kh.back.notification.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final SseEmitterRegistry sseEmitterRegistry;

    private static final int TITLE_MAX_LENGTH = 100; // NOTIFICATIONS.TITLE 컬럼 길이
    private static final int COMMENT_PREVIEW_LENGTH = 15; // 알림에 노출할 댓글 내용 미리보기 길이
    private static final int DEFAULT_PAGE_SIZE = 10; // size 파라미터가 없을 때 기본 페이지 크기
    private static final int MAX_PAGE_SIZE = 50; // 클라이언트가 요청할 수 있는 최대 페이지 크기
    private static final String NOTIFICATION_EVENT = "notification"; // SSE 이벤트명. payload는 신호 역할만 하고, 실제 데이터는 프론트가 기존 조회 API로 다시 가져온다.

    @Override
    public SseEmitter subscribe(Long userId) {
        return sseEmitterRegistry.subscribe(userId);
    }

    @Override
    @Transactional
    public void broadcastNoticePost(Long postId, String title) {
        notificationMapper.insertNoticeBroadcast(postId, title);
        // insert가 아직 커밋되기 전에 push하면, push를 받은 클라이언트가 바로 목록을 다시 조회해도
        // 아직 안 보이는 경우가 생긴다. 그래서 커밋이 끝난 뒤에만 push 하도록 미룬다.
        // 대상 유저 목록을 따로 조회하지 않고, 지금 연결돼 있는(=로그인 중인) 모든 회원에게 보낸다.
        pushAfterCommit(() -> sseEmitterRegistry.sendToAll(NOTIFICATION_EVENT, Map.of("type", "NOTICE")));
    }

    @Override
    public NotificationPageDto getNotifications(Long userId, Long cursorId, Integer size) {
        int pageSize = resolvePageSize(size);
        // 다음 페이지가 있는지 알아보려고 한 건 더 가져온 뒤, 넘치면 마지막 한 건을 떼어내고 hasMore로 알린다.
        List<NotificationDto> fetched = notificationMapper.findByUserId(userId, cursorId, pageSize + 1);
        boolean hasMore = fetched.size() > pageSize;
        List<NotificationDto> items = hasMore ? fetched.subList(0, pageSize) : fetched;
        Long nextCursor = hasMore ? items.get(items.size() - 1).getNotificationId() : null;
        return new NotificationPageDto(items, hasMore, nextCursor);
    }

    // size가 비어있으면 기본값을, 범위를 벗어나면 1~MAX_PAGE_SIZE로 잘라낸다.
    private int resolvePageSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    @Override
    public long getUnreadCount(Long userId) {
        return notificationMapper.countUnreadByUserId(userId);
    }

    @Override
    @Transactional
    public void markAllAsRead(Long userId) {
        notificationMapper.markAllAsReadByUserId(userId);
    }

    @Override
    @Transactional
    public void notifyPostComment(Long postId, Long postAuthorId, Long commenterId, String commenterNickname,
                                   String postTitle, String commentContent) {
        // 본인 글에 본인이 댓글을 달면 알림을 보내지 않는다. 탈퇴 여부는 체크하지 않는다.
        if (postAuthorId.equals(commenterId)) {
            return;
        }
        String safeTitle = (postTitle != null && postTitle.length() > TITLE_MAX_LENGTH)
                ? postTitle.substring(0, TITLE_MAX_LENGTH)
                : postTitle;
        // 알림 목록에는 "닉네임: 댓글 미리보기" 형태로 보여준다.
        String message = commenterNickname + ": " + buildPreview(commentContent);
        notificationMapper.insertCommentNotification(postAuthorId, postId, safeTitle, message);
        // 글 작성자 한 명에게만 보낸다. 커밋 후 push(이유는 broadcastNoticePost 주석 참고).
        pushAfterCommit(() -> sseEmitterRegistry.send(postAuthorId, NOTIFICATION_EVENT, Map.of("type", "COMMENT")));
    }

    @Override
    @Transactional
    public void notifyCommentReply(Long postId, Long parentCommentAuthorId, Long replierId, String replierNickname,
                                    String postTitle, String replyContent) {
        // 본인 댓글에 본인이 답글을 달면 알림을 보내지 않는다. 탈퇴 여부는 체크하지 않는다.
        if (parentCommentAuthorId.equals(replierId)) {
            return;
        }
        String safeTitle = (postTitle != null && postTitle.length() > TITLE_MAX_LENGTH)
                ? postTitle.substring(0, TITLE_MAX_LENGTH)
                : postTitle;
        String message = replierNickname + ": " + buildPreview(replyContent);
        notificationMapper.insertReplyNotification(parentCommentAuthorId, postId, safeTitle, message);
        // 부모 댓글 작성자 한 명에게만 보낸다. 커밋 후 push(이유는 broadcastNoticePost 주석 참고).
        pushAfterCommit(() -> sseEmitterRegistry.send(parentCommentAuthorId, NOTIFICATION_EVENT, Map.of("type", "REPLY")));
    }

    // 댓글 내용 미리보기: 15자를 넘으면 자르고 말줄임표(...)를 붙인다.
    private String buildPreview(String content) {
        if (content == null || content.length() <= COMMENT_PREVIEW_LENGTH) {
            return content;
        }
        return content.substring(0, COMMENT_PREVIEW_LENGTH) + "...";
    }

    // 현재 트랜잭션이 실제로 커밋된 뒤에만 push를 실행한다. 트랜잭션 밖에서 호출된 경우(동기화가 없는 경우)에는 바로 실행한다.
    private void pushAfterCommit(Runnable push) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            push.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                push.run();
            }
        });
    }
}
