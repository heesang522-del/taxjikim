package com.kh.back.auth.service;

import com.kh.back.auth.dto.LoginResponseDto;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;

// 호출자는 업무 인터페이스를 사용하고 구현체가 처리 흐름을 담당한다.
public interface AuthService {
    void unlockAccount(String token);
    void logout(HttpServletRequest request, HttpServletResponse response);
    LoginResponseDto login(String loginId, String password, boolean rememberMe,
                               HttpServletResponse response);

    String reissue(String refreshToken, HttpServletResponse response);

    void sendCodeForFindId(String name, String email);

    void sendCodeForFindPw(String loginId, String email);

    String findLoginId(String name, String email);

    void resetPassword(String loginId, String email, String newPassword);
}
