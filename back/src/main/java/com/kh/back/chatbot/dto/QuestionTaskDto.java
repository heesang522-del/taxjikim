package com.kh.back.chatbot.dto;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.chatbot.service.ChatbotHistoryService;
import com.kh.back.common.exception.ApiException;
import lombok.Builder;
import org.springframework.http.HttpStatus;

import java.util.concurrent.FutureTask;
import java.util.function.Supplier;

public final class QuestionTaskDto {

    private final long createdAt = System.currentTimeMillis(); // 질문 작업 객체가 만들어진 시각을 기록하는 부분
    // 질문 작업이 생성된지 30분이 넘었는지 판단, 실행이 끝났거나 시랭 작업이 없는 항목만 메모리에서 지움 , 완료된 작업도 잠시 남겨두는 이유는 같은 요청 id가 다시 들어왔을떄 중복 실행을 막고 이미 완료된 질문에 취소 요청이 오면 기존 답변을 돌려주기 위해서
    private volatile FutureTask<Void> future; // 질문 답변을 생성하는 백그라운드 작업을 보관하는 필드

    private boolean submitted; // 질문을 이미 접수했는지 나타냄, 같은 요청이 다시 들어왔을때 중복 실행을 막는데 씀
    private boolean cancelled; // 질문이 취소됐는지 나타냄, true가 되면 답변 생성이나 최종 저장을 진행하기 전에 취소를 확인
    private ChatbotReply completed; // 저장까지 끝난 답변결과를 보관하고 저장이 끝난 뒤에 취소 요청이 오면 이결과를 돌려줌

    private ChatbotHistoryService historyService; //대화 기록을 관리하는 서비스, 취소시 질문을 실패 상태로 바꾸고 사용횟수를 복구하는 failQuestion()을 호출
    private Long userId; // 질문을 보낸 회원의 번호
    private Long conversationId; // 질문이 속한 대화방의 번호
    private Long questionId; // 서버가 저장한 개별 질문의 번호, 같은 대화방에 질문이 여러개 있어도 이번호로 구분

    @Builder
    private QuestionTaskDto() {
    }

    public long getCreatedAt() { // 객체가 만들어진 시각을 돌려줌, 오래된 작업을 메모리에서 정리할때 사용
        return createdAt;
    }

    public FutureTask<Void> getFuture() { // 등록된 백그라운드 작업을 돌려줌, 취소 요청이 오면 이 작업을 찾아 중단 요청
        return future;
    }

    public void setFuture(FutureTask<Void> future) { // 전달받은 백그라운드 작업을 현재 객체에 기록, this.future가 객체의 필드
        this.future = future;
    }

    public boolean isSubmitted() { // 질문이 이미 접수됐는지 확인
        return submitted;
    }

    public void setSubmitted(boolean submitted) { // 접수 상태를 기록, 질문을 접수할떄 setSubmitted(true)를 호출
        this.submitted = submitted;
    }

    public synchronized void checkActive() { // 이 질문의 답변 생성을 계속해도 되는지 확인하는 메서드
        if (cancelled || Thread.currentThread().isInterrupted()) { // 사용자가 질문을 취소했는지 , thread.currentThread() .isInter
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

        // 질문 예약과 취소 대상 등록을 같은 잠금 안에서 처리한다.
        ChatbotHistoryService.QuestionReservation reservation =
                historyService.beginQuestion(principal, conversationId, question);

        this.historyService = historyService;
        this.userId = principal.getUserId();
        this.conversationId = conversationId;
        this.questionId = reservation.questionId();

        return reservation;
    }

    public synchronized ChatbotReply complete(Supplier<ChatbotReply> saveAction) {
        checkActive();

        // DB 저장이 성공한 뒤 완료 결과를 보관한다.
        completed = saveAction.get();

        return completed;
    }

    public synchronized ChatbotReply cancel() {
        // 답변 저장이 먼저 끝났다면 이미 완료된 답변을 돌려준다.
        if (completed != null) {
            return completed;
        }

        cancelled = true;

        if (questionId != null) {
            // 기록 서비스에서 질문 실패 처리와 사용 횟수 복구를 함께 수행한다.
            historyService.failQuestion(userId, conversationId, questionId);
        }

        return null;
    }
}