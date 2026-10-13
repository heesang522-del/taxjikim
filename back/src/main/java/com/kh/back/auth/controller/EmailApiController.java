package com.kh.back.auth.controller;

import com.kh.back.auth.service.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/email")
@RequiredArgsConstructor
public class EmailApiController {

    private final EmailVerificationService emailVerificationService;

    /* ================= 이메일 인증 공통 API ================= */
    // 1. 단순 이메일 인증번호 발송 (회원가입)
    @PostMapping("/send-code")
    public ResponseEntity<?> sendVerificationCode(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        try {
            emailVerificationService.sendVerificationCode(email);
            return ResponseEntity.ok(Map.of("success", true, "message", "인증번호가 발송되었습니다."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "메일 발송에 실패했습니다."));
        }
    }

    // 2. 인증번호 검증 API
    @PostMapping("/verify-code")
    public ResponseEntity<?> verifyCode(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String code = request.get("code");

        try {
            boolean isMatched = emailVerificationService.verifyCode(email, code);
            if (isMatched) {
                return ResponseEntity.ok(Map.of("success", true, "message", "인증에 성공하였습니다."));
            }
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "인증번호가 일치하지 않습니다."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}