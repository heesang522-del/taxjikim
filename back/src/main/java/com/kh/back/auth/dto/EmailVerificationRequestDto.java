package com.kh.back.auth.dto;

public record EmailVerificationRequestDto(String email, String code) {
}
