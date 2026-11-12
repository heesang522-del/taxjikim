package com.kh.back.auth.dto;

public record AccountLookupRequestDto(String name, String email, String code) {
}
