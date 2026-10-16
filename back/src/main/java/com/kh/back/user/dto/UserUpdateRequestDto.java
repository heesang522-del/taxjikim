package com.kh.back.user.dto;

public record UserUpdateRequestDto(String nickname, String email, String currentPassword, String newPassword) {
}
