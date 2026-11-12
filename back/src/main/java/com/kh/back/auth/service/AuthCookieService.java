package com.kh.back.auth.service;

import jakarta.servlet.http.HttpServletResponse;

// 호출자는 업무 인터페이스를 사용하고 구현체가 처리 흐름을 담당한다.
public interface AuthCookieService {
    void setAccessToken(HttpServletResponse response, String token, boolean persistent);

    void setRefreshToken(HttpServletResponse response, String token);

    void clearAccessToken(HttpServletResponse response);

    void clearRefreshToken(HttpServletResponse response);

    void clearAuthenticationCookies(HttpServletResponse response);
}
