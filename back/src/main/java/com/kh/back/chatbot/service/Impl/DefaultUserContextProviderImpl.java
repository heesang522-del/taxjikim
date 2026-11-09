package com.kh.back.chatbot.service.Impl;

import com.kh.back.chatbot.service.DefaultUserContextProvider;
import org.springframework.stereotype.Service;

@Service
public class DefaultUserContextProviderImpl
        implements DefaultUserContextProvider {

    @Override
    public String build() {
        return """
                현재 사용자 거래 내역, 소득, 세금 분석 결과는 제공되지 않았습니다.
                사용자 개인의 소득이나 납부·환급 예상액은 확인할 수 없습니다.
                제공되지 않은 사용자 정보를 추측하지 마세요.
                실제 거래 내역이나 분석 결과를 조회했다고 말하지 마세요.
                서비스 사용 방법과 일반적인 금융·세금 용어를 설명하세요.
                개인별 계산 결과가 필요한 질문에는 분석 결과 확인이 필요하다고 안내하세요.
                """;
    }
}