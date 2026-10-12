package com.kh.back.auth.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemberSecurityRepository {

    /**
     * 계정 상태를 잠금(LOCKED) 상태로 변경 (15회 이상 로그인 실패 시)
     */
    int lockAccount(@Param("memberId") String memberId);

    /**
     * 계정 상태를 정상(ACTIVE) 상태로 변경 (이메일 인증 해제 성공 시)
     */
    int unlockAccount(@Param("memberId") String memberId);
}