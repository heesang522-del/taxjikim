package com.kh.back.user.service;

import com.kh.back.user.dto.UserDto;
import org.springframework.web.multipart.MultipartFile;

// 컨트롤러와 인증 코드는 구현체 대신 회원 업무 인터페이스에 의존한다.
public interface UserService {
    void joinUser(UserDto userDto, MultipartFile profileFile);

    boolean isIdDuplicate(String loginId);

    boolean isNicknameDuplicate(String nickname);

    boolean isEmailDuplicate(String email);

    void updateNickname(String loginId, String nickname);

    void updateEmail(String loginId, String email);

    void updatePasswordInSettings(String loginId, String currentPassword, String newPassword);

    void updateProfileImageUrl(String loginId, MultipartFile profile, boolean deleteProfile);

    void deleteUser(String loginId);

    void updateLastLoginAt(String loginId);

    void restoreDormantAccount(String loginId, String email);

    UserDto getUserByLoginId(String loginId);

    UserDto getUserById(Long userId);

    boolean checkPassword(String loginId, String rawPassword);

}
