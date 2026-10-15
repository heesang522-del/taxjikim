package com.kh.back.auth.controller;

import com.kh.back.auth.dto.AccountLookupRequestDto;
import com.kh.back.auth.dto.PasswordResetRequestDto;
import com.kh.back.auth.service.AuthService;
import com.kh.back.auth.service.EmailVerificationService;
import com.kh.back.common.dto.ApiResponse;
import com.kh.back.common.exception.ApiException;
import com.kh.back.common.validation.InputValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AccountLookupApiController {
    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;

    @PostMapping("/account-lookups/email-verifications")
    public ApiResponse<Void> sendLookupCode(@RequestBody AccountLookupRequestDto request) {
        InputValidator.requireText("name", request.name());
        InputValidator.requireText("email", request.email());
        authService.sendCodeForFindId(request.name(), request.email());
        return ApiResponse.ok(null);
    }

    @PostMapping("/password-resets/email-verifications")
    public ApiResponse<Void> sendPasswordResetCode(@RequestBody PasswordResetRequestDto request) {
        InputValidator.requireText("loginId", request.loginId());
        InputValidator.requireText("email", request.email());
        authService.sendCodeForFindPw(request.loginId(), request.email());
        return ApiResponse.ok(null);
    }

    @PostMapping("/account-lookups")
    public ApiResponse<String> findLoginId(@RequestBody AccountLookupRequestDto request) {
        InputValidator.requireText("name", request.name());
        InputValidator.requireText("email", request.email());
        InputValidator.requireText("code", request.code());
        // 서버가 인증번호를 확인한 다음에만 기존 계정 조회 서비스를 호출한다.
        if (!emailVerificationService.verifyCode(request.email(), request.code())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VERIFICATION_CODE_INVALID", "인증번호가 올바르지 않습니다.");
        }
        return ApiResponse.ok(authService.findLoginId(request.name(), request.email()));
    }

    @PostMapping("/password-resets")
    public ApiResponse<Void> resetPassword(@RequestBody PasswordResetRequestDto request) {
        authService.resetPassword(request.loginId(), request.email(), request.newPassword());
        return ApiResponse.ok(null);
    }
}
