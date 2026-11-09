package com.kh.back.chatbot.service;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.chatbot.dto.ChatbotConversationDto.ConversationResponse;
import com.kh.back.chatbot.dto.ChatbotMessageDto.MessageResponse;
import com.kh.back.chatbot.dto.ChatbotReply;
import com.kh.back.common.exception.ApiException;

import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

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

    // SSE 작업에서 검증된 질문을 받고 답변 조각을 onDelta로 전달한다.
    ChatbotReply ask(
            CustomUserDetails principal,
            Long conversationId,
            String question,
            Consumer<String> onDelta,
            QuestionControl control
    );

    void deleteConversation(
            CustomUserDetails principal,
            Long conversationId
    );

    /**
     * 질문 한 건의 예약·취소·완료 상태를 관리한다.
     * 취소와 최종 저장을 같은 잠금으로 조정해 늦은 답변 저장을 막는다.
     */
    final class QuestionControl {

        private boolean cancelled;

        private ChatbotReply completed;

        private ChatbotHistoryService historyService;
        private Long userId;
        private Long conversationId;
        private Long questionId;

        public synchronized void checkActive() {
            if (cancelled || Thread.currentThread().isInterrupted()) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "CHATBOT_CANCELLED",
                        "취소된 질문입니다."
                );
            }
        }

        public synchronized ChatbotHistoryService.QuestionReservation begin(
                ChatbotHistoryService historyService,
                CustomUserDetails principal,
                Long conversationId,
                String question
        ) {
            checkActive();

            // 예약과 취소 대상 등록 사이에 취소가 끼어들지 못하게 한다.
            ChatbotHistoryService.QuestionReservation reservation =
                    historyService.beginQuestion(
                            principal,
                            conversationId,
                            question
                    );

            this.historyService = historyService;
            this.userId = principal.getUserId();
            this.conversationId = conversationId;
            this.questionId = reservation.questionId();

            return reservation;
        }

        public synchronized ChatbotReply complete(
                Supplier<ChatbotReply> saveAction
        ) {
            checkActive();

            // DB 저장이 성공한 경우에만 완료 결과를 보관한다.
            completed = saveAction.get();

            return completed;
        }

        public synchronized ChatbotReply cancel() {
            // 저장이 먼저 완료됐다면 성공 결과를 그대로 반환한다.
            if (completed != null) {
                return completed;
            }

            cancelled = true;

            if (questionId != null) {
                // 실패 상태 변경과 사용 횟수 복구는 기록 서비스에서 함께 처리한다.
                historyService.failQuestion(
                        userId,
                        conversationId,
                        questionId
                );
            }

            return null;
        }
    }
}