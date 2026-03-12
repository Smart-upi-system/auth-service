package com.uws.auth_service.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration:3600000}") // 1 hour
    private Long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration:2592000000}") //30 days
    private Long refreshTokenExpiration;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(String userId, String username, String email, String role) {
        Map<String,Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("name", username);  // Gateway extracts this as "X-Username"
        claims.put("email", email);
        claims.put("role", role);
        claims.put("type", "access");

        long now = System.currentTimeMillis();

        String token = Jwts.builder()
                .claims(claims)
                .subject(userId)
                .issuedAt(new Date(now))
                .expiration(new Date(now + accessTokenExpiration))
                .signWith(getSigningKey())
                .compact();

        log.debug("Generated access token for user: {}", userId);
        return token;
    }

    public String generateRefreshToken(String userId) {
        Map<String,Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("type", "refresh");

        long now = System.currentTimeMillis();

        String token = Jwts.builder()
                .claims(claims)
                .subject(userId)
                .issuedAt(new Date(now))
                .expiration(new Date(now + refreshTokenExpiration))
                .signWith(getSigningKey())
                .compact();

        log.debug("Generated refresh token for user: {}", userId);
        return token;
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenExpired(String token) {
        try {
            return extractClaims(token).getExpiration().before(new Date());
        } catch (Exception e) {
            log.error("Error checking token expiration: {}", e.getMessage());
            return true;
        }
    }

    public boolean validateToken(String token) {
        try {
            extractClaims(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            log.error("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean validateTokenType(String token, String expectedType) {
        try {
            String type = extractClaims(token).get("type", String.class);
            return expectedType.equals(type) && validateToken(token);
        } catch (Exception e) {
            log.error("Token type validation failed: {}", e.getMessage());
            return false;
        }
    }

    public Long getAccessTokenExpirationSeconds() {
        return accessTokenExpiration / 1000;
    }

    public Long getRefreshTokenExpirationSeconds() {
        return refreshTokenExpiration / 1000;
    }
}
