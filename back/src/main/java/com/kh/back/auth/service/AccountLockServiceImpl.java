package com.kh.back.auth.service;

import com.kh.back.auth.dto.LockedAccountDto;
import com.kh.back.auth.mapper.UserSecurityMapper;
import com.kh.back.auth.security.LoginAttemptService;
import com.kh.back.auth.security.LoginSessionRepository;
import com.kh.back.common.exception.ApiException;
import com.kh.back.user.dto.UserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountLockServiceImpl implements AccountLockService {
    private final UserSecurityMapper mapper;
    private final LoginAttemptService attempts;
    private final LoginSessionRepository sessions;

    @Override
    public List<LockedAccountDto> getLockedAccounts() {
        return mapper.findLockedAccounts();
    }

    @Override
    @Transactional
    public void unlockAccount(String loginId) {
        // 해제 중 동일 회원의 상태 변경을 막기 위해 회원 행을 잠근다.
        UserDto user = mapper.findForAuthentication(loginId);
        if (user == null) throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "회원을 찾을 수 없습니다.");
        if (!"LOCKED".equals(user.getUserStatus()) && !"BANNED".equals(user.getUserStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_NOT_LOCKED", "잠금 또는 이용 정지 상태가 아닙니다.");
        }
        if (mapper.unlockAccount(user.getLoginId()) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_UNLOCK_CONFLICT", "계정 상태가 변경되었습니다.");
        }
        // 해제 후 잠금 이전의 쿠키가 다시 유효해지지 않도록 기존 로그인도 종료한다.
        sessions.revokeCurrentSession(user.getUserId());
        // Redis 초기화에 실패하면 DB 해제도 롤백해 성공으로 응답하지 않는다.
        attempts.loginSucceeded(user.getLoginId());
    }
}
