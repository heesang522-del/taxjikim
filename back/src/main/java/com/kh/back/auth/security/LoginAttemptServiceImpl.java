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

/**
 * LoginAttemptService의 실제 처리 코드다. 아이디별 실패 기록을 Redis에 저장한다.
 * Redis의 실패 기록과 DB 회원의 LOCKED 상태는 별개이며, DB 상태 변경은 인증·관리자 코드가 담당한다.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptServiceImpl implements LoginAttemptService {
    // Redis에 명령을 보내고 결과를 받는 Spring 도구다.
    private final StringRedisTemplate redisTemplate;
    // 다른 Redis 데이터와 구분하기 위해 아이디 앞에 붙이는 저장 이름의 접두사다.
    private static final String PREFIX = "LOGIN_ATTEMPT:v2:";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    // 아래 문자열은 Java가 아니라 Redis에서 실행하는 Lua 코드다.
    // 조회 → 횟수 증가 → 차단 시간 저장을 한 번에 처리해 동시 요청이 같은 횟수를 덮어쓰지 않게 한다.
    // KEYS[1]은 기록을 저장할 이름, ARGV[1]은 Java에서 전달하는 작업 종류(read / fail)다.
    // count는 실패 횟수, untilAt은 차단 종료 시각이며, 기록이 없으면 각각 0으로 읽는다.
    // 시각은 Redis 서버 기준의 밀리초를 사용해 요청을 처리하는 서버의 시계 차이를 피한다.
    // 현재 정책: 6회 30초 → 7회 1분 → 8회 3분 → 9회 5분 → 10회 계정 잠금 대상.
    // fail 요청이면서 10회 미만이고 차단 시간이 끝난 경우에만 기록을 변경한다.
    // 따라서 차단 중 시도는 실패 횟수를 늘리거나 차단 시간을 연장하지 않는다.
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
        // read는 현재 기록만 읽고, Lua의 횟수 증가 부분은 실행하지 않는다.
        return execute(loginId, "read");
    }

    @Override
    public LoginAttemptDto loginFailed(String loginId) {
        // 비밀번호 검사 등에서 로그인 실패가 확인되면 fail 작업으로 기록을 갱신한다.
        return execute(loginId, "fail");
    }

    @Override
    public void loginSucceeded(String loginId) {
        // 자동 만료를 두지 않고 로그인 성공·관리자 해제 시에만 삭제한다.
        redisTemplate.delete(key(loginId));
    }

    private LoginAttemptDto execute(String loginId, String action) {
        // 저장 이름 한 개와 작업 종류를 Lua에 전달한다. 반환 순서는 실패 횟수, 종료 시각, 현재 시각이다.
        List<?> values = redisTemplate.execute(ATTEMPT_SCRIPT, List.of(key(loginId)), action);
        // 기록을 읽지 못한 상황을 실패 횟수 0회로 취급하지 않고 오류로 전달한다.
        if (values == null || values.size() != 3) {
            throw new IllegalStateException("로그인 실패 기록을 확인하지 못했습니다.");
        }
        // Redis가 돌려준 숫자를 Java에서 사용하는 int와 long 타입으로 변환한다.
        int count = ((Number) values.get(0)).intValue();
        long untilAt = ((Number) values.get(1)).longValue();
        long now = ((Number) values.get(2)).longValue();
        // 첫 차단(6회)까지 남은 횟수는 음수가 되지 않도록 하고, 종료된 차단 시각은 null로 전달한다.
        // 시각은 한국 시간으로 변환하며, 화면의 남은 시간 계산을 위해 서버 현재 시각도 함께 보낸다.
        // 10회 이상이면 잠금 대상으로 알린다. 이 메서드 자체가 DB 계정을 잠그는 것은 아니다.
        return new LoginAttemptDto(count, Math.max(0, 6 - count),
                untilAt > now ? Instant.ofEpochMilli(untilAt).atZone(SEOUL).toOffsetDateTime() : null,
                Instant.ofEpochMilli(now).atZone(SEOUL).toOffsetDateTime(), count >= 10);
    }

    private String key(String loginId) {
        // 앞뒤 공백과 영문 대소문자 차이로 같은 아이디의 실패 기록이 나뉘지 않게 한다.
        return PREFIX + loginId.trim().toLowerCase(Locale.ROOT);
    }
}
