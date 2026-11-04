package com.kh.back.chatbot.mapper;

import com.kh.back.chatbot.dto.ChatbotConversationDto;
import com.kh.back.chatbot.dto.ChatbotMessageDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatbotMapper {

    List<ChatbotConversationDto> findConversations(
            @Param("userId") Long userId,
            @Param("conversationId") Long conversationId
    );

    int insertConversation(ChatbotConversationDto conversation);

    List<ChatbotMessageDto> findMessages(
            @Param("userId") Long userId,
            @Param("conversationId") Long conversationId
    );

    int updateConversation(
            @Param("userId") Long userId,
            @Param("conversationId") Long conversationId,
            @Param("revision") Long revision,
            @Param("title") String title
    );

    int insertMessage(
            @Param("conversationId") Long conversationId,
            @Param("role") String role,
            @Param("content") String content
    );

    int deleteConversation(
            @Param("userId") Long userId,
            @Param("conversationId") Long conversationId
    );

    int deleteMessages(
            @Param("conversationId") Long conversationId
    );

    int advanceConversationRevision(
            @Param("userId") Long userId,
            @Param("conversationId") Long conversationId,
            @Param("revision") Long revision,
            @Param("resetTitle") boolean resetTitle
    );

    int deleteExchangeMessages(
            @Param("conversationId") Long conversationId,
            @Param("questionId") Long questionId,
            @Param("answerId") Long answerId
    );
}