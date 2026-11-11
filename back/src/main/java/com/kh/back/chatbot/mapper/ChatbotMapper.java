package com.kh.back.chatbot.mapper;

import com.kh.back.chatbot.dto.ChatbotConversationDto;
import com.kh.back.chatbot.dto.ChatbotMessageDto;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatbotMapper {

    List<ChatbotConversationDto> findConversations( @Param("userId") Long userId,
                                                    @Param("conversationId") Long conversationId
    ); // db에서 사용자의 대화방 목록을 조회

    int insertConversation(
            ChatbotConversationDto conversation
    ); // 새 대화방 정보를 db에 저장


    Long lockConversationOwner( @Param("userId") Long userId
    ); // 대화방을 만들 사용자의 db행을 조회하고 잠금


    int countActiveConversations( @Param("userId") Long userId
    ); // 해당 사용자의 삭제되지 않은 대화방 개수를 조회

    ChatbotConversationDto lockConversation(@Param("userId") Long userId,
                                            @Param("conversationId") Long conversationId
    ); // 사용자 소유의 특정 대화방을 조회하면서 해당 대화방의 db행을 잠금

    List<ChatbotMessageDto> findMessages(@Param("userId") Long userId,
                                         @Param("conversationId") Long conversationId
    ); // 해당 사용자의 특정 대화방에 저장된 질문 답변 목록을 조회

    int updateConversation(@Param("userId") Long userId,
                           @Param("conversationId") Long conversationId,
                           @Param("revision") Long revision,
                           @Param("title") String title
    ); // 대화방의 제목 버전 마지막 수정 시각을 갱신

    int renameConversation(
            @Param("userId") Long userId,
            @Param("conversationId") Long conversationId,
            @Param("title") String title
    );

    int insertMessage(
            ChatbotMessageDto message
    ); // 사용자 질문이나 ai 답변한개를 db 에 저장

    int completeQuestion(@Param("conversationId") Long conversationId,
                         @Param("messageId") Long messageId
    ); // 특정 사용자 질문의 처리 상태를 pending 에서 completed로 변경하는 메서드

    int failQuestion(@Param("conversationId") Long conversationId,
                     @Param("messageId") Long messageId
    ); // 특정 사용자 질문의 상태를 pending에서 failed로 변경하는 메서드

    int expirePendingQuestions(@Param("conversationId") Long conversationId,
                               @Param("timeoutSeconds") int timeoutSeconds
    );  // 특정 대화방에서 너무 오래 pending 상태로 남아 있는 질문들을 failed로 변경하는 메서드

    int deleteConversation(@Param("userId") Long userId,
                           @Param("conversationId") Long conversationId
    );
} // 해당 사용자의 특정 대화방을 논리 삭제하는 메서드
