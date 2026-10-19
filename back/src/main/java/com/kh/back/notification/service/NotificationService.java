package com.kh.back.notification.service;

import com.kh.back.notification.dto.NotificationDto;

import java.util.List;

public interface NotificationService {

    // 공지사항 게시글 업로드 시 전체 활성 회원에게 알림 발송
    void broadcastNoticePost(Long postId, String title);

    // 로그인한 회원의 알림 목록 조회
    List<NotificationDto> getNotifications(Long userId);

    // 로그인한 회원의 미읽음 알림 수 조회
    long getUnreadCount(Long userId);

    // 로그인한 회원의 미읽음 알림을 전체 읽음 처리
    void markAllAsRead(Long userId);
}
