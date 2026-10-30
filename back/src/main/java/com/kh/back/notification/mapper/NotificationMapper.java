package com.kh.back.notification.mapper;

import com.kh.back.notification.dto.NotificationDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NotificationMapper {

    // 공지사항 게시글 알림을 활성 회원 전체에게 생성
    int insertNoticeBroadcast(@Param("targetId") Long targetId, @Param("title") String title);

    // 로그인한 회원의 미읽음 알림 목록 커서 페이징 조회 (최신순). cursorId가 없으면 처음부터 조회한다.
    List<NotificationDto> findByUserId(@Param("userId") Long userId, @Param("cursorId") Long cursorId,
                                        @Param("limit") int limit);

    // 로그인한 회원의 미읽음 알림 수 조회
    long countUnreadByUserId(@Param("userId") Long userId);

    // 알림 1건을 읽음 처리(+소프트 삭제)한다. 처리된 행 수를 반환한다(0이면 대상 없음/본인 알림 아님).
    int markAsReadById(@Param("userId") Long userId, @Param("notificationId") Long notificationId);

    // 댓글 알림 1건 생성 (수신자: 게시글 작성자)
    int insertCommentNotification(@Param("userId") Long userId, @Param("targetId") Long targetId,
                                   @Param("title") String title, @Param("message") String message);

    // 답글 알림 1건 생성 (수신자: 답글이 달린 부모 댓글의 작성자)
    int insertReplyNotification(@Param("userId") Long userId, @Param("targetId") Long targetId,
                                 @Param("title") String title, @Param("message") String message);
}