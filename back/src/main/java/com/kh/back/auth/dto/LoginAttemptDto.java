package com.kh.back.auth.dto;

import java.time.OffsetDateTime;

// 서버 시각을 함께 전달해 PC 시계 차이 없이 남은 시간을 표시한다.
public record LoginAttemptDto(int failedAttempts, int remainingAttempts,
                              OffsetDateTime blockedUntil, OffsetDateTime serverTime,
                              boolean locked) {
    public boolean blocked() {
        return blockedUntil != null && blockedUntil.isAfter(serverTime);
    }
}
