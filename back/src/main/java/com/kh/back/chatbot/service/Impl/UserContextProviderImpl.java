package com.kh.back.chatbot.service.Impl;

import com.kh.back.chatbot.service.UserContextProvider;
import org.springframework.stereotype.Service;

@Service
public class UserContextProviderImpl implements UserContextProvider {

    @Override
    public String build(Long userId) {
        // 실제 사용자 거래·분석 조회가 연결되기 전까지 기본 안내만 제공한다.
        // 내부 사용자 번호는 AI에 전달하지 않는다.
        return """
                사용자는 로그인한 상태입니다.
                사용자 거래 내역, 소득, 세금 분석 결과는 제공되지 않았습니다.
                제공되지 않은 사용자 정보를 추측하지 마세요.
                실제 거래 내역이나 분석 결과를 조회했다고 말하지 마세요.
                """;
    }
}