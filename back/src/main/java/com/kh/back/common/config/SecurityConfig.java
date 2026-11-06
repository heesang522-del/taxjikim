package com.kh.back.common.config;

import com.kh.back.auth.security.LoginSessionRepository;
import com.kh.back.auth.service.AuthCookieService;
import com.kh.back.auth.service.AuthService;
import com.kh.back.common.dto.ApiResponse;
import com.kh.back.common.security.JwtAuthenticationFilter;
import com.kh.back.common.security.JwtTokenProvider;
import com.kh.back.common.util.ApiResponseWriter;
import com.kh.back.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
    private final UserService userService;
    private final AuthService authService;
    private final LoginSessionRepository loginSessionRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthCookieService authCookieService;
    private final ApiResponseWriter apiResponseWriter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // JWT 인증은 Security 체인 안에서만 실행하고, 서버 세션은 만들지 않는다.
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtTokenProvider, userService, authService,
                loginSessionRepository, authCookieService, apiResponseWriter);
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives(
                                        "script-src 'self'; " +
                                                "object-src 'none'; " +
                                                "base-uri 'self';"
                                )
                        )
                )
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .authorizeHttpRequests(auth -> auth
                        // 관리자 전용 API는 전부 /api/v1/admin/** 아래에 두므로 한 규칙으로 막는다.
                        // 새 관리자 API를 추가할 때 권한 규칙을 깜빡 빠뜨리는 실수를 막기 위한 것이다.
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/users/me", "/api/v1/users/me/**").authenticated()
                        // 결과 JSON 저장·조회는 로그인 사용자만 접근한다.
                        .requestMatchers("/api/v1/tax/inputs/*/result", "/api/v1/tax/inputs/saved-results").authenticated()
                        .requestMatchers("/api/v1/tax/simulations", "/api/v1/tax/simulations/**").authenticated()
                        .requestMatchers("/api/v1/tax/inputs/simulation").authenticated()
                        .requestMatchers("/ws/chat/**").authenticated()
                        // 알림·1:1 상담 채팅은 본인 로그인 없이는 의미가 없는 API라 전부 인증을 요구한다.
                        .requestMatchers("/api/v1/notifications/**").authenticated()
                        .requestMatchers("/api/v1/chat/**").authenticated()
                        .requestMatchers("/api/v1/auth/session").authenticated()
                        // 댓글 목록 조회는 비로그인도 허용하고, 쓰기 동작만 인증을 요구한다.
                        .requestMatchers(HttpMethod.POST, "/api/v1/posts/*/comments").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/posts/*/comments/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/posts/*/comments/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/posts/*/comments/*/reports").authenticated()
                        // 게시글 목록·조회는 비로그인도 허용하고(비공개 QNA 필터링은 PostValidator가 처리),
                        // 업로드·작성·수정·삭제만 인증을 요구한다.
                        .requestMatchers(HttpMethod.POST, "/api/v1/posts/community/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/posts/community/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> apiResponseWriter.write(response, 401,
                                ApiResponse.fail("AUTHENTICATION_REQUIRED", "로그인이 필요합니다.")))
                        .accessDeniedHandler((request, response, exception) -> apiResponseWriter.write(response, 403,
                                ApiResponse.fail("ACCESS_DENIED", "접근 권한이 없습니다."))))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
