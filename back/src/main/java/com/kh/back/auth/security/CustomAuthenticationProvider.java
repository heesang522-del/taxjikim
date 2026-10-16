package com.kh.back.auth.security;

import com.kh.back.auth.dto.LoginAttemptDto;
import com.kh.back.auth.mapper.UserSecurityMapper;
import com.kh.back.user.dto.UserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationProvider implements AuthenticationProvider {
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final UserSecurityMapper userSecurityMapper;

    // 인증을 별도 트랜잭션으로 처리해 바깥 로그인 실패에도 DB 잠금 변경을 보존한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = LoginFailureException.class)
    @Override
    public Authentication authenticate(Authentication authentication) {
        String loginId = authentication.getName().trim();
        String password = authentication.getCredentials().toString();

        // 같은 회원의 로그인과 관리자 해제를 DB 행 잠금으로 순서대로 처리한다.
        UserDto user = userSecurityMapper.findForAuthentication(loginId);
        if (user != null) {
            loginId = user.getLoginId();
            switch (user.getUserStatus()) {
                case "LOCKED" -> throw new LoginFailureException("LOCKED",
                        "로그인 실패로 계정이 잠겼습니다. 관리자에게 문의하세요.", loginAttemptService.getAttempt(loginId));
                case "BANNED" -> throw new DisabledException("BANNED");
                case "DORMANT" -> throw new LockedException("DORMANT");
                case "ACTIVE" -> { }
                default -> throw new DisabledException("ACCOUNT_UNAVAILABLE");
            }
        }
        LoginAttemptDto attempt = loginAttemptService.getAttempt(loginId);
        if (attempt.locked() || attempt.blocked()) reject(loginId, user, attempt);

        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            reject(loginId, user, loginAttemptService.loginFailed(loginId));
        }

        // 비밀번호와 회원 상태를 모두 통과한 경우에만 실패 기록을 초기화한다.
        loginAttemptService.loginSucceeded(loginId);
        CustomUserDetails details = new CustomUserDetails(user);
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    private void reject(String loginId, UserDto user, LoginAttemptDto attempt) {
        if (attempt.locked()) {
            if (user != null) userSecurityMapper.lockAccount(loginId);
            throw new LoginFailureException("LOCKED",
                    "로그인 실패로 계정이 잠겼습니다. 관리자에게 문의하세요.", attempt);
        }
        if (attempt.blocked()) {
            String message = "로그인이 일시 제한되었습니다. 남은 시간 후 다시 시도해주세요.";
            if (attempt.failedAttempts() == 9) message += " 다음 실패 시 계정이 잠기며 관리자 해제가 필요합니다.";
            throw new LoginFailureException("LOGIN_TEMPORARILY_BLOCKED", message, attempt);
        }
        throw new LoginFailureException("LOGIN_FAILED", "아이디 또는 비밀번호를 확인해주세요. 첫 잠금까지 "
                + attempt.remainingAttempts() + "회 남았습니다.", attempt);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
