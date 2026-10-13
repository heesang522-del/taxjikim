package com.kh.back.auth.security;

// 인증 보조 업무의 호출 규격과 구현을 분리한다.
public interface LoginUnlockTokenService {
    String createUnlockToken(String loginId);

    String getLoginIdByToken(String token);

    void invalidateToken(String token);
}
