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
                        .requestMatchers("/api/v1/admin/account-locks", "/api/v1/admin/account-locks/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/users/me", "/api/v1/users/me/**").authenticated()
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
