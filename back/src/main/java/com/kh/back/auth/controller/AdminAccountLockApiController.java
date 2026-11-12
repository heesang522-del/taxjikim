package com.kh.back.auth.controller;

import com.kh.back.auth.dto.LockedAccountDto;
import com.kh.back.auth.service.AccountLockService;
import com.kh.back.common.dto.ApiResponse;
import com.kh.back.common.validation.InputValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/account-locks")
@RequiredArgsConstructor
public class AdminAccountLockApiController {
    private final AccountLockService accountLockService;

    @GetMapping
    public ApiResponse<List<LockedAccountDto>> list() {
        return ApiResponse.ok(accountLockService.getLockedAccounts());
    }

    @DeleteMapping("/{loginId}")
    public ApiResponse<Void> unlock(@PathVariable String loginId) {
        InputValidator.requireText("loginId", loginId);
        accountLockService.unlockAccount(loginId);
        return ApiResponse.ok(null);
    }
}
