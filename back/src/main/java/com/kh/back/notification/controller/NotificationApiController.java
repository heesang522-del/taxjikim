package com.kh.back.notification.controller;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.common.dto.ApiResponse;
import com.kh.back.common.dto.CursorMeta;
import com.kh.back.common.exception.ApiException;
import com.kh.back.notification.dto.NotificationDto;
import com.kh.back.notification.dto.NotificationPageDto;
import com.kh.back.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationApiController {

    private final NotificationService notificationService;


    // 로그인한 회원의 알림 실시간 구독(SSE). 이벤트는 "알림이 바뀌었다"는 신호만 보내고,
    // 실제 목록·안읽음 수는 클라이언트가 받은 뒤 기존 조회 API로 다시 가져온다.
    // SSE 응답은 스트림이라 공통 ApiResponse 포맷을 쓰지 않는다.
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal CustomUserDetails principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
        }

        return notificationService.subscribe(principal.getUserId());
    }

    //로그인한 회원의 미읽음 알림 목록 커서 페이징 조회 (공지사항 게시글 ID + title 포함)
    //cursorId: 이전 응답 meta.nextCursor 값. 없으면 최신 알림부터 조회한다.
    // 로그인한 회원 알림 목록 보기 + Cursor기반 페이징 처리 (무한스크롤)
    @GetMapping
    public ApiResponse<List<NotificationDto>> getNotifications(@AuthenticationPrincipal CustomUserDetails principal,
                                                                 @RequestParam(required = false) Long cursorId,
                                                                 @RequestParam(required = false) Integer size) {
        if (principal == null || principal.getUserId() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
        }

        NotificationPageDto page = notificationService.getNotifications(principal.getUserId(), cursorId, size);
        return ApiResponse.ok(page.getItems(), new CursorMeta(page.isHasMore(), page.getNextCursor()));
    }

    // 로그인한 회원의 미읽음 알림 수 조회
    // 모달 숫자 표시
    @GetMapping("/unread-count")
    public ApiResponse<Long> getUnreadCount(@AuthenticationPrincipal CustomUserDetails principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
        }

        return ApiResponse.ok(notificationService.getUnreadCount(principal.getUserId()));
    }

    //알림 1건 읽음 처리. 읽은 알림은 곧바로 목록에서 빠진다(소프트 삭제).
    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> markAsRead(@AuthenticationPrincipal CustomUserDetails principal,
                                         @PathVariable Long notificationId) {
        if (principal == null || principal.getUserId() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
        }

        notificationService.markAsRead(principal.getUserId(), notificationId);
        return ApiResponse.ok(null);
    }


}
