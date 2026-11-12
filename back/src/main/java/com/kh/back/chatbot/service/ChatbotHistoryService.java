package com.kh.back.chatbot.service;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.chatbot.dto.ChatbotConversationDto.ConversationResponse;
import com.kh.back.chatbot.dto.ChatbotMessageDto;
import com.kh.back.chatbot.dto.ChatbotUsageDto;

import lombok.Builder;

import java.util.List;

public interface ChatbotHistoryService {

    List<ConversationResponse> getConversations(Long userId);

    ConversationResponse createConversation(Long userId);

    ConversationResponse renameConversation(Long userId, Long conversationId, String title);

    void requireConversationOwnership(
            Long userId,
            Long conversationId
    );

    ConversationSnapshot getSnapshot(
            Long userId,
            Long conversationId
    );

    QuestionReservation beginQuestion(
            CustomUserDetails principal,
            Long conversationId,
            String question
    );

    void completeQuestion(
            Long userId,
            Long conversationId,
            Long questionId,
            String reply
    );

    void failQuestion(
            Long userId,
            Long conversationId,
            Long questionId
    );

    void deleteConversation(
            Long userId,
            Long conversationId
    );

    @Builder
    record ConversationSnapshot(
            Long revision,
            List<ChatbotMessageDto> messages
    ) {
    }

    @Builder
    record QuestionReservation(
            Long questionId,
            ChatbotUsageDto usage,
            List<ChatbotMessageDto> history
    ) {
    }
}
