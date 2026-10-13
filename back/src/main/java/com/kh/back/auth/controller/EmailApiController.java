package com.kh.back.auth.controller;

import com.kh.back.auth.dto.EmailVerificationRequestDto;
import com.kh.back.auth.service.EmailVerificationService;
import com.kh.back.common.dto.ApiResponse;
import com.kh.back.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/email-verifications")
@RequiredArgsConstructor
public class EmailApiController {
    private final EmailVerificationService emailVerificationService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> sendVerificationCode(@RequestBody EmailVerificationRequestDto request) {
        emailVerificationService.sendVerificationCode(request.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(null));
    }

    @PatchMapping
    public ApiResponse<Void> verifyCode(@RequestBody EmailVerificationRequestDto request) {
        if (!emailVerificationService.verifyCode(request.email(), request.code())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VERIFICATION_CODE_INVALID", "인증번호가 올바르지 않습니다.");
        }
        return ApiResponse.ok(null);
    }
}
