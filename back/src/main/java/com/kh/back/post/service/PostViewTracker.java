package com.kh.back.post.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 게시글 조회수 하루 1회 제한을 Redis로 처리한다. "오늘 이미 봤는지"만 기억하면 되는 값이라
 * TTL로 자정 이후 자동 만료시키고, 별도 정리 배치를 두지 않는다. 실제 누적 조회수는
 * POSTS.VIEW_COUNT(MySQL)에 그대로 남으므로, 이 기록이 사라져도(예: Redis 재시작) 최악의 경우
 * 일부 글이 그날 조회수를 한 번 더 세는 정도이고 데이터 유실은 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostViewTracker {

    private final StringRedisTemplate redisTemplate;

    private static final String PREFIX = "POST_VIEW:v1:";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /**
     * 이 조회가 오늘 첫 조회면 true(= 조회수를 올려야 함), 오늘 이미 센 적 있으면 false를 돌려준다.
     * viewerKey는 호출부(PostServiceImpl)가 로그인 여부에 따라 "user:{userId}" 또는 "ip:{ip}"로 만든다.
     * Redis 장애 시에는 글 조회 자체를 막지 않도록 false로 처리하고 조용히 넘어간다(조회수는 부가 기능이다).
     */
    public boolean registerView(Long postId, String viewerKey) {
        try {
            Boolean firstToday = redisTemplate.opsForValue()
                    .setIfAbsent(key(postId, viewerKey), "1", secondsUntilMidnight());
            return Boolean.TRUE.equals(firstToday);
        } catch (RuntimeException e) {
            log.warn("조회수 중복 확인 실패: postId={}", postId, e);
            return false;
        }
    }

    // 다음 자정(한국 시간)까지 남은 시간만큼 TTL을 준다. 24시간 고정이 아니라 "오늘 중" 기준으로 하루 1회를 제한한다.
    private Duration secondsUntilMidnight() {
        ZonedDateTime now = ZonedDateTime.now(SEOUL);
        ZonedDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(SEOUL);
        return Duration.between(now, nextMidnight);
    }

    private String key(Long postId, String viewerKey) {
        return PREFIX + postId + ":" + viewerKey;
    }
}
