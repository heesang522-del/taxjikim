package com.kh.back.user.mapper;

import com.kh.back.user.dto.UserDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface UserMapper {

    void insertUser(UserDto user);

    // 중복 확인 쿼리
    int countByLoginId(@Param("loginId") String loginId);
    int countByNickname(@Param("nickname") String nickname);
    int countByEmail(@Param("email") String email);

    // 이름과 이메일로 아이디 조회 (없으면 null 반환)
    String findLoginIdByNameAndEmail(@Param("name") String name,
                                      @Param("email") String email);

    // 로그인 아이디(LOGIN_ID)로 회원 고유 번호(USER_ID) 조회
    Long findUserIdByLoginId(@Param("loginId") String loginId);

    // 회원 정보 단건 조회 (PK가 필요한 경우에도 이 메서드로 조회 후 getLoginId() 사용)
    UserDto findByLoginId(@Param("loginId") String loginId);

    UserDto findById(@Param("userId") Long userId);

    // 비밀번호 수정
    void updatePassword(@Param("loginId") String loginId,
                        @Param("password") String password);

    int updatePasswordByEmail(@Param("loginId") String loginId,
                              @Param("email") String email,
                              @Param("password") String password);

    // [통합] 회원 정보 동적 수정 (닉네임, 생년월일, 이메일, 프로필 이미지 등)
    void updateUserFields(UserDto user);

    void updateProfileImageUrl(@Param("loginId") String loginId, @Param("profileImageUrl") String profileImageUrl);

    // 회원 즉시 삭제
    void deleteUser(@Param("loginId") String loginId);

    // 로그인 시간 및 휴면/복구 관련
    int updateLastLoginAt(@Param("loginId") String loginId);
    int convertToDormantAccounts(@Param("cutoffDate") LocalDateTime cutoffDate);
    int activateDormantAccount(@Param("loginId") String loginId, @Param("email") String email);
}
