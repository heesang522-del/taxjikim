package com.kh.back.chatbot.service;

import com.kh.back.chatbot.dto.ChatbotConversationDto.ConversationResponse;
import com.kh.back.chatbot.dto.ChatbotMessageDto;
import lombok.Builder;

import java.util.List;

public interface ChatbotHistoryService {

    List<ConversationResponse> getConversations(Long userId);

    ConversationResponse createConversation(Long userId);

    ConversationSnapshot getSnapshot(
            Long userId,
            Long conversationId
    );

    void saveExchange(
            Long userId,
            Long conversationId,
            Long revision,
            String question,
            String reply
    );

    void deleteConversation(
            Long userId,
            Long conversationId
    );

    void deleteLatestExchange(
            Long userId,
            Long conversationId,
            Long messageId
    );

    @Builder
    record ConversationSnapshot(
            Long revision,
            List<ChatbotMessageDto> messages
    ) {
    }
}