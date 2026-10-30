package com.kh.back.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

// 알림 목록 커서 페이징 조회 결과. Service와 Controller 사이에서만 쓰고 API 응답은 data/meta로 나눠 내려준다.
@Getter
@AllArgsConstructor
public class NotificationPageDto {

    private final List<NotificationDto> items;
    private final boolean hasMore;
    private final Long nextCursor; // 다음 조회에 쓸 마지막 알림 ID. 더 없으면 null
}
