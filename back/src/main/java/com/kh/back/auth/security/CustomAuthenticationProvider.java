package com.kh.back.auth.security;

import com.kh.back.auth.dto.LoginAttemptDto;
import com.kh.back.auth.mapper.UserSecurityMapper;
import com.kh.back.user.dto.UserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
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

    // 회원 상태·차단 여부·비밀번호를 검사해 로그인 인증 처리
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
                case "LOCKED" -> throw new LoginFailureException("LOCKED", loginAttemptService.getAttempt(loginId));
                case "BANNED" -> throw new LoginFailureException("BANNED");
                case "DORMANT" -> throw new LoginFailureException("DORMANT");
                case "ACTIVE" -> { } // 정상 상태이면 다음 검사로 진행한다.
                // 알 수 없는 회원 상태도 정상 회원으로 간주하지 않고 인증을 거절한다.
                default -> throw new LoginFailureException("AUTHENTICATION_FAILED");
            }
        }
        // 회원이 없는 아이디도 Redis 기록을 조회한다. 조회 자체로 실패 횟수가 늘지는 않는다.
        LoginAttemptDto attempt = loginAttemptService.getAttempt(loginId);
        // locked는 Redis 실패 횟수 기준 잠금 대상, blocked는 아직 일시 차단 시간이 남은 상태다.
        // 기존 차단이 있으면 실패를 추가하지 않고 현재 상태로 거절한다.
        if (attempt.locked() || attempt.blocked()) reject(loginId, user, attempt);

        // 회원이 없거나 비밀번호가 일치하지 않으면 실패로 기록한다.
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            // 실패 횟수를 갱신한 결과를 받아, 일반 실패·일시 차단·계정 잠금 중 하나로 거절한다.
            reject(loginId, user, loginAttemptService.loginFailed(loginId));
        }

        // 비밀번호와 회원 상태를 모두 통과한 경우에만 실패 기록을 초기화한다.
        loginAttemptService.loginSucceeded(loginId);
        // 검사에 통과한 회원을 Spring Security에서 사용할 사용자 정보로 감싼다.
        CustomUserDetails details = new CustomUserDetails(user);
        // 사용자와 권한이 담긴 인증 성공 객체를 반환한다. 입력 비밀번호는 결과에 보관하지 않는다(null).
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    // 실패 기록에 따라 계정 잠금 또는 로그인 거절 처리
    private void reject(String loginId, UserDto user, LoginAttemptDto attempt) {
        if (attempt.locked()) {
            // 현재 Redis 실패 횟수 기준으로 10회 이상이면 실제 회원의 DB 상태를 LOCKED로 변경한다.
            // 존재하지 않는 아이디는 변경할 회원 행이 없으므로 DB 갱신을 생략한다.
            if (user != null) userSecurityMapper.lockAccount(loginId);
            throw new LoginFailureException("LOCKED", attempt);
        }
        if (attempt.blocked()) {
            // 일시 차단은 DB 상태를 바꾸지 않고 차단 코드와 기록만 전달한다.
            throw new LoginFailureException("LOGIN_TEMPORARILY_BLOCKED", attempt);
        }
        // 아직 차단 단계가 아니면 일반 실패 코드와 기록을 전달한다. 안내 문구는 글로벌에서 만든다.
        throw new LoginFailureException("LOGIN_FAILED", attempt);
    }

    // 이 클래스가 처리할 수 있는 인증 객체 타입인지 확인
    @Override
    public boolean supports(Class<?> authentication) {
        // Spring Security가 이 인증 요청을 이 클래스에 맡길 수 있는지 확인할 때 호출한다.
        // 아이디·비밀번호용 인증 객체와 그 하위 타입을 처리할 수 있다고 반환한다.
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
