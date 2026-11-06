package com.kh.back.chatbot.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

@Mapper
public interface ChatbotUsageMapper {

    int insertUsageIfAbsent(@Param("userId") Long userId,
                            @Param("usageDate") LocalDate usageDate
    ); // 해당 사용자의 해당 날짜 사용량 기록이 없으면 새로 만드는  메서드

    int increaseUsage(@Param("userId") Long userId,
                      @Param("usageDate") LocalDate usageDate,
                      @Param("dailyLimit") int dailyLimit
    ); // 해당 사용자의 하루 질문 횟수가 한도보다 적을떄 사용횟수를 1증가 시키는 메서드

    Integer findUsageCount(@Param("userId") Long userId,
                           @Param("usageDate") LocalDate usageDate
    );  // 특정 사용자가 특정 날짜에 사용한 챗봇 질문 횟수를 db에서 조회하는 메서드
}