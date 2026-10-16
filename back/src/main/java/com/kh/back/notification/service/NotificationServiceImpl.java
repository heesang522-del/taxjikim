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
}
