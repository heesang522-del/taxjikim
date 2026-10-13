package com.kh.back.auth.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class EmailVerificationDto {
    private Long id;
    private String email;
    private String code;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private boolean isVerified;
}
