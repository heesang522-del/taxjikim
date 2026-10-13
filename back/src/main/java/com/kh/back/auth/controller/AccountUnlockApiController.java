package com.kh.back.auth.controller;

import com.kh.back.auth.dto.AccountUnlockRequestDto;
import com.kh.back.auth.service.AuthService;
import com.kh.back.common.dto.ApiResponse;
import com.kh.back.common.validation.InputValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/account-unlocks")
@RequiredArgsConstructor
public class AccountUnlockApiController {
    private final AuthService authService;

    @PostMapping
    public ApiResponse<Void> unlockAccount(@RequestBody AccountUnlockRequestDto request) {
        InputValidator.requireText("token", request.token());
        authService.unlockAccount(request.token());
        return ApiResponse.ok(null);
    }
}
