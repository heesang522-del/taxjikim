package com.kh.back.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.kh.back.user.dto.UserDto;
import com.kh.back.auth.dto.LockedAccountDto;
import java.util.List;

@Mapper
public interface UserSecurityMapper {

    /**
     * 계정 상태를 잠금(LOCKED) 상태로 변경 (15회 이상 로그인 실패 시)
     */
    int lockAccount(@Param("loginId") String loginId);

    /**
     * 관리자가 잠금·이용 정지를 해제한다.
     */
    int unlockAccount(@Param("loginId") String loginId);

    UserDto findForAuthentication(@Param("loginId") String loginId);
    List<LockedAccountDto> findLockedAccounts();
}
