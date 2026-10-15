package com.kh.back.user.service;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.auth.service.EmailVerificationService;
import com.kh.back.common.util.FileUtil;
import com.kh.back.common.exception.ApiException;
import com.kh.back.common.validation.InputValidator;
import com.kh.back.common.validation.PasswordValidator;
import com.kh.back.user.dto.UserDto;
import com.kh.back.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import com.kh.back.common.util.SavedFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final FileUtil fileUtil;
    private static final String PROFILE_SUB_DIR = "profile";
    private static final String PROFILE_WEB_PREFIX = "/uploads/profile";

    private String saveProfile(MultipartFile file, String oldFilePath) {
        try {
            SavedFile savedFile = fileUtil.save(file, PROFILE_SUB_DIR, PROFILE_WEB_PREFIX, oldFilePath);
            return savedFile == null ? null : savedFile.getPath();
        } catch (IOException exception) {
            // 파일 저장 실패도 런타임 예외로 전달하여 회원 DB 작업을 롤백한다.
            throw new UncheckedIOException("프로필 파일 저장 중 오류가 발생했습니다.", exception);
        }
    }

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
    @Override
    public void joinUser(UserDto userDto, MultipartFile profileFile) {
        InputValidator.requireText("loginId", userDto.getLoginId());
        InputValidator.requireText("password", userDto.getPassword());
        InputValidator.requireText("name", userDto.getName());
        InputValidator.requireText("nickname", userDto.getNickname());
        InputValidator.requireText("email", userDto.getEmail());
        InputValidator.requireMaxLength("loginId", userDto.getLoginId(), 50);
        InputValidator.requireMaxLength("name", userDto.getName(), 50);
        InputValidator.requireMaxLength("nickname", userDto.getNickname(), 50);
        InputValidator.requireMaxLength("email", userDto.getEmail(), 255);
        PasswordValidator.validate("password", userDto.getPassword());
        // 이메일 인증 완료 여부 확인
        validateEmailVerification(userDto.getEmail());
        userDto.setPassword(
                passwordEncoder.encode(userDto.getPassword()));

        // 신규 저장이므로 oldFilePath 자리에 null 전달
        if (profileFile != null && !profileFile.isEmpty()) {
            userDto.setProfileImageUrl(
                    saveProfile(profileFile, null));
        }

        userMapper.insertUser(userDto);
        // 이메일 완료된 인증 데이터 삭제
        emailVerificationService.removeVerification(userDto.getEmail());
    }

    @Override
    public boolean isIdDuplicate(String loginId) {
        return userMapper.countByLoginId(loginId) > 0;
    }

    @Override
    public boolean isNicknameDuplicate(String nickname) {
        return userMapper.countByNickname(nickname) > 0;
    }

    @Override
    public boolean isEmailDuplicate(String email) {
        return userMapper.countByEmail(email) > 0;
    }

    /* ================= 3. 회원 정보 수정 (설정 페이지) ================= */

    @Transactional
    @Override
    public void updateNickname(String loginId, String nickname) {
        InputValidator.requireText("nickname", nickname);
        InputValidator.requireMaxLength("nickname", nickname, 50);
        UserDto updateDto = new UserDto();
        updateDto.setLoginId(loginId);
        updateDto.setNickname(nickname);
        userMapper.updateUserFields(updateDto);
        refreshSecurityContext(loginId);
    }

    @Transactional
    @Override
    public void updateEmail(String loginId, String email) {
        InputValidator.requireText("email", email);
        InputValidator.requireMaxLength("email", email, 255);
        // 계정 찾기에 사용하는 이메일은 새 주소 인증이 완료된 경우에만 변경한다.
        validateEmailVerification(email);
        UserDto updateDto = new UserDto();
        updateDto.setLoginId(loginId);
        updateDto.setEmail(email);
        userMapper.updateUserFields(updateDto);
        refreshSecurityContext(loginId);
        emailVerificationService.removeVerification(email);
    }

    @Transactional
    @Override
    public void updatePasswordInSettings(String loginId, String currentPassword, String newPassword) {
        InputValidator.requireText("currentPassword", currentPassword);
        PasswordValidator.validate("newPassword", newPassword);
        UserDto user = userMapper.findByLoginId(loginId);
        if (user == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INVALID", "현재 비밀번호가 올바르지 않습니다.");
        }

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_UNCHANGED", "현재 비밀번호와 동일한 비밀번호로는 변경할 수 없습니다.");
        }

        userMapper.updatePassword(loginId, passwordEncoder.encode(newPassword));
    }

    @Transactional
    @Override
    public void updateProfileImageUrl(String loginId, MultipartFile profile, boolean deleteProfile) {
        UserDto currentUser = userMapper.findByLoginId(loginId);
        String oldFilePath = currentUser != null ? currentUser.getProfileImageUrl() : null;

        if (deleteProfile) {
            userMapper.updateProfileImageUrl(loginId, null);
            try {
                fileUtil.delete(oldFilePath, PROFILE_SUB_DIR);
            } catch (IOException exception) {
                throw new UncheckedIOException("프로필 파일 삭제 중 오류가 발생했습니다.", exception);
            }
        } else if (profile != null && !profile.isEmpty()) {
            // 기존 파일 교체이므로 oldFilePath 전달
            String newFilePath = saveProfile(profile, oldFilePath);

            UserDto updateDto = new UserDto();
            updateDto.setLoginId(loginId);
            updateDto.setProfileImageUrl(newFilePath);
            userMapper.updateUserFields(updateDto);
        }

        refreshSecurityContext(loginId);
    }

    /* ================= 4. 계정 상태 변경 (휴면 / 탈퇴 / 복구) ================= */

    @Transactional
    @Override
    public void deleteUser(String loginId) {
        userMapper.deleteUser(loginId);
    }

    @Transactional
    @Override
    public void restoreDormantAccount(String loginId, String email) {
        if (!StringUtils.hasText(loginId) || !StringUtils.hasText(email)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "복구 요청 정보를 확인해주세요.");
        }

        // 이메일을 인증했다는 사실과 해당 계정의 소유자라는 사실을 함께 확인한다.
        UserDto user = userMapper.findByLoginId(loginId);
        if (user == null || !email.equals(user.getEmail())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ACCOUNT_RECOVERY_REJECTED", "계정 복구 정보를 확인해주세요.");
        }
        if (!emailVerificationService.isVerified(email)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMAIL_NOT_VERIFIED", "이메일 인증이 필요합니다.");
        }

        if (!"DORMANT".equals(user.getUserStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_NOT_DORMANT", "휴면 계정만 재활성화할 수 있습니다.");
        }
        int updatedCount = userMapper.activateDormantAccount(loginId, email);

        // 조회 후 이메일이나 상태가 바뀌면 SQL이 갱신하지 않으므로 성공으로 처리하지 않는다.
        if (updatedCount != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_RECOVERY_CONFLICT", "계정 상태가 변경되었습니다. 다시 확인해주세요.");
        }

        // 복구가 실제로 성공한 경우에만 인증 완료 기록을 소비한다.
        emailVerificationService.removeVerification(email);
    }

    /* ================= 5. 보조 메서드 ================= */

    @Override
    public UserDto getUserById(Long userId) {
        UserDto user = userMapper.findById(userId);
        if (user != null) {
            user.setPassword(null);
        }
        return user;
    }

    @Override
    public boolean checkPassword(String loginId, String rawPassword) {
        UserDto userDto = userMapper.findByLoginId(loginId);
        return userDto != null && passwordEncoder.matches(rawPassword, userDto.getPassword());
    }

    private void validateEmailVerification(String email) {
        if (!emailVerificationService.isVerified(email)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMAIL_NOT_VERIFIED", "이메일 인증이 완료되지 않았습니다.");
        }
    }
}
