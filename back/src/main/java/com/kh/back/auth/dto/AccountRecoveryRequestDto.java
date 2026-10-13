package com.kh.back.auth.dto;

public record AccountRecoveryRequestDto(String loginId, String email, String code) {
}
