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
    } // 해당 사용자의 채팅방 목록을 db에서 조회하고 , 응답용 객체 목록으로 변환해 반환하는 메서드

    @Override
    @Transactional
    public ConversationResponse createConversation(Long userId) {
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
    }   // 해당 사용자의 새 채팅방을 db에 저장하고, 생성된 채팅방 정보를 반환하는 메서드

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
    } // 채팅방의 버전과 메세지 목록을 묶어서 조회하는 메서드 , 이렇게 한번의 일관된 조회결과를 snapshot이라고 부름

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public QuestionReservation beginQuestion(
            CustomUserDetails principal,
            Long conversationId,
            String question
    ) {
        Long userId = principal.getUserId();

        ChatbotConversationDto conversation =
                requireLockedConversation(userId, conversationId);
 // 질문 처리를 시작하면서 로그인 사용자 id를 가져오고 해당 채팅방을 잠금 조회하는 부분

        chatbotMapper.expirePendingQuestions(
                conversationId,
                Math.max(1, pendingTimeoutSeconds)
        ); // 해당 채팅방에서 너무 오래 처리 중인 질문을 실패상태로 변경하는부분

        List<ChatbotMessageDto> messages =
                chatbotMapper.findMessages(userId, conversationId); // 해당 사용자의 특정 채팅방 메세지를 db에서 조회해 messages 변수에 담는 코드

        boolean hasPending = messages.stream()
                .anyMatch(message ->
                        "PENDING".equals(message.getStatus())
                ); // 메세지 목록에 '처리중'인 메세지가 하나라도 있는지 확인

        // 처리 중 상태는 DB 조회 결과에 따른 업무 규칙이므로 서비스에서 확인한다.
        if (hasPending) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CHATBOT_REQUEST_IN_PROGRESS",
                    "이 대화방에서 답변을 생성 중입니다. 잠시 후 다시 시도해주세요."
            );
        }


        ChatbotUsageDto usage =
                chatbotUsageService.reserveUsage(principal);  // 로그인한 사용자의 오늘 질문 횟수를 1회 사용 처리하고 변경된 사용량 정보를 usage 변수에 저장

        String title = buildTitle(question);

        int updated = chatbotMapper.updateConversation(
                userId,
                conversationId,
                conversation.getRevision(),
                title
        ); // 채팅방 정보를 db에서 갱신하고 수정된 행 개수를 updated에 저장

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

        // 완료된 메시지만 다음 AI 호출의 이전 대화로 사용한다.
        List<ChatbotMessageDto> completedHistory = messages.stream()
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
        // AI 호출 중 채팅방이 삭제됐다면 삭제 상태를 유지한다.
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
        // 사용자 소유권을 확인하고 동시 변경을 막는다.
        requireLockedConversation(userId, conversationId);

        // 기존 다중 테이블 UPDATE로 채팅방과 메시지를 함께 논리 삭제한다.
        int updated = chatbotMapper.deleteConversation(
                userId,
                conversationId
        );

        // 여러 행이 수정될 수 있으므로 1 이상이면 성공이다.
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
        // 입력값 검증은 Validator에 위임한다.
        chatbotValidator.validateConversationId(conversationId);

        List<ChatbotConversationDto> conversations =
                chatbotMapper.findConversations(
                        userId,
                        conversationId
                );

        // DB 조회와 소유권 확인 결과는 서비스에서 처리한다.
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
        // 입력값이 아닌 DB 수정 결과를 확인하므로 서비스에 유지
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