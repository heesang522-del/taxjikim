package com.kh.back.user.service;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.auth.service.EmailVerificationService;
import com.kh.back.common.util.FileUtil;
import com.kh.back.user.dto.UserDto;
import com.kh.back.user.mapper.UserMapper;
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
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final FileUtil fileUtil; // 🎯 공통 파일 유틸 주입

    // SecurityContext 내의 UserDto 정보 갱신용 보조 메서드
    private void refreshSecurityContext(String loginId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails) {
            UserDto updatedUser = userMapper.findByLoginId(loginId);
            if (updatedUser != null) {
                updatedUser.setPassword(null); // 보안상 비밀번호 제거
                CustomUserDetails newDetails = new CustomUserDetails(updatedUser);
                UsernamePasswordAuthenticationToken newAuth = new UsernamePasswordAuthenticationToken(
                        newDetails, auth.getCredentials(), newDetails.getAuthorities()
                );
                SecurityContextHolder.getContext().setAuthentication(newAuth);
            }
        }
    }

    /* ================= 1. 회원가입 및 중복확인 ================= */

    @Transactional
    public void joinUser(UserDto userDto, MultipartFile profileFile) {
        // 이메일 인증 완료 여부 확인
        validateEmailVerification(userDto.getEmail());
        userDto.setPassword(
                passwordEncoder.encode(userDto.getPassword()));

        // 신규 저장이므로 oldFilePath 자리에 null 전달
        if (profileFile != null && !profileFile.isEmpty()) {
            userDto.setProfileImageUrl(
                    fileUtil.saveFile(profileFile, "profile", null));
        }

        userMapper.insertUser(userDto);
        // 이메일 완료된 인증 데이터 삭제
        emailVerificationService.removeVerification(userDto.getEmail());
    }

    public boolean isIdDuplicate(String loginId) {
        return userMapper.countByLoginId(loginId) > 0;
    }

    public boolean isNicknameDuplicate(String nickname) {
        return userMapper.countByNickname(nickname) > 0;
    }

    public boolean isEmailDuplicate(String email) {
        return userMapper.countByEmail(email) > 0;
    }

    /* ================= 3. 회원 정보 수정 (설정 페이지) ================= */

    @Transactional
    public void updateNickname(String loginId, String nickname) {
        UserDto updateDto = new UserDto();
        updateDto.setLoginId(loginId);
        updateDto.setNickname(nickname);
        userMapper.updateUserFields(updateDto);
        refreshSecurityContext(loginId);
    }


    @Transactional
    public void updateEmail(String loginId, String email) {
        UserDto updateDto = new UserDto();
        updateDto.setLoginId(loginId);
        updateDto.setEmail(email);
        userMapper.updateUserFields(updateDto);
        refreshSecurityContext(loginId);
    }

    @Transactional
    public void updatePasswordInSettings(String loginId, String currentPassword, String newPassword) {
        UserDto user = userMapper.findByLoginId(loginId);
        if (user == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
        }

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호와 동일한 비밀번호로는 변경할 수 없습니다.");
        }

        userMapper.updatePassword(loginId, passwordEncoder.encode(newPassword));
    }

    @Transactional
    public void updateProfileImageUrl(String loginId, MultipartFile profile, boolean deleteProfile) {
        UserDto currentUser = userMapper.findByLoginId(loginId);
        String oldFilePath = currentUser != null ? currentUser.getProfileImageUrl() : null;

        if (deleteProfile) {
            userMapper.updateProfileImageUrl(loginId, null);
            fileUtil.deleteFile(oldFilePath);
        } else if (profile != null && !profile.isEmpty()) {
            // 기존 파일 교체이므로 oldFilePath 전달
            String newFilePath = fileUtil.saveFile(profile, "profile", oldFilePath);

            UserDto updateDto = new UserDto();
            updateDto.setLoginId(loginId);
            updateDto.setProfileImageUrl(newFilePath);
            userMapper.updateUserFields(updateDto);
        }

        refreshSecurityContext(loginId);
    }

    /* ================= 4. 계정 상태 변경 (휴면 / 탈퇴 / 복구) ================= */

    @Transactional
    public void deleteUser(String loginId) {
        userMapper.deleteUser(loginId);
    }

    @Transactional
    public void updateLastLoginAt(String loginId) {
        userMapper.updateLastLoginAt(loginId);
    }

    @Transactional
    public void restoreAccountStatus(String loginId, String email, AccountRestoreType type) {
        validateEmailVerification(email);

        if (type == AccountRestoreType.DORMANT) {
            userMapper.activateDormantAccount(loginId);
        } else if (type == AccountRestoreType.DELETE) {
            userMapper.cancelDeleteUser(loginId);
        }

        emailVerificationService.removeVerification(email);
    }


    /* ================= 5. 보조 메서드 ================= */

    @Transactional
    public UserDto getUserByLoginId(String loginId) {
        UserDto userDto = userMapper.findByLoginId(loginId);
        if (userDto != null) {
            userDto.setPassword(null);
        }
        return userDto;
    }

    public UserDto getUserById(Long userId) {
        UserDto user = userMapper.findById(userId);
        if (user != null) {
            user.setPassword(null);
        }
        return user;
    }

    public boolean checkPassword(String loginId, String rawPassword) {
        UserDto userDto = userMapper.findByLoginId(loginId);
        return userDto != null && passwordEncoder.matches(rawPassword, userDto.getPassword());
    }

    private void validateEmailVerification(String email) {
        if (!emailVerificationService.isVerified(email)) {
            throw new IllegalStateException("이메일 인증이 완료되지 않았습니다.");
        }
    }
}