package com.kh.back.auth.dto;

import java.time.OffsetDateTime;

// 서버 시각을 함께 전달해 PC 시계 차이 없이 남은 시간을 표시한다.
public record LoginAttemptDto(
        int failedAttempts,          // Redis에 기록된 로그인 실패 횟수
        int remainingAttempts,       // 첫 일시 차단까지 남은 실패 횟수
        OffsetDateTime blockedUntil, // 일시 차단 종료 시각. 차단이 끝났거나 없으면 null
        OffsetDateTime serverTime,   // 남은 차단 시간을 계산할 기준인 Redis 서버의 현재 시각
        boolean locked) {            // 실패 횟수 기준 잠금 대상 여부. DB 회원 상태를 조회한 값은 아님
    public boolean blocked() {
        return blockedUntil != null && blockedUntil.isAfter(serverTime);
    }
}
