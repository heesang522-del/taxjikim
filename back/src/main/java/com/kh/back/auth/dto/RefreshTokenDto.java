package com.kh.back.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 반환용 래퍼 클래스 (또는 Record)
@Getter
@AllArgsConstructor
public class RefreshTokenDto {
    private String refreshToken;
    private String jti;
}
