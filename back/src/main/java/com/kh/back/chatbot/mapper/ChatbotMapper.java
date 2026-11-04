package com.kh.back.chatbot.mapper;

import com.kh.back.chatbot.dto.ChatbotConversationDto;
import com.kh.back.chatbot.dto.ChatbotMessageDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatbotMapper {

    List<ChatbotConversationDto> findConversations(@Param("userId") Long userId,
                                                   @Param("conversationId") Long conversationId
    ); // db에서 사용자의 채팅방 정보를 조회

    int insertConversation(ChatbotConversationDto conversation); // 새 채팅방 정보를 db에 저장하는 메서드

    ChatbotConversationDto lockConversation(@Param("userId") Long userId,
                                            @Param("conversationId") Long conversationId
    ); // 사용자의 특정 채팅방을 조회하면서 해당 db행에 잠금을 거는 메서드, 같은 채팅방에 요청이 동시에 들어왔을 때, 두 요청이 모두 처리되는 것을 막기 위해서

    List<ChatbotMessageDto> findMessages(@Param("userId") Long userId,
                                         @Param("conversationId") Long conversationId
    ); // 특정 사용자의 채팅방에 저장된 질문 답변 목록을 db에서 조회하는 메서드

    int updateConversation(@Param("userId") Long userId,
                           @Param("conversationId") Long conversationId,
                           @Param("revision") Long revision,
                           @Param("title") String title
    ); // db에 저장된 채팅방의 제목 . 버전 마지막 수정시각을 갱신하는 메서드

    int insertMessage(ChatbotMessageDto message); // 메세지 한개를 db에 저장하는 메서드

    int completeQuestion(@Param("conversationId") Long conversationId,
                         @Param("messageId") Long messageId
    ); // 이미 저장한 질문의 상태를 변경하는 메서드, complete 완료 처리

    int failQuestion(@Param("conversationId") Long conversationId,
                     @Param("messageId") Long messageId
    ); // 실패처리

    int expirePendingQuestions(@Param("conversationId") Long conversationId,
                               @Param("timeoutSeconds") int timeoutSeconds
    ); // 너무 오래 처리중으로 남아있는 질문을 실패로 변경하는 메서드

    int deleteConversation(@Param("userId") Long userId,
                           @Param("conversationId") Long conversationId
    ); // 대화 삭제

}