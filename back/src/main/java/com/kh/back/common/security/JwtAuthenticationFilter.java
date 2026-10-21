package com.kh.back.common.security;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.auth.security.LoginSessionRepository;
import com.kh.back.auth.service.AuthCookieService;
import com.kh.back.auth.service.AuthService;
import com.kh.back.user.dto.UserDto;
import com.kh.back.user.service.UserService;
import com.kh.back.common.dto.ApiResponse;
import com.kh.back.common.util.ApiResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Matcher;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;
    private final AuthService authService;
    private final LoginSessionRepository loginSessionRepository;
    private final AuthCookieService authCookieService;
    private final ApiResponseWriter apiResponseWriter;

    // � [추가] 정적 파일(.css, .js, .png 등) 및 파비콘 요청은 JWT 필터 검사를 생략함
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();

        // 재발급은 컨트롤러에서 한 번만 처리
            if ("/api/v1/auth/account-unlocks".equals(path)
                    || "/api/v1/auth/tokens".equals(path) || "/api/v1/auth/sessions".equals(path)
                    || "/api/v1/auth/sessions/current".equals(path)) {
                return true;
            }
            // 공개 인증 절차는 이전 세션 쿠키가 남아 있어도 진행할 수 있어야 한다.
            if ("/api/v1/users".equals(path) || "/api/v1/users/check-id".equals(path)
                    || "/api/v1/users/check-nickname".equals(path) || "/api/v1/users/check-email".equals(path)
                    || "/api/v1/email-verifications".equals(path)
                    || "/api/v1/auth/account-lookups".equals(path)
                    || "/api/v1/auth/account-lookups/email-verifications".equals(path)
                    || "/api/v1/auth/password-resets".equals(path)
                    || "/api/v1/auth/password-resets/email-verifications".equals(path)) {
                return true;
        }
        // Matcher matcher = ();

        return path.endsWith(".css") ||
                path.endsWith(".js") ||
                path.endsWith(".png") ||
                path.endsWith(".jpg") ||
                path.endsWith(".ico") ||
                path.startsWith("/css/") ||
                path.startsWith("/js/") ||
                path.startsWith("/images/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String accessToken = resolveAccessToken(request);
        String refreshToken = resolveCookieToken(request, "refreshToken");

        // 1. Access Token이 유효한 경우 -> 정상 인증
        if (accessToken != null && jwtTokenProvider.validateToken(accessToken)) {
            Long userId = jwtTokenProvider.getUserId(accessToken);
            if (!jwtTokenProvider.isTokenType(accessToken, "access")
                    || !loginSessionRepository.isCurrentSession(userId, jwtTokenProvider.getSessionId(accessToken))) {
                rejectSession(request, response, "SESSION_REPLACED");
                return;
            }
            setAuthenticationToContext(userId);
        }
        // 2. Access Token은 만료/없지만 Refresh Token 쿠키가 있는 경우 -> 자동 로그인(reissue)
        else if (refreshToken != null) {
            try {
                String newAccessToken = authService.reissue(refreshToken, response);

                Long userId = jwtTokenProvider.getUserId(newAccessToken);
                setAuthenticationToContext(userId);
            } catch (AuthenticationException e) {
                rejectSession(request, response,
                        "SESSION_REPLACED".equals(e.getMessage()) ? "SESSION_REPLACED" : "SESSION_EXPIRED");
                return;
            }
        } else {
            // refresh token 만료시
            // 만료
        }

        filterChain.doFilter(request, response);
    }

    private void rejectSession(HttpServletRequest request, HttpServletResponse response, String code)
            throws IOException {
        SecurityContextHolder.clearContext();
        authCookieService.clearAuthenticationCookies(response);
        if (request.getServletPath().startsWith("/api/")) {
            String message = "SESSION_REPLACED".equals(code)
                    ? "다른 기기에서 로그인했거나 현재 로그인이 종료되었습니다."
                    : "로그인이 만료되었습니다. 다시 로그인해 주세요.";
            apiResponseWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, ApiResponse.fail(code, message));
        } else {
            response.sendRedirect(request.getContextPath() + "/auth/login?reason=" + code);
        }
    }

    private String resolveAccessToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return resolveCookieToken(request, "accessToken");
    }

    private String resolveCookieToken(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookieName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private void setAuthenticationToContext(Long userId) {
        UserDto userDto = userService.getUserById(userId);
        if (userDto != null && "ACTIVE".equals(userDto.getUserStatus())) {
            CustomUserDetails userDetails = new CustomUserDetails(userDto);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities());

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        }
    }
}
