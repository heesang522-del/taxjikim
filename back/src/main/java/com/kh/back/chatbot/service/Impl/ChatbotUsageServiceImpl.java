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

    private static final int DAILY_LIMIT = 30;

    private static final ZoneId KOREA_ZONE =
            ZoneId.of("Asia/Seoul");

    private final ChatbotUsageMapper chatbotUsageMapper;

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
            CustomUserDetails principal
    ) {
        return reserveUsage(
                principal,
                LocalDate.now(KOREA_ZONE)
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