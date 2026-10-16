package com.kh.back.auth.security;

import com.kh.back.auth.dto.LoginAttemptDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LoginAttemptServiceImpl implements LoginAttemptService {
    private final StringRedisTemplate redisTemplate;
    private static final String PREFIX = "LOGIN_ATTEMPT:v2:";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    // 확인·증가·차단 설정을 Redis에서 한 번에 실행한다. 차단 중에는 값을 갱신하지 않는다.
    private static final DefaultRedisScript<List> ATTEMPT_SCRIPT = new DefaultRedisScript<>("""
            local time = redis.call('TIME')
            local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            local count = tonumber(redis.call('HGET', KEYS[1], 'count') or '0')
            local untilAt = tonumber(redis.call('HGET', KEYS[1], 'until') or '0')
            if ARGV[1] == 'fail' and count < 10 and untilAt <= now then
                count = count + 1
                local durations = {[6]=30000, [7]=60000, [8]=180000, [9]=300000}
                untilAt = durations[count] and now + durations[count] or 0
                redis.call('HSET', KEYS[1], 'count', count, 'until', untilAt)
            end
            return {count, untilAt, now}
            """, List.class);

    @Override
    public LoginAttemptDto getAttempt(String loginId) {
        return execute(loginId, "read");
    }

    @Override
    public LoginAttemptDto loginFailed(String loginId) {
        return execute(loginId, "fail");
    }

    @Override
    public void loginSucceeded(String loginId) {
        // 자동 만료를 두지 않고 로그인 성공·관리자 해제 시에만 삭제한다.
        redisTemplate.delete(key(loginId));
    }

    private LoginAttemptDto execute(String loginId, String action) {
        List<?> values = redisTemplate.execute(ATTEMPT_SCRIPT, List.of(key(loginId)), action);
        if (values == null || values.size() != 3) {
            throw new IllegalStateException("로그인 실패 기록을 확인하지 못했습니다.");
        }
        int count = ((Number) values.get(0)).intValue();
        long untilAt = ((Number) values.get(1)).longValue();
        long now = ((Number) values.get(2)).longValue();
        return new LoginAttemptDto(count, Math.max(0, 6 - count),
                untilAt > now ? Instant.ofEpochMilli(untilAt).atZone(SEOUL).toOffsetDateTime() : null,
                Instant.ofEpochMilli(now).atZone(SEOUL).toOffsetDateTime(), count >= 10);
    }

    private String key(String loginId) {
        return PREFIX + loginId.trim().toLowerCase(Locale.ROOT);
    }
}
