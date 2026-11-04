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
    public List<ConversationResponse> getConversations(Long userId) {
        return chatbotMapper.findConversations(userId, null)
                .stream()
                .map(ChatbotConversationDto::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ConversationResponse createConversation(Long userId) {
        ChatbotConversationDto conversation =
                ChatbotConversationDto.builder()
                        .userId(userId)
                        .title("새 대화")
                        .build();

        // 생성된 ID로 DB 기본값까지 다시 조회한다.
        chatbotMapper.insertConversation(conversation);

        return requireConversation(
                userId,
                conversation.getConversationId()
        ).toResponse();
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
        // 대화 버전과 메시지를 동일한 시점 기준으로 조회한다.
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

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void deleteLatestExchange(
            Long userId,
            Long conversationId,
            Long messageId
    ) {
        if (messageId == null || messageId <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "메시지 번호가 올바르지 않습니다."
            );
        }

        // 대화방 조회에서 사용자 소유권도 함께 확인한다.
        ChatbotConversationDto conversation =
                requireConversation(userId, conversationId);

        List<ChatbotMessageDto> messages =
                chatbotMapper.findMessages(userId, conversationId);

        boolean exists = messages.stream()
                .anyMatch(message ->
                        messageId.equals(message.getMessageId())
                );

        if (!exists) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "CHATBOT_MESSAGE_NOT_FOUND",
                    "메시지를 찾을 수 없습니다."
            );
        }

        if (messages.size() < 2) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CHATBOT_EXCHANGE_INVALID",
                    "질문과 답변을 함께 확인할 수 없습니다."
            );
        }

        ChatbotMessageDto question =
                messages.get(messages.size() - 2);

        ChatbotMessageDto answer =
                messages.get(messages.size() - 1);

        // 이후 대화의 맥락을 유지하기 위해 마지막 질문·답변만 삭제한다.
        if (!"USER".equals(question.getRole())
                || !"ASSISTANT".equals(answer.getRole())
                || !messageId.equals(question.getMessageId())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CHATBOT_LATEST_EXCHANGE_ONLY",
                    "가장 최근 질문과 답변만 함께 삭제할 수 있습니다."
            );
        }

        // 모든 메시지를 삭제하면 대화방 제목도 초기화한다.
        boolean resetTitle = messages.size() == 2;

        // 조회 이후 대화가 바뀌었다면 삭제를 중단한다.
        // 버전을 증가시켜 삭제 전 기록을 참고한 AI 응답의 저장도 차단한다.
        int updated = chatbotMapper.advanceConversationRevision(
                userId,
                conversationId,
                conversation.getRevision(),
                resetTitle
        );

        if (updated != 1) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CHATBOT_CONVERSATION_CHANGED",
                    "대화가 변경되었습니다. 다시 불러와주세요."
            );
        }

        int deleted = chatbotMapper.deleteExchangeMessages(
                conversationId,
                question.getMessageId(),
                answer.getMessageId()
        );

        if (deleted != 2) {
            // 두 메시지가 함께 삭제되지 않으면 버전 갱신까지 롤백한다.
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CHATBOT_CONVERSATION_CHANGED",
                    "대화가 변경되었습니다. 다시 불러와주세요."
            );
        }
    }

    private ChatbotConversationDto requireConversation(
            Long userId,
            Long conversationId
    ) {
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