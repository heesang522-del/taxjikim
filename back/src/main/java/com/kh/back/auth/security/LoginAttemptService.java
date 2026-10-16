package com.kh.back.auth.security;

// 인증 보조 업무의 호출 규격과 구현을 분리한다.
public interface LoginAttemptService {
    void loginSucceeded(String loginId);

    int loginFailed(String loginId);

    void setBlockTime(String loginId, long minutes);

    long getRemainingBlockMinutes(String loginId);
}
