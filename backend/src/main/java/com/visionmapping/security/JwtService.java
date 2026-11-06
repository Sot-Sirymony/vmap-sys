package com.visionmapping.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
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
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String INSECURE_DEFAULT_SECRET = "vision-mapping-development-secret-key-change-before-production";
    private static final Set<String> PROFILES_ALLOWING_DEFAULT_SECRET = Set.of("local", "test");
    static final String ISSUER = "vision-mapping";
    static final String SESSION_CLAIM = "sid";

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

    /**
     * V10 (SEC-07-04, SEC-07-09): a short-lived access token for one session.
     * The role is not trusted from the token: it is read from the database on
     * every request, so a role change applies at once.
     */
    public String generateToken(AppUserPrincipal principal, Long sessionId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(principal.getUsername())
                .claim("userId", principal.getId())
                .claim(SESSION_CLAIM, sessionId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /** Seconds an access token lasts, for the client to plan its refresh. */
    public long expiresInSeconds() {
        return expirationMinutes * 60;
    }

    /**
     * Verifies and reads an access token. The signature must be HS256 with this
     * app's key (so "alg: none" and other keys are refused), the issuer must be
     * this app, and the token must carry an expiry, a subject and a session.
     *
     * @throws JwtException when any of that is not true, or the token expired
     */
    public AccessToken parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(ISSUER)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Long sessionId = claims.get(SESSION_CLAIM, Long.class);
        if (claims.getExpiration() == null || claims.getSubject() == null || sessionId == null) {
            throw new MalformedJwtException("The token is missing its expiry, subject or session.");
        }
        return new AccessToken(claims.getSubject(), sessionId);
    }

    public record AccessToken(String email, Long sessionId) {
    }
}
