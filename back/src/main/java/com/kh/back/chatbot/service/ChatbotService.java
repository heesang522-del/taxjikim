package com.kh.back.chatbot.service;

import com.kh.back.chatbot.dto.ChatbotMessageDto;
import com.kh.back.chatbot.dto.ChatbotReply;

import java.util.List;

public interface ChatbotService {

    // 사용자의 질문에 답변한다.
    ChatbotReply ask(Long userId, String message);

    // 해당 사용자의 대화 기록을 조회한다.
    List<ChatbotMessageDto> getHistory(Long userId);

    // 해당 사용자의 대화 기록을 삭제한다.
    void clearHistory(Long userId);
}