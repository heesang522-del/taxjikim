package com.kh.back.chatbot.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatbotConfig {

    @Bean
    public ChatMemory chatMemory() {
        // 질문과 답변을 합쳐 최근 10개 메시지를 서버 메모리에 보관한다.
        // 서버를 재시작하면 기록이 사라진다.
        return MessageWindowChatMemory.builder()
                .maxMessages(10)
                .build();
    }
}