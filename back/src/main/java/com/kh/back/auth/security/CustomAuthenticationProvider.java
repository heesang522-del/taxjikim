package com.kh.back.auth.security;

import com.kh.back.auth.repository.MemberSecurityRepository;
import com.kh.back.auth.service.UnlockEmailService;
import com.kh.back.member.dto.MemberDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationProvider implements AuthenticationProvider {

    private final CustomUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final MemberSecurityRepository memberSecurityRepository;
    private final UnlockEmailService unlockEmailService;

    @Override
    public Authentication authenticate(Authentication authentication)
            throws AuthenticationException {

        String memberId = authentication.getName();
        String password = authentication.getCredentials().toString();

        // 1. 5회/10회 일시 차단(30분/1시간) 여부 체크 (최우선 실행)
        long remainingMinutes = loginAttemptService.getRemainingBlockMinutes(memberId);
        if (remainingMinutes > 0) {
            throw new LockedException(remainingMinutes + "분 동안 로그인이 제한됩니다.");
        }

        // 2. DB 회원 정보 조회
        CustomUserDetails userDetails = null;
        try {
            userDetails = (CustomUserDetails) userDetailsService.loadUserByUsername(memberId);
        } catch (Exception e) {
            // 회원 정보가 없는 경우 실패 처리 메서드 실행 (내부에서 예외 던짐)
            processFailedAttempt(memberId, null);
        }

        // 3. 비밀번호 검증
        if (!passwordEncoder.matches(password, userDetails.getPassword())) {
            // 비밀번호 불일치 시 실패 처리 메서드 실행 (내부에서 예외 던짐)
            processFailedAttempt(memberId, userDetails.getMemberDto());
        }

        // 4. 회원 상태(member_status) 검증
        MemberDto member = userDetails.getMemberDto();
        String status = member != null ? member.getMemberStatus() : "ACTIVE";

        if ("DORMANT".equals(status)) {
            throw new LockedException("DORMANT");
        }
        if ("DELETED".equals(status)) {
            throw new DisabledException("DELETED");
        }
        if ("BANNED".equals(status)) {
            throw new DisabledException("BANNED");
        }
        if ("LOCKED".equals(status)) {
            throw new LockedException("LOCKED");
        }

        // 5. 로그인 성공 (Caffeine 메모리 실패 기록 리셋)
        loginAttemptService.loginSucceeded(memberId);

        return new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
    }

    /**
     * 로그인 실패 통합 처리 메서드 (1회만 실패 카운트 증가)
     */
    private void processFailedAttempt(String memberId, MemberDto memberDto) throws AuthenticationException {
        int failCount = loginAttemptService.loginFailed(memberId);

        // 1. 5회 실패 (30분 차단)
        if (failCount == 5) {
            loginAttemptService.setBlockTime(memberId, 30);
            if (memberDto != null && memberDto.getMemberEmail() != null) {
                unlockEmailService.sendWarningEmail(memberId, memberDto.getMemberEmail(), "5회 실패 (30분 일시 차단)");
            }
            throw new LockedException("5회 실패로 30분간 로그인이 제한됩니다. 발송된 이메일을 통해 바로 해제할 수 있습니다.");
        }

        // 2. 10회 실패 (1시간 차단)
        if (failCount == 10) {
            loginAttemptService.setBlockTime(memberId, 60);
            if (memberDto != null && memberDto.getMemberEmail() != null) {
                unlockEmailService.sendWarningEmail(memberId, memberDto.getMemberEmail(), "10회 실패 (1시간 일시 차단)");
            }
            throw new LockedException("10회 실패로 1시간 동안 로그인이 제한됩니다. 발송된 이메일을 통해 바로 해제할 수 있습니다.");
        }

        // 3. 15회 이상 실패 시 DB 영구 잠금(LOCKED)
        if (failCount >= 15) {
            memberSecurityRepository.lockAccount(memberId);
            if (memberDto != null && memberDto.getMemberEmail() != null) {
                unlockEmailService.sendUnlockEmail(memberId, memberDto.getMemberEmail());
            }
            throw new LockedException("LOCKED");
        }

        int targetLimit = (failCount < 5) ? 5 : (failCount < 10) ? 10 : 15;
        throw new BadCredentialsException("회원 정보가 올바르지 않습니다. (실패 횟수: " + failCount + "/" + targetLimit + "회)");
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}