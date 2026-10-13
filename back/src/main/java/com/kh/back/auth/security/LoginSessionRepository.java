package com.kh.back.auth.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class LoginSessionRepository {
    public enum RotationResult {
        SUCCESS,
        SESSION_ENDED,
        TOKEN_REUSED
    }

    private final StringRedisTemplate redisTemplate;
    // 회원 고유 번호로 로그인 세션을 구분한다. 로그인 아이디와 혼용하지 않는다.
    private static final String PREFIX = "LOGIN:user-id:";
    private static final DefaultRedisScript<Long> LOGIN_SCRIPT = new DefaultRedisScript<>("""
            redis.call('DEL', KEYS[1])
            redis.call('HSET', KEYS[1], 'sid', ARGV[1], 'jti', ARGV[2])
            redis.call('PEXPIRE', KEYS[1], ARGV[3])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>("""
            local sid = redis.call('HGET', KEYS[1], 'sid')
            if not sid or sid ~= ARGV[1] then return -1 end
            local jti = redis.call('HGET', KEYS[1], 'jti')
            if not jti or jti == '' or jti ~= ARGV[2] then
                redis.call('DEL', KEYS[1])
                return 0
            end
            redis.call('HSET', KEYS[1], 'jti', ARGV[3])
            redis.call('PEXPIRE', KEYS[1], ARGV[4])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> LOGOUT_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[1], 'sid') ~= ARGV[1] then return 0 end
            return redis.call('DEL', KEYS[1])
            """, Long.class);

    public void replaceCurrentSession(Long userId, String sessionId, String jti, long timeoutMillis) {
        requirePositiveTimeout(timeoutMillis);
        redisTemplate.execute(LOGIN_SCRIPT, List.of(PREFIX + userId),
                sessionId, jti == null ? "" : jti, Long.toString(timeoutMillis));
    }
    public boolean isCurrentSession(Long userId, String sessionId) {
        return sessionId != null && sessionId.equals(redisTemplate.opsForHash().get(PREFIX + userId, "sid"));
    }
    public RotationResult rotateRefreshToken(Long userId, String sessionId, String currentJti,
                                   String newJti, long timeoutMillis) {
        requirePositiveTimeout(timeoutMillis);
        Long result = redisTemplate.execute(ROTATE_SCRIPT, List.of(PREFIX + userId),
                sessionId, currentJti, newJti, Long.toString(timeoutMillis));
        if (result == null) throw new IllegalStateException("Redis 세션 확인에 실패했습니다.");
        // Lua의 숫자 결과는 저장소 내부에서만 해석합니다.
        return switch (result.intValue()) {
            case 1 -> RotationResult.SUCCESS;
            case -1 -> RotationResult.SESSION_ENDED;
            case 0 -> RotationResult.TOKEN_REUSED;
            default -> throw new IllegalStateException("알 수 없는 Redis 토큰 교체 결과입니다.");
        };
    }
    public void endSessionIfCurrent(Long userId, String sessionId) {
        if (sessionId != null) redisTemplate.execute(LOGOUT_SCRIPT, List.of(PREFIX + userId), sessionId);
    }
    // 회원 탈퇴 등 계정 전체의 로그인 종료에 사용합니다.
    public void revokeCurrentSession(Long userId) { redisTemplate.delete(PREFIX + userId); }
    private void requirePositiveTimeout(long timeoutMillis) {
        if (timeoutMillis <= 0) throw new IllegalArgumentException("만료 시간은 양수여야 합니다.");
    }
}
