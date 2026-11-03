package com.kh.back.chatbot.service.Impl;

import com.kh.back.chatbot.dto.ChatbotConversationDto;
import com.kh.back.chatbot.dto.ChatbotConversationDto.ConversationResponse;
import com.kh.back.chatbot.dto.ChatbotMessageDto;
import com.kh.back.chatbot.mapper.ChatbotMapper;
import com.kh.back.chatbot.service.ChatbotHistoryService;
import com.kh.back.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatbotHistoryServiceImpl implements ChatbotHistoryService {

    private final ChatbotMapper chatbotMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ConversationResponse> getConversations(Long userId) { // 사용자 id를 받아 응답용 대화방 목록을 반환하는 메서드
        return chatbotMapper.findConversations(userId, null) //findConversations(userID, null) : 해당 사용자의 전체 대화방 조회
                .stream() // 조회한 목록을 하나씩 변환할수 있는 처리 흐름 생성
                .map(ChatbotConversationDto::toResponse) // 각 dto의 toresponse()를 호출해 응답 객체로 변환
                .toList(); // 변환 결과를 목록으로 모음
    }

    @Override
    @Transactional // 새 대화방을 db에 저장하기 전에 사용자 id와 기본 제목을 담은 객체를 만드는 부분
    public ConversationResponse createConversation(Long userId) { // 사용자 id를 받아 새 대화방을 만들고, 결과를 conversationResponse 타입으로 반환하는 메서드
        ChatbotConversationDto conversation = // 생성한 dto를 보관할 변수 선언
                ChatbotConversationDto.builder()  // dto 생성 준비
                        .userId(userId) // 전달받은 사용자 id 설정
                        .title("새 대화") // 기본 제목 설정
                        .build(); // 설정한 값으로 dto 객체 생성

        // 생성된 ID를 이용해 DB에서 설정한 기본값까지 다시 조회한다.
        chatbotMapper.insertConversation(conversation); // 저장하면서 db가 자동 생성한 conversationId를 dto에 채우도록 설정 ( mapper)

        return requireConversation(
                userId,
                conversation.getConversationId() // 앞에서 db에 저장할떄 자동 생성되어 dto에 채워진 대화방 id를 가져옴
        ).toResponse(); // 조회한 chatbotConversationDto 를 화면에 전달할
    }

    @Override
    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ
    )
    public ConversationSnapshot getSnapshot(
            Long userId,
            Long conversationId
    ) {
        // 대화 버전과 메시지를 동일한 시점의 기록에서 조회한다.
        ChatbotConversationDto conversation =
                requireConversation(userId, conversationId);

        List<ChatbotMessageDto> messages =
                chatbotMapper.findMessages(userId, conversationId);

        return ConversationSnapshot.builder()
                .revision(conversation.getRevision())
                .messages(messages)
                .build();
    }

    @Override
    @Transactional
    public void saveExchange(
            Long userId,
            Long conversationId,
            Long revision,
            String question,
            String reply
    ) {
        // 마스킹된 첫 질문의 앞 40글자를 대화방 제목으로 사용한다.
        String title = question.replaceAll("\\s+", " ").strip();

        int titleLength = Math.min(
                40,
                title.codePointCount(0, title.length())
        );

        title = title.substring(
                0,
                title.offsetByCodePoints(0, titleLength)
        );

        int updated = chatbotMapper.updateConversation(
                userId,
                conversationId,
                revision,
                title
        );

        if (updated != 1) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CHATBOT_CONVERSATION_CHANGED",
                    "대화가 변경되거나 삭제되었습니다. 다시 불러와주세요."
            );
        }

        // 질문과 답변을 함께 저장하고 실패하면 전체 롤백한다.
        chatbotMapper.insertMessage(
                conversationId,
                "USER",
                question
        );

        chatbotMapper.insertMessage(
                conversationId,
                "ASSISTANT",
                reply
        );
    }

    @Override
    @Transactional
    public void deleteConversation(
            Long userId,
            Long conversationId
    ) {
        // 대화방의 존재 여부와 소유권을 확인한다.
        requireConversation(userId, conversationId);

        int deleted = chatbotMapper.deleteConversation(
                userId,
                conversationId
        );

        if (deleted != 1) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "CHATBOT_CONVERSATION_NOT_FOUND",
                    "대화를 찾을 수 없습니다."
            );
        }

        // 대화방과 메시지를 같은 트랜잭션에서 논리 삭제한다.
        chatbotMapper.deleteMessages(conversationId);
    }

    private ChatbotConversationDto requireConversation(
            Long userId,
            Long conversationId
    ) {
        // null이 전체 목록 조회로 처리되지 않도록 단건 입력을 검사한다.
        if (conversationId == null || conversationId <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "대화방 번호가 올바르지 않습니다."
            );
        }

        List<ChatbotConversationDto> conversations =
                chatbotMapper.findConversations(
                        userId,
                        conversationId
                );

        if (conversations.isEmpty()) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "CHATBOT_CONVERSATION_NOT_FOUND",
                    "대화를 찾을 수 없습니다."
            );
        }

        return conversations.get(0);
    }
}