package com.kh.back.auth.controller;

import com.kh.back.auth.dto.LoginRequestDto;
import com.kh.back.auth.dto.LoginResponse;
import com.kh.back.auth.service.AuthService;
import com.kh.back.auth.service.EmailVerificationService;
import com.kh.back.user.service.UserService;
import com.kh.back.user.service.UserService.AccountRestoreType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthApiController {

    private final UserService userService;
    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;

    /**
     * 💡 [REST API 로그인 엔드포인트]
     * 성공 시 Access Token 및 Cookie(Refresh Token) 반환
     * 실패 시 예외 메시지(DORMANT, LOCKED, BANNED 등)를 JSON 응답으로 전달
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequestDto requestDto,
            HttpServletResponse response) {
        try {
            LoginResponse loginResponse = authService.login(
                    requestDto.loginId(),
                    requestDto.password(),
                    requestDto.rememberMe(),
                    response
            );
            return ResponseEntity.ok(loginResponse);

        } catch (AuthenticationException e) {
            // 예외 메시지(DORMANT, LOCKED, BANNED 등)를 프론트엔드가 응답받아 분기 처리할 수 있도록 JSON 반환
            String errorType = e.getMessage();

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "error", errorType,
                    "message", getErrorMessageDetail(errorType)
            ));
        }
    }

    // 💡 [에러 메시지 매핑 헬퍼 메서드]
    private String getErrorMessageDetail(String errorType) {
        return switch (errorType) {
            case "DORMANT" -> "휴면 계정입니다. 계정 재활성화가 필요합니다.";
            case "DELETED" -> "탈퇴 대기 중인 계정입니다. 계정 복구를 진행해 주세요.";
            case "LOCKED" -> "보안을 위해 계정이 잠겼습니다. 이메일 해제 링크를 확인해 주세요.";
            case "BANNED" -> "운영 정책 위반으로 이용이 정지된 계정입니다.";
            default -> errorType;
        };
    }

    // 연결 안되어있는 코드 (토큰 재발급 확인용)
    @PostMapping("/reissue")
    public ResponseEntity<?> reissue(
            @CookieValue(name = "refreshToken", required = false)
            String refreshToken,
            HttpServletResponse response) {

        if (!StringUtils.hasText(refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "message", "Refresh Token이 존재하지 않습니다."
                    ));
        }

        try {
            String newAccessToken =
                    authService.reissue(refreshToken, response);

            return ResponseEntity.ok(Map.of(
                    "accessToken", newAccessToken,
                    "tokenType", "Bearer"
            ));

        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "code", "SESSION_REPLACED".equals(e.getMessage())
                                    ? "SESSION_REPLACED" : "REFRESH_REJECTED",
                            "message", "SESSION_REPLACED".equals(e.getMessage())
                                    ? "다른 기기에서 로그인했거나 현재 로그인이 종료되었습니다."
                                    : e.getMessage()
                    ));
        }
    }

    /* ================= 계정 복구 / 휴면 해제 API ================= */

    @PostMapping("/send-recovery-code")
    public ResponseEntity<?> sendRecoveryCode(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        try {
            emailVerificationService.sendVerificationCode(email);
            return ResponseEntity.ok(Map.of("success", true, "message", "인증번호가 발송되었습니다."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "메일 발송에 실패했습니다."));
        }
    }

    @PostMapping("/unlock-dormant")
    public ResponseEntity<String> unlockDormant(@RequestBody Map<String, String> request) {
        return processAccountRestore(request, AccountRestoreType.DORMANT);
    }

    @PostMapping("/restore-account")
    public ResponseEntity<String> restoreAccount(@RequestBody Map<String, String> request) {
        return processAccountRestore(request, AccountRestoreType.DELETE);
    }

    private ResponseEntity<String> processAccountRestore(
            Map<String, String> request,
            AccountRestoreType restoreType
    ) {
        String code = request.get("code");
        String loginId = request.get("loginId");
        String email = request.get("email");

        if (loginId == null || email == null) {
            return ResponseEntity.ok("EXPIRED");
        }

        try {
            boolean isCodeValid = emailVerificationService.verifyCode(email, code);
            if (isCodeValid) {
                userService.restoreAccountStatus(loginId, email, restoreType);
                return ResponseEntity.ok("SUCCESS");
            }
            return ResponseEntity.ok("FAIL");
        } catch (Exception e) {
            return ResponseEntity.ok("FAIL");
        }
    }
}
