package com.kh.back.auth.service;

// 호출자는 업무 인터페이스를 사용하고 구현체가 처리 흐름을 담당한다.
public interface UnlockEmailService {
    void sendWarningEmail(String loginId, String toEmail, String failReason);

    void sendUnlockEmail(String loginId, String toEmail);
}
