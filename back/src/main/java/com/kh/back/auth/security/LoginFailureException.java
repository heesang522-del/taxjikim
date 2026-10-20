package com.kh.back.auth.security;

import com.kh.back.auth.dto.LoginAttemptDto;
import lombok.Getter;
import org.springframework.security.core.AuthenticationException;

@Getter
public class LoginFailureException extends AuthenticationException {
    // 글로벌에서 안내 문구를 선택할 로그인 실패 응답 코드. DB 회원 상태와는 별개
    private final String code;
    // 실패 횟수·남은 횟수·차단 종료 시각·서버 시각·잠금 대상 여부를 묶은 DTO. 필요 없으면 null
    private final LoginAttemptDto details;

    // 실패 기록 없이 로그인 거절 코드만 전달
    public LoginFailureException(String code) {
        this(code, null);
    }

    // 로그인 거절 코드와 실패 기록을 전달. 사용자 안내 문구는 글로벌에서 작성
    public LoginFailureException(String code, LoginAttemptDto details) {
        super(code);
        this.code = code;
        this.details = details;
    }
}
