package com.bipros.security.infrastructure.jwt;

import com.bipros.security.domain.model.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes());
    }

    public String generateAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        List<String> roles = user.getRoles().stream()
                .map(userRole -> userRole.getRole().getName())
                .collect(Collectors.toList());
        claims.put("roles", roles);

        return buildToken(claims, user.getUsername(), jwtProperties.getAccessTokenExpiration());
    }

    public String generateRefreshToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        return buildToken(claims, user.getUsername(), jwtProperties.getRefreshTokenExpiration());
    }

    private String buildToken(Map<String, Object> claims, String subject, long expirationMs) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public String getUsernameFromToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Failed to extract username from token", e);
            throw new RuntimeException("Invalid token");
        }
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token is expired");
        } catch (MalformedJwtException e) {
            log.warn("Invalid JWT token");
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT token validation failed");
        }
        return false;
    }

    public long getAccessTokenExpirationMs() {
        return jwtProperties.getAccessTokenExpiration();
    }
}
