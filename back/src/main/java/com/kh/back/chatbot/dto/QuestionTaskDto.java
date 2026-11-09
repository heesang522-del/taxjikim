package com.kh.back.chatbot.dto;

import com.kh.back.chatbot.service.ChatbotService;

import lombok.Builder;

import java.util.concurrent.FutureTask;

public final class QuestionTaskDto {

    private final ChatbotService.QuestionControl control =
            new ChatbotService.QuestionControl();

    private final long createdAt = System.currentTimeMillis();

    // 오래된 작업을 정리하는 스레드에서도 등록된 작업을 읽을 수 있게 한다.
    private volatile FutureTask<Void> future;

    private boolean submitted;

    @Builder
    private QuestionTaskDto() {
    }

    public ChatbotService.QuestionControl getControl() {
        return control;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public FutureTask<Void> getFuture() {
        return future;
    }

    public void setFuture(FutureTask<Void> future) {
        this.future = future;
    }

    public boolean isSubmitted() {
        return submitted;
    }

    public void setSubmitted(boolean submitted) {
        this.submitted = submitted;
    }
}