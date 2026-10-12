package com.kh.back.member.service;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.auth.service.EmailVerificationService;
import com.kh.back.common.util.FileUtil;
import com.kh.back.member.dto.MemberDto;
import com.kh.back.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final FileUtil fileUtil; // 🎯 공통 파일 유틸 주입

    // SecurityContext 내의 MemberDto 정보 갱신용 보조 메서드
    private void refreshSecurityContext(String memberId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails) {
            MemberDto updatedMember = memberRepository.findByMemberId(memberId);
            if (updatedMember != null) {
                updatedMember.setMemberPassword(null); // 보안상 비밀번호 제거
                CustomUserDetails newDetails = new CustomUserDetails(updatedMember);
                UsernamePasswordAuthenticationToken newAuth = new UsernamePasswordAuthenticationToken(
                        newDetails, auth.getCredentials(), newDetails.getAuthorities()
                );
                SecurityContextHolder.getContext().setAuthentication(newAuth);
            }
        }
    }

    /* ================= 1. 회원가입 및 중복확인 ================= */

    @Transactional
    public void joinMember(MemberDto memberDto, MultipartFile profileFile) {
        // 이메일 인증 완료 여부 확인
        validateEmailVerification(memberDto.getMemberEmail());
        memberDto.setMemberPassword(
                passwordEncoder.encode(memberDto.getMemberPassword()));

        // 신규 저장이므로 oldFilePath 자리에 null 전달
        if (profileFile != null && !profileFile.isEmpty()) {
            memberDto.setProfileImage(
                    fileUtil.saveFile(profileFile, "profile", null));
        }

        memberRepository.insertMember(memberDto);
        // 이메일 완료된 인증 데이터 삭제
        emailVerificationService.removeVerification(memberDto.getMemberEmail());
    }

    public boolean isIdDuplicate(String memberId) {
        return memberRepository.countByMemberId(memberId) > 0;
    }

    public boolean isNicknameDuplicate(String nickname) {
        return memberRepository.countByNickname(nickname) > 0;
    }

    public boolean isEmailDuplicate(String memberEmail) {
        return memberRepository.countByMemberEmail(memberEmail) > 0;
    }

    /* ================= 3. 회원 정보 수정 (설정 페이지) ================= */

    @Transactional
    public void updateNickname(String memberId, String nickname) {
        MemberDto updateDto = new MemberDto();
        updateDto.setMemberId(memberId);
        updateDto.setNickname(nickname);
        memberRepository.updateMemberFields(updateDto);
        refreshSecurityContext(memberId);
    }

    @Transactional
    public void updatePhone(String memberId, String memberPhone) {
        MemberDto updateDto = new MemberDto();
        updateDto.setMemberId(memberId);
        updateDto.setMemberPhone(memberPhone);
        memberRepository.updateMemberFields(updateDto);
        refreshSecurityContext(memberId);
    }

    @Transactional
    public void updateEmail(String memberId, String memberEmail) {
        MemberDto updateDto = new MemberDto();
        updateDto.setMemberId(memberId);
        updateDto.setMemberEmail(memberEmail);
        memberRepository.updateMemberFields(updateDto);
        refreshSecurityContext(memberId);
    }

    @Transactional
    public void updatePasswordInSettings(String memberId, String currentPassword, String newPassword) {
        MemberDto member = memberRepository.findByMemberId(memberId);
        if (member == null || !passwordEncoder.matches(currentPassword, member.getMemberPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
        }

        if (passwordEncoder.matches(newPassword, member.getMemberPassword())) {
            throw new IllegalArgumentException("현재 비밀번호와 동일한 비밀번호로는 변경할 수 없습니다.");
        }

        memberRepository.updatePassword(memberId, passwordEncoder.encode(newPassword));
    }

    @Transactional
    public void updateProfileImage(String memberId, MultipartFile profile, boolean deleteProfile) {
        MemberDto currentMember = memberRepository.findByMemberId(memberId);
        String oldFilePath = currentMember != null ? currentMember.getProfileImage() : null;

        if (deleteProfile) {
            memberRepository.updateProfileImage(memberId, null);
            fileUtil.deleteFile(oldFilePath);
        } else if (profile != null && !profile.isEmpty()) {
            // 기존 파일 교체이므로 oldFilePath 전달
            String newFilePath = fileUtil.saveFile(profile, "profile", oldFilePath);

            MemberDto updateDto = new MemberDto();
            updateDto.setMemberId(memberId);
            updateDto.setProfileImage(newFilePath);
            memberRepository.updateMemberFields(updateDto);
        }

        refreshSecurityContext(memberId);
    }

    /* ================= 4. 계정 상태 변경 (휴면 / 탈퇴 / 복구) ================= */

    @Transactional
    public void deleteMember(String memberId) {
        memberRepository.deleteMember(memberId);
    }

    @Transactional
    public void updateLastLoginAt(String memberId) {
        memberRepository.updateLastLoginAt(memberId);
    }

    @Transactional
    public void restoreAccountStatus(String memberId, String memberEmail, AccountRestoreType type) {
        validateEmailVerification(memberEmail);

        if (type == AccountRestoreType.DORMANT) {
            memberRepository.activateDormantAccount(memberId);
        } else if (type == AccountRestoreType.DELETE) {
            memberRepository.cancelDeleteMember(memberId);
        }

        emailVerificationService.removeVerification(memberEmail);
    }

    public enum AccountRestoreType {
        DORMANT, DELETE
    }

    /* ================= 5. 보조 메서드 ================= */

    @Transactional
    public MemberDto getMemberById(String memberId) {
        MemberDto memberDto = memberRepository.findByMemberId(memberId);
        if (memberDto != null) {
            memberDto.setMemberPassword(null);
        }
        return memberDto;
    }

    public MemberDto getMemberByNumber(Long memberNumber) {
        MemberDto member = memberRepository.findById(memberNumber);
        if (member != null) {
            member.setMemberPassword(null);
        }
        return member;
    }

    public boolean checkPassword(String memberId, String rawPassword) {
        MemberDto memberDto = memberRepository.findByMemberId(memberId);
        return memberDto != null && passwordEncoder.matches(rawPassword, memberDto.getMemberPassword());
    }

    private void validateEmailVerification(String email) {
        if (!emailVerificationService.isVerified(email)) {
            throw new IllegalStateException("이메일 인증이 완료되지 않았습니다.");
        }
    }
}