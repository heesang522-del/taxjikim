package com.kh.back.chatbot.service;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.chatbot.dto.ChatbotConversationDto.ConversationResponse;
import com.kh.back.chatbot.dto.ChatbotMessageDto.MessageResponse;
import com.kh.back.chatbot.dto.ChatbotReply;

import java.util.List;
import java.util.function.Consumer;

public interface ChatbotService {

    List<ConversationResponse> getConversations(
            CustomUserDetails principal
    );

    ConversationResponse createConversation(
            CustomUserDetails principal
    );

    List<MessageResponse> getMessages(
            CustomUserDetails principal,
            Long conversationId
    );

    ChatbotReply ask(
            CustomUserDetails principal,
            Long conversationId,
            String message
    );

    ChatbotReply askStream(
            CustomUserDetails principal,
            Long conversationId,
            String message,
            Consumer<String> onDelta
    );

    void deleteConversation(
            CustomUserDetails principal,
            Long conversationId
    );
}