package com.kh.back.auth.security;

import com.kh.back.auth.dto.LoginAttemptDto;

public interface LoginAttemptService {
    LoginAttemptDto getAttempt(String loginId);
    LoginAttemptDto loginFailed(String loginId);
    void loginSucceeded(String loginId);
}
