package com.kh.back.auth.service;

import com.kh.back.auth.dto.LoginResponseDto;
import com.kh.back.auth.dto.RefreshTokenDto;
import com.kh.back.auth.security.CustomAuthenticationProvider;
import com.kh.back.auth.security.CustomUserDetails;
import com.kh.back.auth.security.LoginSessionRepository;
import com.kh.back.auth.security.LoginSessionRepository.RotationResult;
import com.kh.back.common.security.JwtTokenProvider;
import com.kh.back.user.dto.UserDto;
import com.kh.back.user.mapper.UserMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Cookie;
import com.kh.back.auth.mapper.UserSecurityMapper;
import com.kh.back.auth.security.LoginUnlockTokenService;
import com.kh.back.auth.security.LoginAttemptService;
import com.kh.back.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import com.kh.back.common.validation.InputValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final CustomAuthenticationProvider customAuthenticationProvider;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginSessionRepository loginSessionRepository;
    private final AuthCookieService authCookieService;
    private final UserSecurityMapper userSecurityMapper;
    private final LoginUnlockTokenService loginUnlockTokenService;
    private final LoginAttemptService loginAttemptService;

    /**
     * 회원 인증 후 현재 로그인을 교체하고, 선택한 로그인 방식에 맞춰 쿠키를 설정합니다.
     */
    @Transactional
    @Override
    public LoginResponseDto login(String loginId, String password, boolean rememberMe,
                               HttpServletResponse response) {
        // 1. 입력값 검증
        InputValidator.requireText("loginId", loginId);
        InputValidator.requireText("password", password);

        // 2. 회원 인증 및 현재 요청의 인증 정보 설정
        Authentication authentication = customAuthenticationProvider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(loginId, password)
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        UserDto userDto = userDetails.getUserDto();
        String role = userDto.getUserRole();

        // 3. 마지막 로그인 시간 갱신
        userMapper.updateLastLoginAt(userDto.getLoginId());

        // 4. 로그인 번호 생성. 리프레시 토큰은 자동 로그인 선택 시에만 생성
        String sessionId = UUID.randomUUID().toString();
        RefreshTokenDto refreshTokenDto = null;
        String refreshTokenId = null;
        long sessionExpiration = jwtTokenProvider.getAccessTokenExpiration();

        if (rememberMe) {
            refreshTokenDto = jwtTokenProvider.generateRefreshToken(userDto.getUserId(), sessionId);
            refreshTokenId = refreshTokenDto.getJti();
            sessionExpiration = jwtTokenProvider.getRefreshTokenExpiration();
        }

        String accessToken = jwtTokenProvider.generateAccessToken(userDto.getUserId(), sessionId);

        // 5. 새 로그인을 현재 로그인으로 저장
        loginSessionRepository.replaceCurrentSession(
                userDto.getUserId(), sessionId, refreshTokenId, sessionExpiration);

        // 6. 쿠키 설정. 자동 로그인 미선택 시 이전 리프레시 쿠키 삭제
        authCookieService.setAccessToken(response, accessToken, rememberMe);
        if (rememberMe) {
            authCookieService.setRefreshToken(response, refreshTokenDto.getRefreshToken());
        } else {
            authCookieService.clearRefreshToken(response);
        }

        return new LoginResponseDto(
                accessToken,
                "Bearer",
                userDto.getUserId(),
                userDto.getLoginId(),
                role
        );
    }

    /**
     * 리프레시 토큰을 검증하고 같은 로그인 번호를 유지하며 토큰을 교체합니다.
     * Redis 교체에 성공한 경우에만 새 토큰을 전달합니다.
     */
    @Transactional
    @Override
    public String reissue(String refreshToken, HttpServletResponse response) {
        // 1. 서명과 만료 시간 확인
        if (!StringUtils.hasText(refreshToken) || !jwtTokenProvider.validateToken(refreshToken)) {
            throw new BadCredentialsException("유효하지 않거나 만료된 Refresh Token입니다.");
        }

        // 2. 토큰 정보 추출 및 종류와 필수 값 확인
        Long userId = jwtTokenProvider.getUserId(refreshToken);
        UserDto user = userMapper.findById(userId);
        if (user == null || !"ACTIVE".equals(user.getUserStatus())) {
            throw new BadCredentialsException("이용할 수 없는 계정입니다.");
        }
        String jti = jwtTokenProvider.getJtiFromToken(refreshToken);
        String sessionId = jwtTokenProvider.getSessionId(refreshToken);
        if (!jwtTokenProvider.isTokenType(refreshToken, "refresh")
                || !StringUtils.hasText(sessionId) || !StringUtils.hasText(jti)) {
            throw new BadCredentialsException("다시 로그인해 주세요.");
        }

        // 3. 기존 로그인 번호로 새 토큰 준비
        String newAccessToken = jwtTokenProvider.generateAccessToken(userId, sessionId);

        RefreshTokenDto refreshTokenDto = jwtTokenProvider.generateRefreshToken(userId, sessionId);
        String newRefreshToken = refreshTokenDto.getRefreshToken();
        String newJti = refreshTokenDto.getJti();

        // 4. Redis에서 토큰 확인과 교체. 실패 시 응답하지 않고 종료
        RotationResult rotationResult = loginSessionRepository.rotateRefreshToken(
                userId, sessionId, jti, newJti, jwtTokenProvider.getRefreshTokenExpiration());
        if (rotationResult == RotationResult.SESSION_ENDED) {
            throw new BadCredentialsException("SESSION_REPLACED");
        }
        if (rotationResult == RotationResult.TOKEN_REUSED) {
            throw new BadCredentialsException("토큰 탈취 위험이 감지되어 모든 세션이 만료되었습니다.");
        }

        // 5. 교체 성공 후 쿠키 설정 및 액세스 토큰 반환
        authCookieService.setRefreshToken(response, newRefreshToken);
        authCookieService.setAccessToken(response, newAccessToken, true);

        return newAccessToken;
    }

    /* ================= 아이디 / 비밀번호 찾기 ================= */

    @Override
    public void sendCodeForFindId(String name, String email) {
        String loginId = userMapper.findLoginIdByNameAndEmail(name, email);
        if (loginId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ACCOUNT_LOOKUP_REJECTED", "일치하는 회원 정보가 없습니다.");
        }
        emailVerificationService.sendVerificationCode(email);
    }

    @Override
    public void sendCodeForFindPw(String loginId, String email) {
        UserDto user = userMapper.findByLoginId(loginId);
        if (user == null || !email.equals(user.getEmail())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ACCOUNT_LOOKUP_REJECTED", "일치하는 회원 정보가 없습니다.");
        }
        emailVerificationService.sendVerificationCode(email);
    }

    @Transactional
    @Override
    public String findLoginId(String name, String email) {
        validateEmailVerification(email);

        String foundLoginId = userMapper.findLoginIdByNameAndEmail(name, email);
        if (foundLoginId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ACCOUNT_LOOKUP_REJECTED", "일치하는 회원 정보가 없습니다.");
        }

        emailVerificationService.removeVerification(email);
        return foundLoginId;
    }

    @Transactional
    @Override
    public void resetPassword(String loginId, String email, String newPassword) {
        InputValidator.requireText("loginId", loginId);
        InputValidator.requireText("email", email);
        InputValidator.requireText("newPassword", newPassword);
        // 인증한 이메일이 비밀번호를 바꾸려는 계정에 등록된 이메일인지 확인한다.
        UserDto user = userMapper.findByLoginId(loginId);
        if (user == null || !email.equals(user.getEmail())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_RESET_REJECTED", "비밀번호 재설정 정보를 확인해주세요.");
        }
        validateEmailVerification(email);

        String encodedPassword = passwordEncoder.encode(newPassword);
        // 조회 이후 이메일이 변경된 경우에도 다른 소유권 정보로 갱신하지 않는다.
        int updatedCount = userMapper.updatePasswordByEmail(loginId, email, encodedPassword);
        if (updatedCount != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "PASSWORD_RESET_CONFLICT", "계정 정보가 변경되었습니다. 다시 확인해주세요.");
        }

        // 실제 변경 성공 후에만 인증 완료 기록을 소비한다.
        emailVerificationService.removeVerification(email);
    }

    private void validateEmailVerification(String email) {
        if (!emailVerificationService.isVerified(email)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMAIL_NOT_VERIFIED", "이메일 인증이 완료되지 않았습니다.");
        }
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String authorization = request.getHeader("Authorization");
        // 명시적으로 전달된 Bearer 세션을 우선 종료하여 다른 쿠키 세션을 종료하지 않는다.
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            endSessionForToken(authorization.substring(7), "access");
        } else if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("refreshToken".equals(cookie.getName()) || "accessToken".equals(cookie.getName())) {
                    String token = cookie.getValue();
                    endSessionForToken(token, "accessToken".equals(cookie.getName()) ? "access" : "refresh");
                }
            }
        }
        authCookieService.clearAuthenticationCookies(response);
        SecurityContextHolder.clearContext();
    }

    private void endSessionForToken(String token, String type) {
        if (jwtTokenProvider.validateToken(token) && jwtTokenProvider.isTokenType(token, type)) {
            loginSessionRepository.endSessionIfCurrent(jwtTokenProvider.getUserId(token), jwtTokenProvider.getSessionId(token));
        }
    }

    @Transactional
    @Override
    public void unlockAccount(String token) {
        // 기존 이메일 링크의 토큰 확인·잠금 해제 순서를 그대로 유지한다.
        String loginId = loginUnlockTokenService.getLoginIdByToken(token);
        if (loginId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ACCOUNT_UNLOCK_REJECTED", "유효하지 않거나 만료된 해제 요청입니다.");
        }
        userSecurityMapper.unlockAccount(loginId);
        loginAttemptService.loginSucceeded(loginId);
        loginUnlockTokenService.invalidateToken(token);
    }
}
