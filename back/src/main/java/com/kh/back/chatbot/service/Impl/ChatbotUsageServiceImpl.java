package com.kh.back.chatbot.service.Impl;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.chatbot.dto.ChatbotUsageDto;
import com.kh.back.chatbot.mapper.ChatbotUsageMapper;
import com.kh.back.chatbot.service.ChatbotUsageService;
import com.kh.back.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class ChatbotUsageServiceImpl implements ChatbotUsageService {

    // 금융 기준이 아닌 챗봇 운영 정책이다.
    private static final int DAILY_LIMIT = 30;

    private static final ZoneId KOREA_ZONE =
            ZoneId.of("Asia/Seoul");

    private final ChatbotUsageMapper chatbotUsageMapper;

    @Override
    @Transactional(readOnly = true)
    public ChatbotUsageDto getUsage(CustomUserDetails principal) {
        Long userId = requireUserId(principal);
        LocalDate today = LocalDate.now(KOREA_ZONE);

        Integer count = chatbotUsageMapper.findUsageCount(
                userId,
                today
        );

        // 오늘 사용 기록이 없으면 0회로 처리한다.
        int used = count == null ? 0 : count;

        return buildUsage(today, used);
    }

    @Override
    @Transactional
    public ChatbotUsageDto reserveUsage(CustomUserDetails principal) {
        Long userId = requireUserId(principal);

        // 한 요청에서는 동일한 한국 날짜를 기준으로 처리한다.
        LocalDate today = LocalDate.now(KOREA_ZONE);

        chatbotUsageMapper.insertUsageIfAbsent(
                userId,
                today
        );

        // 한도 검사와 증가를 하나의 SQL로 수행해 동시 요청을 보호한다.
        int updated = chatbotUsageMapper.increaseUsage(
                userId,
                today,
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

        Integer used = chatbotUsageMapper.findUsageCount(
                userId,
                today
        );

        if (used == null) {
            // 조회 실패 시 같은 트랜잭션의 횟수 증가도 롤백한다.
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "CHATBOT_USAGE_UNAVAILABLE",
                    "질문 사용량을 확인하지 못했습니다."
            );
        }

        return buildUsage(today, used);
    }

    private ChatbotUsageDto buildUsage(
            LocalDate usageDate,
            int used
    ) {
        // 사용 한도보다 큰 값이 있어도 남은 횟수는 음수로 표시하지 않는다.
        return ChatbotUsageDto.builder()
                .usageDate(usageDate)
                .dailyLimit(DAILY_LIMIT)
                .used(used)
                .remaining(Math.max(0, DAILY_LIMIT - used))
                .build();
    }

    private Long requireUserId(CustomUserDetails principal) {
        // 사용량 API가 직접 호출되는 경우에도 인증 정보를 검증한다.
        if (principal == null
                || principal.getUserId() == null
                || principal.getUserId() <= 0) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED",
                    "로그인이 필요합니다."
            );
        }

        return principal.getUserId();
    }
}