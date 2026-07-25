package com.seongho.fds.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component

// 토큰 생성/검증/파싱
public class JwtUtil {

    private final SecretKey secretKey;  // HMAC-SHA 서명 키
    private final long expiration;      // 만료 시간(ms)

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    // JWT 문자열 생성
    public String generateToken(String username, String role) {
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(secretKey)
                .compact();
    }

    // 토큰에서 username 추출
    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    // 토큰에서 role 추출
    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    // 유효한 토큰인지 검증
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public long getExpirationTime(String token) {
        return parseClaims(token).getExpiration().getTime();
    }

    // 토큰 디코딩 (private)
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
