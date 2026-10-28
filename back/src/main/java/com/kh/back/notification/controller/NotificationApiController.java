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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationApiController {

    private final NotificationService notificationService;


    //로그인한 회원의 알림 목록 커서 페이징 조회 (공지사항 게시글 ID + title 포함)
    //cursorId: 이전 응답 meta.nextCursor 값. 없으면 최신 알림부터 조회한다.
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

    //로그인한 회원의 미읽음 알림 수 조회
    @GetMapping("/unread-count")
    public ApiResponse<Long> getUnreadCount(@AuthenticationPrincipal CustomUserDetails principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
        }

        return ApiResponse.ok(notificationService.getUnreadCount(principal.getUserId()));
    }

    //로그인한 회원의 미읽음 알림 전체 읽음 처리
    @PatchMapping("/read-all")
    public ApiResponse<Void> markAllAsRead(@AuthenticationPrincipal CustomUserDetails principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
        }

        notificationService.markAllAsRead(principal.getUserId());
        return ApiResponse.ok(null);
    }


}
