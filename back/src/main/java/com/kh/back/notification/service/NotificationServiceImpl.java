package com.kh.back.notification.service;

import com.kh.back.notification.dto.NotificationDto;
import com.kh.back.notification.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;

    private static final int TITLE_MAX_LENGTH = 100; // NOTIFICATIONS.TITLE 컬럼 길이
    private static final int COMMENT_PREVIEW_LENGTH = 15; // 알림에 노출할 댓글 내용 미리보기 길이

    @Override
    @Transactional
    public void broadcastNoticePost(Long postId, String title) {
        notificationMapper.insertNoticeBroadcast(postId, title);
    }

    @Override
    public List<NotificationDto> getNotifications(Long userId) {
        return notificationMapper.findByUserId(userId);
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
    }

    // 댓글 내용 미리보기: 15자를 넘으면 자르고 말줄임표(...)를 붙인다.
    private String buildPreview(String content) {
        if (content == null || content.length() <= COMMENT_PREVIEW_LENGTH) {
            return content;
        }
        return content.substring(0, COMMENT_PREVIEW_LENGTH) + "...";
    }
}
