package com.kh.back.chatbot.service;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.chatbot.dto.ChatbotConversationDto.ConversationResponse;
import com.kh.back.chatbot.dto.ChatbotMessageDto.MessageResponse;
import com.kh.back.chatbot.dto.ChatbotReply;
import com.kh.back.chatbot.dto.QuestionTaskDto;

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

    // 검증된 질문을 받고 생성된 답변 조각을 onDelta로 전달한다.
    ChatbotReply ask(
            CustomUserDetails principal,
            Long conversationId,
            String question,
            Consumer<String> onDelta,
            QuestionTaskDto task
    );

    void deleteConversation(
            CustomUserDetails principal,
            Long conversationId
    );
}