package com.kh.back.auth.dto;

public record LoginRequestDto(
        String loginId,
        String password,
        boolean rememberMe
) {
}
