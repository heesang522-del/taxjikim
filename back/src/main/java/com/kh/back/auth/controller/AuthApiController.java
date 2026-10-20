package com.kh.back.auth.controller;

import com.kh.back.auth.dto.AccountRecoveryRequestDto;
import com.kh.back.auth.dto.LoginRequestDto;
import com.kh.back.auth.dto.LoginResponseDto;
import com.kh.back.auth.dto.TokenResponseDto;
import com.kh.back.auth.service.AuthService;
import com.kh.back.auth.service.EmailVerificationService;
import com.kh.back.common.dto.ApiResponse;
import com.kh.back.common.validation.InputValidator;
import com.kh.back.common.exception.ApiException;
import com.kh.back.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthApiController {
    private final UserService userService;
    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;

    // 아이디·비밀번호와 자동 로그인 선택값을 받아 로그인 처리
    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<LoginResponseDto>> login(@RequestBody LoginRequestDto request, HttpServletResponse response) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                authService.login(request.loginId(), request.password(), request.rememberMe(), response)));
    }

    // 현재 로그인 종료 및 인증 쿠키 삭제 요청 처리
    @DeleteMapping("/sessions/current")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return ApiResponse.ok(null);
    }

    // 리프레시 토큰 쿠키로 인증 토큰 재발급 요청 처리
    @PostMapping("/tokens")
    public ApiResponse<TokenResponseDto> reissue(
            @CookieValue(name = "refreshToken", required = false) String refreshToken, HttpServletResponse response) {
        return ApiResponse.ok(new TokenResponseDto(authService.reissue(refreshToken, response), "Bearer"));
    }

    // 이메일 인증번호 확인 후 휴면 해제 요청 처리 (휴면 기능 전체는 미완료)
    @PostMapping("/dormancy-recoveries")
    public ApiResponse<Void> unlockDormant(@RequestBody AccountRecoveryRequestDto request) {
        InputValidator.requireText("loginId", request.loginId());
        InputValidator.requireText("email", request.email());
        InputValidator.requireText("code", request.code());
        // 인증번호 검증 후 계정 소유권과 상태 검증은 회원 업무 서비스에 맡긴다.
        if (!emailVerificationService.verifyCode(request.email(), request.code())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VERIFICATION_CODE_INVALID", "인증번호가 올바르지 않습니다.");
        }
        userService.restoreDormantAccount(request.loginId(), request.email());
        return ApiResponse.ok(null);
    }
}
