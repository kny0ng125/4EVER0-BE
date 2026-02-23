package com.team4ever.backend.global.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys; // Key 생성을 위한 유틸리티
import jakarta.annotation.PostConstruct; // Spring Boot 3 (javax -> jakarta)
import lombok.extern.slf4j.Slf4j; // 로깅 추가 권장
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.Base64;

@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${app.auth.tokenSecret}")
    private String secretKey;

    @Value("${app.auth.accessTokenExpirationMsec}")
    private long accessTokenValidity;

    @Value("${app.auth.refreshTokenExpirationMsec}")
    private long refreshTokenValidity;

    private Key key;

    // 객체 초기화 시 Secret Key 생성 (성능 최적화)
    @PostConstruct
    protected void init() {
        // Secret Key가 Base64로 인코딩되어 있다고 가정하거나,
        // 평문이라면 getBytes()를 사용하되 길이는 충분해야 함 (HS512의 경우 64byte 이상)
        byte[] keyBytes = Base64.getEncoder().encode(secretKey.getBytes());
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String createAccessToken(String userId) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + accessTokenValidity);

        return Jwts.builder()
                .setSubject(userId)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(key, SignatureAlgorithm.HS512) // 변경: Key 객체 사용
                .compact();
    }

    public String createRefreshToken(String userId) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + refreshTokenValidity);

        return Jwts.builder()
                .setSubject(userId)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(key, SignatureAlgorithm.HS512) // 변경: Key 객체 사용
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            // 변경: parserBuilder 사용
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.info("잘못된 JWT 서명입니다.");
        } catch (ExpiredJwtException e) {
            log.info("만료된 JWT 토큰입니다.");
        } catch (UnsupportedJwtException e) {
            log.info("지원되지 않는 JWT 토큰입니다.");
        } catch (IllegalArgumentException e) {
            log.info("JWT 토큰이 잘못되었습니다.");
        }
        return false;
    }

    public String getUserId(String token) {
        // 변경: parserBuilder 사용
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }
}