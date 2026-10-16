package com.kh.back.notification.controller;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.common.dto.ApiResponse;
import com.kh.back.common.exception.ApiException;
import com.kh.back.notification.dto.NotificationDto;
import com.kh.back.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationApiController {

    private final NotificationService notificationService;


    //로그인한 회원의 알림 목록 조회 (공지사항 게시글 ID + title 포함)
    @GetMapping
    public ApiResponse<List<NotificationDto>> getNotifications(@AuthenticationPrincipal CustomUserDetails principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
        }

        return ApiResponse.ok(notificationService.getNotifications(principal.getUserId()));
    }

}
