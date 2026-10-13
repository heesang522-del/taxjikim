package com.kh.back.common.config;

import com.kh.back.auth.security.CustomAuthenticationProvider;
import com.kh.back.auth.security.LoginSessionRepository;
import com.kh.back.auth.service.AuthCookieService;
import com.kh.back.auth.service.AuthService;
import com.kh.back.common.security.JwtAuthenticationFilter;
import com.kh.back.common.security.JwtTokenProvider;
import com.kh.back.user.service.UserService;
import jakarta.servlet.http.Cookie;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomAuthenticationProvider customAuthenticationProvider;
    private final UserService userService;
    private final AuthService authService;

    // Redis 서비스 및 JWT 토큰 프로바이더 주입
    private final LoginSessionRepository loginSessionRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthCookieService authCookieService;

    // 💡 2. JwtAuthenticationFilter를 여기서 직접 Bean으로 생성
    // (JwtAuthenticationFilter.java 클래스의 @Component 어노테이션은 반드시 삭제되어 있어야 합니다!)
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider, userService, authService, loginSessionRepository, authCookieService);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // 1. 기본 보안 옵션 비활성화
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                // CSP 설정 추가
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives(
                                        "script-src 'self'; " +
                                                "object-src 'none'; " +
                                                "base-uri 'self';"
                                )
                        )
                )


                // 2. 세션 미사용 및 Context 저장 차단 (Stateless 완벽 적용)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .securityContext(context -> context
                        .securityContextRepository(new NullSecurityContextRepository())
                )

                // 3. [필수] 미인증 접근 시 Spring Security의 자동 세션 캐싱 차단
                .requestCache(cache -> cache
                        .requestCache(new NullRequestCache())
                )

                // 3. Request URL 권한 설정
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/css/**", "/js/**", "/images/**", "/uploads/**", "/error"
                        ).permitAll()
                        .requestMatchers(
                                "/user/update**", "/user/delete", "/user/mypage", "/user/settings"
                        ).authenticated()
                        .requestMatchers(
                                "/", "/api/**",
                                "/auth/**", "/user/**"
                        ).permitAll()
                        .requestMatchers(
                                "/index"
                        ).authenticated()
                        .anyRequest().permitAll()
                )

                // 4. 로그아웃 설정 (기존 URL: /user/logout 유지, POST 요청 시에만 실행)
                .logout(logout -> logout
                        .logoutUrl("/user/logout")
                        .logoutSuccessUrl("/")
                        .clearAuthentication(true)
                        .addLogoutHandler((request, response, authentication) -> {
                            if (!"POST".equalsIgnoreCase(request.getMethod())) {
                                return;
                            }

                            Cookie[] cookies = request.getCookies();
                            if (cookies != null) {
                                for (Cookie cookie : cookies) {
                                    if ("refreshToken".equals(cookie.getName()) || "accessToken".equals(cookie.getName())) {
                                        String token = cookie.getValue();

                                        if (jwtTokenProvider.validateToken(token)) {
                                            Long userId = jwtTokenProvider.getUserId(token);
                                            loginSessionRepository.endSessionIfCurrent(userId, jwtTokenProvider.getSessionId(token));
                                        }

                                        authCookieService.clearRefreshToken(response);
                                    }

                                    if ("accessToken".equals(cookie.getName())) {
                                        authCookieService.clearAccessToken(response);
                                    }
                                }
                            }
                        })
                )

                // 💡 3. 주입받은 필드 대신 메서드 호출(jwtAuthenticationFilter())로 필터 등록!
                .addFilterBefore(
                        jwtAuthenticationFilter(),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(customAuthenticationProvider);
    }
}
