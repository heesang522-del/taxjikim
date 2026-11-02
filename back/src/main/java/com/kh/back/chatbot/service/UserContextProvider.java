package com.kh.back.chatbot.service;

public interface UserContextProvider {

    // AI에게 제공할 사용자 관련 안내를 만든다.
    String build(Long userId);
}