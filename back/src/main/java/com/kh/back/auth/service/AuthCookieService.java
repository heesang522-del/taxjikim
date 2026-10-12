package com.kh.back.auth.service;

import com.kh.back.common.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthCookieService {
    private final JwtTokenProvider jwtTokenProvider;

    public void setAccessToken(HttpServletResponse response, String token, boolean persistent) {
        ResponseCookie.ResponseCookieBuilder cookie = cookie("accessToken", token);
        if (persistent) cookie.maxAge(jwtTokenProvider.getAccessTokenExpiration() / 1000);
        write(response, cookie);
    }

    public void setRefreshToken(HttpServletResponse response, String token) {
        write(response, cookie("refreshToken", token)
                .maxAge(jwtTokenProvider.getRefreshTokenExpiration() / 1000));
    }

    public void clearAccessToken(HttpServletResponse response) {
        write(response, cookie("accessToken", "").maxAge(0));
    }

    public void clearRefreshToken(HttpServletResponse response) {
        write(response, cookie("refreshToken", "").maxAge(0));
    }

    public void clearAuthenticationCookies(HttpServletResponse response) {
        clearAccessToken(response);
        clearRefreshToken(response);
    }

    private ResponseCookie.ResponseCookieBuilder cookie(String name, String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true).secure(true).path("/").sameSite("Lax");
    }

    private void write(HttpServletResponse response, ResponseCookie.ResponseCookieBuilder cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.build().toString());
    }
}
