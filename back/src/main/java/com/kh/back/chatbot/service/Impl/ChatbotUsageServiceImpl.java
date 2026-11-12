package com.kh.back.chatbot.service.Impl;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.chatbot.dto.ChatbotUsageDto;
import com.kh.back.chatbot.mapper.ChatbotUsageMapper;
import com.kh.back.chatbot.service.ChatbotUsageService;
import com.kh.back.common.exception.ApiException;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatbotUsageServiceImpl implements ChatbotUsageService {

    private static final int DAILY_LIMIT = 30;

    private static final int WINDOW_SECONDS = 10;
    private static final int MAX_REQUESTS = 30;
    private static final DefaultRedisScript<Long> COUNT_REQUEST = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private static final ZoneId KOREA_ZONE =
            ZoneId.of("Asia/Seoul");

    private final ChatbotUsageMapper chatbotUsageMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void checkReadRate(Long userId) {
        Long count;
        try {
            // 서버 인스턴스와 조회 API가 달라도 회원당 같은 횟수를 사용한다.
            count = redisTemplate.execute(
                    COUNT_REQUEST,
                    List.of("chatbot:read-limit:" + userId),
                    String.valueOf(WINDOW_SECONDS)
            );
        } catch (DataAccessException exception) {
            throw unavailable();
        }

        if (count == null) {
            throw unavailable();
        }
        if (count > MAX_REQUESTS) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "CHATBOT_READ_RATE_LIMIT",
                    "조회 요청이 많습니다. 최대 10초 후 다시 시도해주세요."
            );
        }
    }

    private ApiException unavailable() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "CHATBOT_READ_LIMIT_UNAVAILABLE",
                "현재 대화 기록을 조회할 수 없습니다. 잠시 후 다시 시도해주세요."
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ChatbotUsageDto getUsage(
            CustomUserDetails principal
    ) {
        LocalDate today = LocalDate.now(KOREA_ZONE);

        Integer count =
                chatbotUsageMapper.findUsageCount(
                        principal.getUserId(),
                        today
                );

        return buildUsage(
                today,
                count == null ? 0 : count
        );
    }

    @Override
    @Transactional
    public ChatbotUsageDto reserveUsage(
            CustomUserDetails principal,
            LocalDate usageDate
    ) {
        Long userId = principal.getUserId();

        chatbotUsageMapper.insertUsageIfAbsent(
                userId,
                usageDate
        );

        // 한도 검사와 예약 증가를 하나의 SQL로 처리한다.
        int updated =
                chatbotUsageMapper.increaseUsage(
                        userId,
                        usageDate,
                        DAILY_LIMIT
                );

        if (updated != 1) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "CHATBOT_LIMIT",
                    "오늘 질문 횟수("
                            + DAILY_LIMIT
                            + "회)를 모두 사용했습니다."
            );
        }

        Integer used =
                chatbotUsageMapper.findUsageCount(
                        userId,
                        usageDate
                );

        if (used == null) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "CHATBOT_USAGE_UNAVAILABLE",
                    "질문 사용량을 확인하지 못했습니다."
            );
        }

        return buildUsage(usageDate, used);
    }

    @Override
    @Transactional
    public void releaseQuestionUsage(
            Long userId,
            Long conversationId,
            Long questionId
    ) {
        int updated =
                chatbotUsageMapper.releaseQuestionUsage(
                        userId,
                        conversationId,
                        questionId
                );

        if (updated != 1) {
            // 호출한 기록 트랜잭션도 롤백해 실패 상태만 남지 않도록 한다.
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "CHATBOT_USAGE_RELEASE_FAILED",
                    "질문 횟수 복구를 확인하지 못했습니다."
            );
        }
    }

    private ChatbotUsageDto buildUsage(
            LocalDate usageDate,
            int used
    ) {
        return ChatbotUsageDto.builder()
                .usageDate(usageDate)
                .dailyLimit(DAILY_LIMIT)
                .used(used)
                .remaining(
                        Math.max(0, DAILY_LIMIT - used)
                )
                .build();
    }
}