package com.kh.back.chatbot.service.Impl;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.chatbot.dto.ChatbotConversationDto;
import com.kh.back.chatbot.dto.ChatbotConversationDto.ConversationResponse;
import com.kh.back.chatbot.dto.ChatbotMessageDto;
import com.kh.back.chatbot.dto.ChatbotUsageDto;
import com.kh.back.chatbot.mapper.ChatbotMapper;
import com.kh.back.chatbot.service.ChatbotHistoryService;
import com.kh.back.chatbot.service.ChatbotUsageService;
import com.kh.back.chatbot.validator.ChatbotValidator;
import com.kh.back.common.exception.ApiException;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatbotHistoryServiceImpl implements ChatbotHistoryService {

    private static final int MAX_CONVERSATIONS = 10;

    private final ChatbotMapper chatbotMapper;
    private final ChatbotUsageService chatbotUsageService;
    private final ChatbotValidator chatbotValidator;

    // 운영 정책값이며 application.yml에서 조정할 수 있다.
    @Value("${chatbot.pending-timeout-seconds:300}")
    private int pendingTimeoutSeconds;

    @Override
    @Transactional(readOnly = true)
    public List<ConversationResponse> getConversations(Long userId) {
        return chatbotMapper.findConversations(userId, null)
                .stream()
                .map(ChatbotConversationDto::toResponse)
                .toList();
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ConversationResponse createConversation(Long userId) {
        // 같은 사용자의 생성 요청을 순서대로 처리하기 위해 사용자 행을 잠근다.
        Long lockedUserId =
                chatbotMapper.lockConversationOwner(userId);

        if (lockedUserId == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "USER_NOT_FOUND",
                    "사용자를 찾을 수 없습니다."
            );
        }

        // 잠금을 얻은 뒤 최신 활성 대화방 개수를 확인한다.
        int conversationCount =
                chatbotMapper.countActiveConversations(userId);

        if (conversationCount >= MAX_CONVERSATIONS) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CHATBOT_CONVERSATION_LIMIT",
                    "대화방은 최대 10개까지 만들 수 있습니다. 기존 대화를 삭제해주세요."
            );
        }

        ChatbotConversationDto conversation =
                ChatbotConversationDto.builder()
                        .userId(userId)
                        .title("새 대화")
                        .build();

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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public QuestionReservation beginQuestion(
            CustomUserDetails principal,
            Long conversationId,
            String question
    ) {
        Long userId = principal.getUserId();

        // 동일 대화방의 질문 상태가 동시에 변경되지 않도록 잠근다.
        ChatbotConversationDto conversation =
                requireLockedConversation(userId, conversationId);

        // 오래 남아 있는 처리 중 질문을 실패 상태로 정리한다.
        chatbotMapper.expirePendingQuestions(
                conversationId,
                Math.max(1, pendingTimeoutSeconds)
        );

        List<ChatbotMessageDto> messages =
                chatbotMapper.findMessages(userId, conversationId);

        boolean hasPending = messages.stream()
                .anyMatch(message ->
                        "PENDING".equals(message.getStatus())
                );

        if (hasPending) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CHATBOT_REQUEST_IN_PROGRESS",
                    "이 대화방에서 답변을 생성 중입니다. 잠시 후 다시 시도해주세요."
            );
        }

        // 질문 저장과 사용량 차감을 같은 트랜잭션에서 처리한다.
        ChatbotUsageDto usage =
                chatbotUsageService.reserveUsage(principal);

        String title = buildTitle(question);

        int updated = chatbotMapper.updateConversation(
                userId,
                conversationId,
                conversation.getRevision(),
                title
        );

        requireUpdated(updated);

        ChatbotMessageDto pendingQuestion =
                ChatbotMessageDto.builder()
                        .conversationId(conversationId)
                        .role("USER")
                        .content(question)
                        .status("PENDING")
                        .build();

        if (chatbotMapper.insertMessage(pendingQuestion) != 1) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "CHATBOT_QUESTION_SAVE_FAILED",
                    "질문을 저장하지 못했습니다."
            );
        }

        // 실패하거나 처리 중인 질문은 AI의 이전 대화에서 제외한다.
        List<ChatbotMessageDto> completedHistory =
                messages.stream()
                        .filter(message ->
                                "COMPLETED".equals(message.getStatus())
                        )
                        .toList();

        return QuestionReservation.builder()
                .questionId(pendingQuestion.getMessageId())
                .usage(usage)
                .history(completedHistory)
                .build();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeQuestion(
            Long userId,
            Long conversationId,
            Long questionId,
            String reply
    ) {
        ChatbotConversationDto conversation =
                requireLockedConversation(userId, conversationId);

        // 질문 완료 처리와 답변 저장은 함께 성공하거나 함께 롤백된다.
        int completed = chatbotMapper.completeQuestion(
                conversationId,
                questionId
        );

        if (completed != 1) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CHATBOT_QUESTION_CHANGED",
                    "질문 상태가 변경되었습니다. 대화를 다시 조회해주세요."
            );
        }

        ChatbotMessageDto answer =
                ChatbotMessageDto.builder()
                        .conversationId(conversationId)
                        .role("ASSISTANT")
                        .content(reply)
                        .status("COMPLETED")
                        .build();

        if (chatbotMapper.insertMessage(answer) != 1) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "CHATBOT_ANSWER_SAVE_FAILED",
                    "답변을 저장하지 못했습니다."
            );
        }

        requireUpdated(
                chatbotMapper.updateConversation(
                        userId,
                        conversationId,
                        conversation.getRevision(),
                        conversation.getTitle()
                )
        );
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failQuestion(
            Long userId,
            Long conversationId,
            Long questionId
    ) {
        // 실패한 작업과 별도 트랜잭션으로 질문 상태를 기록한다.
        ChatbotConversationDto conversation =
                chatbotMapper.lockConversation(
                        userId,
                        conversationId
                );

        if (conversation == null) {
            return;
        }

        int failed = chatbotMapper.failQuestion(
                conversationId,
                questionId
        );

        // 실제로 실패 상태가 변경된 경우에만 대화방 버전을 갱신한다.
        if (failed == 1) {
            requireUpdated(
                    chatbotMapper.updateConversation(
                            userId,
                            conversationId,
                            conversation.getRevision(),
                            conversation.getTitle()
                    )
            );
        }
    }

    @Override
    @Transactional
    public void deleteConversation(
            Long userId,
            Long conversationId
    ) {
        requireLockedConversation(userId, conversationId);

        // 기존 SQL에 따라 대화방을 논리 삭제한다.
        int updated = chatbotMapper.deleteConversation(
                userId,
                conversationId
        );

        if (updated < 1) {
            throw conversationNotFound();
        }
    }

    private String buildTitle(String question) {
        // 마스킹된 첫 질문의 앞 40글자를 제목으로 사용한다.
        String title = question.replaceAll("\\s+", " ").strip();

        int titleLength = Math.min(
                40,
                title.codePointCount(0, title.length())
        );

        return title.substring(
                0,
                title.offsetByCodePoints(0, titleLength)
        );
    }

    private ChatbotConversationDto requireConversation(
            Long userId,
            Long conversationId
    ) {
        chatbotValidator.validateConversationId(conversationId);

        List<ChatbotConversationDto> conversations =
                chatbotMapper.findConversations(
                        userId,
                        conversationId
                );

        if (conversations.isEmpty()) {
            throw conversationNotFound();
        }

        return conversations.get(0);
    }

    private ChatbotConversationDto requireLockedConversation(
            Long userId,
            Long conversationId
    ) {
        chatbotValidator.validateConversationId(conversationId);

        // 사용자 번호와 대화방 번호를 함께 확인해 소유권을 검사한다.
        ChatbotConversationDto conversation =
                chatbotMapper.lockConversation(
                        userId,
                        conversationId
                );

        if (conversation == null) {
            throw conversationNotFound();
        }

        return conversation;
    }

    private void requireUpdated(int updated) {
        if (updated != 1) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CHATBOT_CONVERSATION_CHANGED",
                    "대화가 변경되었습니다. 다시 조회해주세요."
            );
        }
    }

    private ApiException conversationNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "CHATBOT_CONVERSATION_NOT_FOUND",
                "대화를 찾을 수 없습니다."
        );
    }
}