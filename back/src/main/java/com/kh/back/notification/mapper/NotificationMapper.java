package com.kh.back.notification.mapper;

import com.kh.back.notification.dto.NotificationDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NotificationMapper {

    // 공지사항 게시글 알림을 활성 회원 전체에게 생성
    int insertNoticeBroadcast(@Param("targetId") Long targetId, @Param("title") String title);

    // 로그인한 회원의 알림 목록 조회 (최신순)
    List<NotificationDto> findByUserId(@Param("userId") Long userId);

    // 로그인한 회원의 미읽음 알림 수 조회
    long countUnreadByUserId(@Param("userId") Long userId);

    // 로그인한 회원의 미읽음 알림을 전체 읽음 처리
    int markAllAsReadByUserId(@Param("userId") Long userId);

    // 댓글 알림 1건 생성 (수신자: 게시글 작성자)
    int insertCommentNotification(@Param("userId") Long userId, @Param("targetId") Long targetId,
                                   @Param("title") String title, @Param("message") String message);
}