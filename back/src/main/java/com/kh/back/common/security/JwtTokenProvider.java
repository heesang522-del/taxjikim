package com.kh.back.common.security;

import com.kh.back.auth.dto.RefreshTokenDto;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Getter // 🎯 Lombok @Getter 적용 (refreshTokenExpiration 등의 getter 자동 생성)
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration
    ) {
        // 원본 프로젝트와 동일하게 설정 문자열의 UTF-8 바이트를 서명키로 사용한다.
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    // Access Token 생성
    public String generateAccessToken(Long userId, String sessionId) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("sid", sessionId)
                .claim("type", "access")
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    // JwtTokenProvider 내 메서드 수정
    public RefreshTokenDto generateRefreshToken(Long userId, String sessionId) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + refreshTokenExpiration);
        String jti = UUID.randomUUID().toString();

        String refreshToken = Jwts.builder()
                .subject(userId.toString())
                .id(jti)
                .claim("sid", sessionId)
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();

        // � 토큰 문자열과 jti를 같이 묶어서 반환
        return new RefreshTokenDto(refreshToken, jti);
    }

    // Refresh Token에서 jti 추출
    public String getJtiFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getId();
    }

    // JWT 검증
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            getUserId(token);
            return true;
        } catch (SecurityException | SignatureException e) {
            log.error("잘못된 JWT 서명입니다.");
        } catch (ExpiredJwtException e) {
            log.error("만료된 JWT 토큰입니다.");
        } catch (UnsupportedJwtException e) {
            log.error("지원되지 않는 JWT 토큰입니다.");
        } catch (IllegalArgumentException e) {
            log.error("JWT 토큰이 비어있거나 잘못되었습니다.");
        } catch (MalformedJwtException e) {
            log.debug("JWT 형식이 올바르지 않습니다.");
        }
        return false;
    }

    public Long getUserId(String token) {
        Claims claims = getClaims(token);
        if (claims.containsKey("role")) {
            throw new IllegalArgumentException("기존 형식의 토큰입니다. 다시 로그인해 주세요.");
        }
        Long userId = Long.valueOf(claims.getSubject());
        if (userId <= 0) throw new IllegalArgumentException("유효하지 않은 회원 번호입니다.");
        return userId;
    }
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getSessionId(String token) {
        return getClaims(token).get("sid", String.class);
    }

    public boolean isTokenType(String token, String type) {
        return type.equals(getClaims(token).get("type", String.class));
    }
}
