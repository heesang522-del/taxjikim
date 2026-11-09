package com.kh.back.chatbot.service;

public interface DefaultUserContextProvider {

    // 실제 사용자 데이터가 없을 때 사용할 기본 안내를 반환한다.
    String build();
}