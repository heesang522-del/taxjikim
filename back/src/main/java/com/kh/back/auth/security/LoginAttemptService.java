package com.kh.back.auth.security;

import com.kh.back.auth.dto.LoginAttemptDto;

/**
 * 아이디별 로그인 실패 횟수와 일시 차단 시간을 관리하는 기능의 목록이다.
 * 실제 저장·계산은 LoginAttemptServiceImpl이 담당하고, 비밀번호 검사는 인증 담당 코드에서 한다.
 */
public interface LoginAttemptService {
    // 실패 횟수와 차단 상태를 조회한다. 조회만으로 실패 횟수는 늘어나지 않는다.
    LoginAttemptDto getAttempt(String loginId);

    // 실패를 기록한 뒤 현재 상태를 반환한다. 차단 중에는 횟수·차단 시간을 변경하지 않는다.
    LoginAttemptDto loginFailed(String loginId);

    // 로그인 성공 또는 관리자 잠금 해제 시 실패 횟수와 일시 차단 기록을 삭제한다.
    // DB의 회원 상태를 변경하는 기능은 포함하지 않는다.
    void loginSucceeded(String loginId);
}
