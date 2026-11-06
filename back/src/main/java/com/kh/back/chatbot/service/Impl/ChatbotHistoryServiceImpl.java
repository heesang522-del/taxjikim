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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatbotHistoryServiceImpl implements ChatbotHistoryService {

    private static final int MAX_CONVERSATIONS = 10;

    private static final ZoneId KOREA_ZONE =
            ZoneId.of("Asia/Seoul");

    private final ChatbotMapper chatbotMapper;
    private final ChatbotUsageService chatbotUsageService;
    private final ChatbotValidator chatbotValidator;

    @Value("${chatbot.pending-timeout-seconds:300}")
    private int pendingTimeoutSeconds;

    @Override
    @Transactional(readOnly = true)
    public List<ConversationResponse> getConversations(
            Long userId
    ) {
        return chatbotMapper.findConversations(userId, null)
                .stream()
                .map(ChatbotConversationDto::toResponse)
                .toList();
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ConversationResponse createConversation(
            Long userId
    ) {
        Long lockedUserId =
                chatbotMapper.lockConversationOwner(userId);

        if (lockedUserId == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "USER_NOT_FOUND",
                    "사용자를 찾을 수 없습니다."
            );
        }

        int count =
                chatbotMapper.countActiveConversations(userId);

        if (count >= MAX_CONVERSATIONS) {
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
        ChatbotConversationDto conversation =
                requireConversation(
                        userId,
                        conversationId
                );

        List<ChatbotMessageDto> messages =
                chatbotMapper.findMessages(
                        userId,
                        conversationId
                );

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

        ChatbotConversationDto conversation =
                requireLockedConversation(
                        userId,
                        conversationId
                );

        LocalDateTime questionTime =
                LocalDateTime.now(KOREA_ZONE);

        LocalDateTime cutoff =
                questionTime.minusSeconds(
                        Math.max(1, pendingTimeoutSeconds)
                );

        List<ChatbotMessageDto> messages =
                chatbotMapper.findMessages(
                        userId,
                        conversationId
                );

        // 서버 중단 등으로 오래 남은 예약도 실패 확정과 함께 복구한다.
        for (ChatbotMessageDto item : messages) {
            if ("PENDING".equals(item.getStatus())
                    && item.getCreatedAt().isBefore(cutoff)) {
                failAndRelease(
                        userId,
                        conversationId,
                        item.getMessageId()
                );
            }
        }

        // 상태 정리 후 최신 메시지로 진행 중인 질문을 확인한다.
        messages = chatbotMapper.findMessages(
                userId,
                conversationId
        );

        boolean hasPending = messages.stream()
                .anyMatch(item ->
                        "PENDING".equals(item.getStatus())
                );

        if (hasPending) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CHATBOT_REQUEST_IN_PROGRESS",
                    "이 대화방에서 답변을 생성 중입니다. 잠시 후 다시 시도해주세요."
            );
        }

        // 메시지 날짜와 사용량 예약 날짜를 일치시킨다.
        ChatbotUsageDto usage =
                chatbotUsageService.reserveUsage(
                        principal,
                        questionTime.toLocalDate()
                );

        requireUpdated(
                chatbotMapper.updateConversation(
                        userId,
                        conversationId,
                        conversation.getRevision(),
                        buildTitle(question)
                )
        );

        ChatbotMessageDto pendingQuestion =
                ChatbotMessageDto.builder()
                        .conversationId(conversationId)
                        .role("USER")
                        .content(question)
                        .status("PENDING")
                        .createdAt(questionTime)
                        .build();

        if (chatbotMapper.insertMessage(pendingQuestion) != 1) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "CHATBOT_QUESTION_SAVE_FAILED",
                    "질문을 저장하지 못했습니다."
            );
        }

        List<ChatbotMessageDto> completedHistory =
                messages.stream()
                        .filter(item ->
                                "COMPLETED".equals(item.getStatus())
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
                requireLockedConversation(
                        userId,
                        conversationId
                );

        int completed =
                chatbotMapper.completeQuestion(
                        conversationId,
                        questionId
                );

        if (completed != 1) {
            // 취소 또는 실패한 질문에는 늦게 도착한 답변을 저장하지 않는다.
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
        ChatbotConversationDto conversation =
                chatbotMapper.lockConversation(
                        userId,
                        conversationId
                );

        if (conversation == null) {
            return;
        }

        boolean changed = failAndRelease(
                userId,
                conversationId,
                questionId
        );

        if (changed) {
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
        requireLockedConversation(
                userId,
                conversationId
        );

        List<ChatbotMessageDto> messages =
                chatbotMapper.findMessages(
                        userId,
                        conversationId
                );

        // 진행 중인 질문을 먼저 실패 확정하고 예약 횟수를 복구한다.
        for (ChatbotMessageDto item : messages) {
            if ("PENDING".equals(item.getStatus())) {
                failAndRelease(
                        userId,
                        conversationId,
                        item.getMessageId()
                );
            }
        }

        int updated =
                chatbotMapper.deleteConversation(
                        userId,
                        conversationId
                );

        if (updated < 1) {
            throw conversationNotFound();
        }
    }

    private boolean failAndRelease(
            Long userId,
            Long conversationId,
            Long questionId
    ) {
        int changed =
                chatbotMapper.failQuestion(
                        conversationId,
                        questionId
                );

        if (changed != 1) {
            return false;
        }

        // 상태 변경과 사용량 복구는 호출한 메서드의 동일 트랜잭션에 참여한다.
        chatbotUsageService.releaseQuestionUsage(
                userId,
                conversationId,
                questionId
        );

        return true;
    }

    private String buildTitle(String question) {
        String title =
                question.replaceAll("\\s+", " ").strip();

        int length = Math.min(
                40,
                title.codePointCount(0, title.length())
        );

        return title.substring(
                0,
                title.offsetByCodePoints(0, length)
        );
    }

    private ChatbotConversationDto requireConversation(
            Long userId,
            Long conversationId
    ) {
        chatbotValidator.validateConversationId(
                conversationId
        );

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
        chatbotValidator.validateConversationId(
                conversationId
        );

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