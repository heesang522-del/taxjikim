package com.kh.back.auth.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class LoginUnlockTokenServiceImpl implements LoginUnlockTokenService {

    // 15분 후 자동 만료되는 토큰 저장소 (Key: UUID 토큰, Value: loginId)
    private final Cache<String, String> unlockTokenCache = Caffeine.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .maximumSize(10000)
            .build();

    /**
     * 계정 잠금 해제용 토큰 생성 및 저장
     */
    @Override
    public String createUnlockToken(String loginId) {
        String token = UUID.randomUUID().toString();
        unlockTokenCache.put(token, loginId);
        return token;
    }

    /**
     * 토큰 검증 및 loginId 추출
     */
    @Override
    public String getLoginIdByToken(String token) {
        return unlockTokenCache.getIfPresent(token);
    }

    /**
     * 사용된 토큰 즉시 삭제
     */
    @Override
    public void invalidateToken(String token) {
        unlockTokenCache.invalidate(token);
    }
}
