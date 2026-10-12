package com.kh.back.auth.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class LoginAttemptService {

    // 실패 카운트 저장 (1시간 후 자동 소멸)
    private final Cache<String, Integer> attemptsCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(10000)
            .build();

    // 일시 차단 시간 저장 (memberId -> 차단 해제 시각)
    private final ConcurrentHashMap<String, LocalDateTime> blockTimeMap = new ConcurrentHashMap<>();

    public void loginSucceeded(String memberId) {
        attemptsCache.invalidate(memberId);
        blockTimeMap.remove(memberId);
    }

    public int loginFailed(String memberId) {
        int attempts = attemptsCache.get(memberId, k -> 0) + 1;
        attemptsCache.put(memberId, attempts);
        return attempts;
    }

    // 필요할 때 외부에서 차단 시간을 걸 수 있는 전용 메서드 추가
    public void setBlockTime(String memberId, long minutes) {
        blockTimeMap.put(memberId, LocalDateTime.now().plusMinutes(minutes));
    }

    public long getRemainingBlockMinutes(String memberId) {
        LocalDateTime blockedUntil = blockTimeMap.get(memberId);
        if (blockedUntil == null) return 0;

        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(blockedUntil)) {
            blockTimeMap.remove(memberId);
            return 0;
        }

        return Duration.between(now, blockedUntil).toMinutes() + 1;
    }
}