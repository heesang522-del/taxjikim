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
public class LoginAttemptServiceImpl implements LoginAttemptService {

    // 실패 카운트 저장 (1시간 후 자동 소멸)
    private final Cache<String, Integer> attemptsCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(10000)
            .build();

    // 일시 차단 시간 저장 (loginId -> 차단 해제 시각)
    private final ConcurrentHashMap<String, LocalDateTime> blockTimeMap = new ConcurrentHashMap<>();

    @Override
    public void loginSucceeded(String loginId) {
        attemptsCache.invalidate(loginId);
        blockTimeMap.remove(loginId);
    }

    @Override
    public int loginFailed(String loginId) {
        int attempts = attemptsCache.get(loginId, k -> 0) + 1;
        attemptsCache.put(loginId, attempts);
        return attempts;
    }

    // 필요할 때 외부에서 차단 시간을 걸 수 있는 전용 메서드 추가
    @Override
    public void setBlockTime(String loginId, long minutes) {
        blockTimeMap.put(loginId, LocalDateTime.now().plusMinutes(minutes));
    }

    @Override
    public long getRemainingBlockMinutes(String loginId) {
        LocalDateTime blockedUntil = blockTimeMap.get(loginId);
        if (blockedUntil == null) return 0;

        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(blockedUntil)) {
            blockTimeMap.remove(loginId);
            return 0;
        }

        return Duration.between(now, blockedUntil).toMinutes() + 1;
    }
}
