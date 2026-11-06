package com.kh.back.chatbot.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

@Mapper
public interface ChatbotUsageMapper {

    int insertUsageIfAbsent(
            @Param("userId") Long userId,
            @Param("usageDate") LocalDate usageDate
    );

    int increaseUsage(
            @Param("userId") Long userId,
            @Param("usageDate") LocalDate usageDate,
            @Param("dailyLimit") int dailyLimit
    );

    Integer findUsageCount(
            @Param("userId") Long userId,
            @Param("usageDate") LocalDate usageDate
    );

    int releaseQuestionUsage(
            @Param("userId") Long userId,
            @Param("conversationId") Long conversationId,
            @Param("questionId") Long questionId
    );
}