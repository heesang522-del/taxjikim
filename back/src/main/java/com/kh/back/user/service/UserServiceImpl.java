package com.kh.back.user.service;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.auth.service.EmailVerificationService;
import com.kh.back.common.util.FileUtil;
import com.kh.back.common.exception.ApiException;
import com.kh.back.common.validation.InputValidator;
import com.kh.back.common.validation.PasswordValidator;
import com.kh.back.common.validation.UserInputValidator;
import com.kh.back.user.dto.UserDto;
import com.kh.back.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
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
        // 가입·중복 확인·인증에서 같은 값으로 검증하고 저장하도록 앞뒤 공백을 정리한다.
        userDto.setLoginId(UserInputValidator.normalizeLoginId(userDto.getLoginId()));
        userDto.setEmail(UserInputValidator.normalizeEmail(userDto.getEmail()));
        InputValidator.requireText("name", userDto.getName());
        InputValidator.requireText("nickname", userDto.getNickname());
        InputValidator.requireMaxLength("name", userDto.getName(), 50);
        InputValidator.requireMaxLength("nickname", userDto.getNickname(), 50);
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
        return userMapper.countByLoginId(UserInputValidator.normalizeLoginId(loginId)) > 0;
    }

    @Override
    public boolean isNicknameDuplicate(String nickname) {
        return userMapper.countByNickname(nickname) > 0;
    }

    @Override
    public boolean isEmailDuplicate(String email) {
        return userMapper.countByEmail(UserInputValidator.normalizeEmail(email)) > 0;
    }

    /* ================= 3. 회원 정보 수정 (설정 페이지) ================= */

    @Transactional
    @Override
    public void updateNickname(String loginId, String nickname) {
        InputValidator.requireText("nickname", nickname);
        nickname = nickname.trim();
        InputValidator.requireMaxLength("nickname", nickname, 50);
        UserDto currentUser = userMapper.findByLoginId(loginId);
        if (currentUser == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "회원 정보를 찾을 수 없습니다.");
        }
        // 본인의 현재 닉네임은 다른 회원과의 중복과 구분해 변경하지 않는다.
        if (nickname.equals(currentUser.getNickname())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NICKNAME_UNCHANGED",
                    "현재 사용 중인 닉네임입니다. 다른 닉네임을 입력해주세요.");
        }
        if (userMapper.countByNickname(nickname) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "NICKNAME_DUPLICATE",
                    "이미 사용 중인 닉네임입니다.");
        }
        UserDto updateDto = new UserDto();
        updateDto.setLoginId(loginId);
        updateDto.setNickname(nickname);
        try {
            userMapper.updateUserFields(updateDto);
        } catch (DuplicateKeyException exception) {
            // 중복 조회 직후 다른 회원이 먼저 저장한 경우에도 같은 안내를 반환한다.
            throw new ApiException(HttpStatus.CONFLICT, "NICKNAME_DUPLICATE",
                    "이미 사용 중인 닉네임입니다.");
        }
        refreshSecurityContext(loginId);
    }

    @Transactional
    @Override
    public void updateEmail(String loginId, String email) {
        email = UserInputValidator.normalizeEmail(email);
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
