package com.kh.back.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotSseEvent {

    private String requestId; // 요청 id
    private Long conversationId; //  대화 id

    private Boolean accepted; // 접수됐는지 여부
    private Boolean cancelled; // 취소됐는지 여부

    private String delta; // 방금 만들어진 답변조각
    private String reply; // 완성된 답변전체
    private Integer remaining; // 오늘 남은 질문 횟수

    private String code; // 프로그램이 오류 종류를 구분하는 오류 코드
    private String message; // 사용자에게 보여주는 오류 안내 문구
}