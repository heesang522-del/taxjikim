package com.kh.back.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.kh.back.user.dto.UserDto;
import com.kh.back.auth.dto.LockedAccountDto;
import java.util.List;

// 로그인 검사와 관리자 계정 잠금·해제에 필요한 DB 작업
@Mapper
public interface UserSecurityMapper {

    // 로그인 실패로 계정을 잠금 상태로 변경
    int lockAccount(@Param("loginId") String loginId);

    // 관리자가 계정 잠금·이용 정지를 해제
    int unlockAccount(@Param("loginId") String loginId);

    // 로그인 검사에 필요한 회원 정보 조회
    UserDto findForAuthentication(@Param("loginId") String loginId);

    // 관리자 화면에 표시할 잠금·이용 정지 회원 목록 조회
    List<LockedAccountDto> findLockedAccounts();
}
