package com.kh.back.user.controller;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.auth.service.AuthCookieService;
import com.kh.back.auth.security.LoginSessionRepository;
import com.kh.back.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import com.kh.back.common.dto.ApiResponse;
import com.kh.back.user.dto.UserDto;
import com.kh.back.user.dto.UserUpdateRequestDto;
import com.kh.back.user.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserProfileApiController {
    private final UserService userService;
    private final AuthCookieService authCookieService;
    private final LoginSessionRepository loginSessionRepository;

    @GetMapping
    public ApiResponse<UserDto> getUser(@AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok(userService.getUserById(principal.getUserId()));
    }

    @PatchMapping("/nickname")
    public ApiResponse<Void> updateNickname(@AuthenticationPrincipal CustomUserDetails principal, @RequestBody UserUpdateRequestDto request) {
        userService.updateNickname(principal.getUsername(), request.nickname());
        return ApiResponse.ok(null);
    }

    @PatchMapping("/email")
    public ApiResponse<Void> updateEmail(@AuthenticationPrincipal CustomUserDetails principal, @RequestBody UserUpdateRequestDto request) {
        userService.updateEmail(principal.getUsername(), request.email());
        return ApiResponse.ok(null);
    }

    @PutMapping("/password")
    public ApiResponse<Void> updatePassword(@AuthenticationPrincipal CustomUserDetails principal, @RequestBody UserUpdateRequestDto request) {
        userService.updatePasswordInSettings(principal.getUsername(), request.currentPassword(), request.newPassword());
        return ApiResponse.ok(null);
    }

    @PutMapping("/profile-image")
    public ApiResponse<Void> updateProfileImage(@AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(value = "deleteProfile", defaultValue = "false") boolean deleteProfile,
            @RequestParam(value = "profile", required = false) MultipartFile profile) {
        userService.updateProfileImageUrl(principal.getUsername(), profile, deleteProfile);
        return ApiResponse.ok(null);
    }

    @DeleteMapping
    public ApiResponse<Void> deleteUser(@AuthenticationPrincipal CustomUserDetails principal,
            @RequestBody UserUpdateRequestDto request, HttpServletResponse response) {
        // 기존 탈퇴 흐름의 비밀번호 확인·계정 삭제·세션 종료 순서를 유지한다.
        if (!userService.checkPassword(principal.getUsername(), request.currentPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INVALID", "비밀번호가 올바르지 않습니다.");
        }
        userService.deleteUser(principal.getUsername());
        loginSessionRepository.revokeCurrentSession(principal.getUserId());
        authCookieService.clearRefreshToken(response);
        SecurityContextHolder.clearContext();
        return ApiResponse.ok(null);
    }
}
