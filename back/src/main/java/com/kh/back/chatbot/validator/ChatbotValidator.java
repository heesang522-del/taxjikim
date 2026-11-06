package com.kh.back.chatbot.validator;

import com.kh.back.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ChatbotValidator {

    private static final int MAX_QUESTION_LENGTH = 500;

    public void validateConversationId(Long conversationId) {
        if (conversationId == null || conversationId <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "대화방 번호가 올바르지 않습니다."
            );
        }
    }

    public void validateQuestion(String question) {
        // 서비스에서 앞뒤 공백을 제거한 질문을 전달받아 검사한다.
        if (question == null
                || question.isBlank()
                || question.length() > MAX_QUESTION_LENGTH) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "질문은 1~500자로 입력해주세요.",
                    Map.of(
                            "message",
                            "질문은 1~500자로 입력해주세요."
                    )
            );
        }
    }
}