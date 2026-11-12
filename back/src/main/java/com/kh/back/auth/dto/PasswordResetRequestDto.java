package com.kh.back.auth.dto;

public record PasswordResetRequestDto(String loginId, String email, String newPassword) {
}
