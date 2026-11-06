package com.visionmapping.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String INSECURE_DEFAULT_SECRET = "vision-mapping-development-secret-key-change-before-production";
    private static final Set<String> PROFILES_ALLOWING_DEFAULT_SECRET = Set.of("local", "test");

    private final SecretKey secretKey;
    private final long expirationMinutes;

    public JwtService(
            @Value("${app.security.jwt-secret}") String jwtSecret,
            @Value("${app.security.jwt-expiration-minutes}") long expirationMinutes,
            Environment environment
    ) {
        validateSecret(jwtSecret, environment);
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    private static void validateSecret(String jwtSecret, Environment environment) {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET must be set and non-blank.");
        }
        boolean allowsDefault = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(PROFILES_ALLOWING_DEFAULT_SECRET::contains);
        if (!allowsDefault && jwtSecret.equals(INSECURE_DEFAULT_SECRET)) {
            throw new IllegalStateException(
                    "JWT_SECRET is still set to the built-in development default. "
                            + "Set a unique JWT_SECRET environment variable before running outside the local/test profiles.");
        }
    }

    public String generateToken(AppUserPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(principal.getUsername())
                .claim("userId", principal.getId())
                .claim("role", principal.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(secretKey)
                .compact();
    }

    public String extractUsername(String token) {
        return claims(token).getSubject();
    }

    public boolean isValid(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        // SEC-01-05: a token issued before the account was disabled stops working
        // on its next request, not when it expires.
        return userDetails.isEnabled()
                && username.equals(userDetails.getUsername())
                && claims(token).getExpiration().after(new Date());
    }

    private Claims claims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
