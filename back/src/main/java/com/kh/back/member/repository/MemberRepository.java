package com.kh.back.member.repository;

import com.kh.back.member.dto.MemberDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface MemberRepository {

    void insertMember(MemberDto member);

    // 중복 확인 쿼리
    int countByMemberId(@Param("memberId") String memberId);
    int countByNickname(@Param("nickname") String nickname);
    int countByMemberEmail(@Param("memberEmail") String memberEmail);

    // 이름과 이메일로 아이디 조회 (없으면 null 반환)
    String findMemberIdByNameAndEmail(@Param("memberName") String memberName,
                                      @Param("memberEmail") String memberEmail);

    // 로그인 아이디(member_id)로 실제 회원 PK(id) 조회
    Integer findIdByMemberId(@Param("memberId") String memberId);

    // 회원 정보 단건 조회 (PK가 필요한 경우에도 이 메서드로 조회 후 getMemberId() 사용)
    MemberDto findByMemberId(@Param("memberId") String memberId);

    MemberDto findById(@Param("id") Long id);

    // 비밀번호 수정
    void updatePassword(@Param("memberId") String memberId,
                        @Param("memberPassword") String memberPassword);

    // [통합] 회원 정보 동적 수정 (닉네임, 전화번호, 이메일, 프로필 이미지 등)
    void updateMemberFields(MemberDto member);

    void updateProfileImage(@Param("memberId") String memberId, @Param("profileImage") String profileImage);

    // 회원 삭제 (상태 변경)
    void deleteMember(@Param("memberId") String memberId);

    // 만료된 탈퇴 회원 삭제
    int deleteExpiredMembers(@Param("cutoffDate") LocalDateTime cutoffDate);

    // 로그인 시간 및 휴면/복구 관련
    int updateLastLoginAt(@Param("memberId") String memberId);
    int convertToDormantAccounts(@Param("cutoffDate") LocalDateTime cutoffDate);
    int activateDormantAccount(@Param("memberId") String memberId);
    int cancelDeleteMember(@Param("memberId") String memberId);
}