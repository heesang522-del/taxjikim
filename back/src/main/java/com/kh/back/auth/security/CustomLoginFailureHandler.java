package com.kh.back.auth.security;

import com.kh.back.user.dto.UserDto;
import com.kh.back.user.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class CustomLoginFailureHandler implements AuthenticationFailureHandler {

    private final UserService userService;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {

        String loginId = request.getParameter("loginId");
        String message = exception.getMessage();

        // 1. DORMANT (휴면 계정)
        if ("DORMANT".equals(message)) {
            UserDto user = userService.getUserByLoginId(loginId);
            if (user != null) {
                String redirectUrl = String.format("/auth/unlock-dormant?loginId=%s&email=%s",
                        URLEncoder.encode(user.getLoginId(), StandardCharsets.UTF_8),
                        URLEncoder.encode(user.getEmail(), StandardCharsets.UTF_8));
                response.sendRedirect(redirectUrl);
                return;
            }
        }

        // 2. DELETED (탈퇴 대기 계정)
        if ("DELETED".equals(message)) {
            UserDto user = userService.getUserByLoginId(loginId);
            if (user != null) {
                String redirectUrl = String.format("/auth/restore-account?loginId=%s&email=%s",
                        URLEncoder.encode(user.getLoginId(), StandardCharsets.UTF_8),
                        URLEncoder.encode(user.getEmail(), StandardCharsets.UTF_8));
                response.sendRedirect(redirectUrl);
                return;
            }
        }

        // 3. LOCKED (계정 잠금)
        if ("LOCKED".equals(message)) {
            String errorMsg = URLEncoder.encode("보안을 위해 계정이 잠겼습니다. 이메일로 발송된 해제 링크를 확인해 주세요.", StandardCharsets.UTF_8);
            response.sendRedirect("/auth/login?error=true&exception=" + errorMsg);
            return;
        }

        // 4. BANNED (이용 정지 계정)
        if ("BANNED".equals(message)) {
            String errorMsg = URLEncoder.encode("운영 정책 위반으로 이용이 정지된 계정입니다.", StandardCharsets.UTF_8);
            response.sendRedirect("/auth/login?error=true&exception=" + errorMsg);
            return;
        }

        // 5. 일반 로그인 실패
        String errorMsg = URLEncoder.encode(message, StandardCharsets.UTF_8);
        response.sendRedirect("/auth/login?error=true&exception=" + errorMsg);
    }
}
