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
}
