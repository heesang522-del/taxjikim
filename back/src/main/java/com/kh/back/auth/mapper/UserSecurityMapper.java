package com.kh.back.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserSecurityMapper {

    /**
     * 계정 상태를 잠금(LOCKED) 상태로 변경 (15회 이상 로그인 실패 시)
     */
    int lockAccount(@Param("loginId") String loginId);

    /**
     * 계정 상태를 정상(ACTIVE) 상태로 변경 (이메일 인증 해제 성공 시)
     */
    int unlockAccount(@Param("loginId") String loginId);
}
