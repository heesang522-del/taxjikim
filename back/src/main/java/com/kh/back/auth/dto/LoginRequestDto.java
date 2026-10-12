package com.kh.back.auth.dto;

public record LoginRequestDto(
        String memberId,
        String memberPassword,
        boolean rememberMe
) {
}
