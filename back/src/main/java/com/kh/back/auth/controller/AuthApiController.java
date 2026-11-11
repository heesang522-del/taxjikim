package com.kh.back.auth.controller;

import com.kh.back.auth.dto.LoginRequestDto;
import com.kh.back.auth.dto.LoginResponseDto;
import com.kh.back.auth.service.AuthService;
import com.kh.back.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthApiController {
    private final AuthService authService;

    // 아이디·비밀번호와 자동 로그인 선택값을 받아 로그인 처리
    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<LoginResponseDto>> login(@RequestBody LoginRequestDto request, HttpServletResponse response) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                authService.login(request.loginId(), request.password(), request.rememberMe(), response)));
    }

    // 세션이 아직 유효한지만 가볍게 확인한다. 이 경로는 SecurityConfig에서 인증을 요구하므로
    // 여기 도달했다는 것 자체가 유효하다는 뜻이고, 화면 전환마다 무거운 /users/me 전체 프로필을
    // 다시 조회하지 않게 한다.
    @GetMapping("/session")
    public ApiResponse<Void> checkSession() {
        return ApiResponse.ok(null);
    }

    // 현재 로그인 종료 및 인증 쿠키 삭제 요청 처리
    @DeleteMapping("/sessions/current")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);

        // 로그아웃시 행해져야하는 작업
        // 1) 세션정리
        // 희상님이 작업

        return ApiResponse.ok(null);
    }

}
