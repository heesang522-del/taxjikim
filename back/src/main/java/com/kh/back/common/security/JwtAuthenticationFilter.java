package com.kh.back.common.security;

import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.auth.security.LoginSessionRepository;
import com.kh.back.auth.service.AuthCookieService;
import com.kh.back.auth.service.AuthService;
import com.kh.back.member.dto.MemberDto;
import com.kh.back.member.service.MemberService;
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

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final MemberService memberService;
    private final AuthService authService;
    private final LoginSessionRepository loginSessionRepository;
    private final AuthCookieService authCookieService;

    // 💡 [추가] 정적 파일(.css, .js, .png 등) 및 파비콘 요청은 JWT 필터 검사를 생략함
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getServletPath();

        // 재발급은 컨트롤러에서 한 번만 처리
        if ("/api/auth/reissue".equals(path) || "/api/auth/login".equals(path)
                || "/auth/login".equals(path)) {
            return true;
        }
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
            Long memberNumber = jwtTokenProvider.getMemberNumber(accessToken);
            if (!jwtTokenProvider.isTokenType(accessToken, "access")
                    || !loginSessionRepository.isCurrentSession(memberNumber, jwtTokenProvider.getSessionId(accessToken))) {
                rejectSession(request, response, "SESSION_REPLACED");
                return;
            }
            setAuthenticationToContext(memberNumber);
        }
        // 2. Access Token은 만료/없지만 Refresh Token 쿠키가 있는 경우 -> 자동 로그인(reissue)
        else if (refreshToken != null) {
            try {
                String newAccessToken = authService.reissue(refreshToken, response);

                Long memberNumber = jwtTokenProvider.getMemberNumber(newAccessToken);
                setAuthenticationToContext(memberNumber);
            } catch (AuthenticationException e) {
                rejectSession(request, response,
                        "SESSION_REPLACED".equals(e.getMessage()) ? "SESSION_REPLACED" : "SESSION_EXPIRED");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void rejectSession(HttpServletRequest request, HttpServletResponse response, String code)
            throws IOException {
        SecurityContextHolder.clearContext();
        authCookieService.clearAuthenticationCookies(response);
        if (request.getServletPath().startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            String message = "SESSION_REPLACED".equals(code)
                    ? "다른 기기에서 로그인했거나 현재 로그인이 종료되었습니다."
                    : "로그인이 만료되었습니다. 다시 로그인해 주세요.";
            response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
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

    private void setAuthenticationToContext(Long memberNumber) {
        MemberDto memberDto = memberService.getMemberByNumber(memberNumber);
        if (memberDto != null && "ACTIVE".equals(memberDto.getMemberStatus())) {
            CustomUserDetails userDetails = new CustomUserDetails(memberDto);
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
