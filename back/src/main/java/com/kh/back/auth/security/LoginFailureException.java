package com.kh.back.auth.security;

import com.kh.back.auth.dto.LoginAttemptDto;
import lombok.Getter;
import org.springframework.security.core.AuthenticationException;

@Getter
public class LoginFailureException extends AuthenticationException {
    private final String code;
    private final LoginAttemptDto details;

    public LoginFailureException(String code, String message, LoginAttemptDto details) {
        super(message);
        this.code = code;
        this.details = details;
    }
}
