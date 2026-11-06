package com.visionmapping.service;

import com.visionmapping.security.audit.SecurityOutcome;
import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityEventService;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.AuthSession;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.exception.SessionEndedException;
import com.visionmapping.exception.SessionSupersededException;
import com.visionmapping.repository.AuthSessionRepository;
import com.visionmapping.security.AppUserPrincipal;
import com.visionmapping.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V10 (SEC-07-03, SEC-07-04): server-side sessions behind short access tokens.
 *
 * <p>Signing in opens a session and returns two tokens. The access token is a
 * JWT that lasts {@code app.security.jwt-expiration-minutes} (30 by default)
 * and names its session; the client keeps it in memory. The refresh token is a
 * random 256-bit value the client keeps in browser storage and trades for a new
 * pair when the access token runs out. Only its SHA-256 is stored.
 *
 * <p><b>Rotation and reuse.</b> Each refresh replaces the refresh token. If a
 * replaced token is used again within {@code refresh-grace-seconds}, it is
 * almost always two tabs refreshing at once: the caller gets 409 and retries
 * with the newer token another tab saved. Later than that, the token was
 * copied: the session is ended, which also stops its access tokens on their
 * next request (JwtAuthenticationFilter checks the session every time).
 *
 * <p>Why not an HttpOnly cookie: the frontend and API are on separate
 * onrender.com subdomains, which browsers treat as different sites (the domain
 * is on the Public Suffix List), so the cookie would be a blocked third-party
 * cookie. See BRD V10, SEC-07-05.
 */
@Slf4j
@Service
public class AuthSessionService {

    static final String REASON_LOGOUT = "LOGOUT";
    static final String REASON_PASSWORD_CHANGED = "PASSWORD_CHANGED";
    static final String REASON_PASSWORD_RESET = "PASSWORD_RESET";
    static final String REASON_REUSED = "REFRESH_TOKEN_REUSED";

    private static final int TOKEN_BYTES = 32;

    private final AuthSessionRepository repository;
    private final JwtService jwtService;
    private final SecurityEventService securityEvents;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration rememberedLifetime;
    private final Duration sessionLifetime;
    private final Duration refreshGrace;

    public AuthSessionService(
            AuthSessionRepository repository,
            JwtService jwtService,
            SecurityEventService securityEvents,
            Clock clock,
            @Value("${app.security.session.remember-me-days:30}") long rememberMeDays,
            @Value("${app.security.session.session-hours:12}") long sessionHours,
            @Value("${app.security.session.refresh-grace-seconds:30}") long refreshGraceSeconds) {
        this.repository = repository;
        this.jwtService = jwtService;
        this.securityEvents = securityEvents;
        this.clock = clock;
        this.rememberedLifetime = Duration.ofDays(rememberMeDays);
        this.sessionLifetime = Duration.ofHours(sessionHours);
        this.refreshGrace = Duration.ofSeconds(refreshGraceSeconds);
    }

    /** The two tokens a sign-in or refresh hands back. */
    public record Tokens(String accessToken, String refreshToken, long expiresInSeconds, AppUser user) {
    }

    /** Opens a session for a user who has just proved who they are. */
    @Transactional
    public Tokens open(AppUser user, boolean rememberMe) {
        Instant now = clock.instant();
        String refreshToken = newToken();
        AuthSession session = repository.save(AuthSession.builder()
                .user(user)
                .refreshTokenHash(hash(refreshToken))
                .rememberMe(rememberMe)
                .expiresAt(now.plus(rememberMe ? rememberedLifetime : sessionLifetime))
                .lastUsedAt(now)
                .build());
        return tokens(session, refreshToken);
    }

    /**
     * Trades a refresh token for a new pair.
     *
     * @throws SessionSupersededException (409) if the token was replaced a moment ago
     * @throws SessionEndedException (401) if the token does not open a session
     */
    @Transactional(noRollbackFor = SessionEndedException.class)
    public Tokens refresh(String refreshToken) {
        Instant now = clock.instant();
        String presented = hash(refreshToken);
        Optional<AuthSession> current = repository.findByRefreshTokenHash(presented);
        if (current.isPresent()) {
            AuthSession session = current.get();
            if (!session.isActive(now) || session.getUser().getStatus() != UserStatus.ACTIVE) {
                throw new SessionEndedException();
            }
            String next = newToken();
            session.setPreviousTokenHash(session.getRefreshTokenHash());
            session.setRefreshTokenHash(hash(next));
            session.setRotatedAt(now);
            session.setLastUsedAt(now);
            return tokens(session, next);
        }

        AuthSession replaced = repository.findFirstByPreviousTokenHash(presented)
                .orElseThrow(SessionEndedException::new);
        if (!replaced.isActive(now)) {
            throw new SessionEndedException();
        }
        if (replaced.getRotatedAt() != null && replaced.getRotatedAt().plus(refreshGrace).isAfter(now)) {
            throw new SessionSupersededException();
        }
        replaced.setRevokedAt(now);
        replaced.setRevokedReason(REASON_REUSED);
        log.warn("Refresh token reuse detected; session {} of user {} ended", replaced.getId(), replaced.getUser().getId());
        securityEvents.recordFor(replaced.getUser().getId(), SecurityEventType.REFRESH_TOKEN_REUSED, SecurityOutcome.DENIED,
                "session", replaced.getId(), "session ended");
        throw new SessionEndedException();
    }

    /** Signing out: ends the session the refresh token belongs to. Unknown tokens are ignored. */
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        Instant now = clock.instant();
        repository.findByRefreshTokenHash(hash(refreshToken))
                .filter(session -> session.getRevokedAt() == null)
                .ifPresent(session -> {
                    session.setRevokedAt(now);
                    session.setRevokedReason(REASON_LOGOUT);
                    securityEvents.recordFor(session.getUser().getId(), SecurityEventType.LOGGED_OUT, SecurityOutcome.SUCCESS,
                            "session", session.getId(), null);
                });
    }

    /** After a password change: every other session ends, the one making the change stays. */
    @Transactional
    public int endOtherSessions(Long userId, Long keepSessionId) {
        return repository.revokeAllForUser(userId, keepSessionId, REASON_PASSWORD_CHANGED, clock.instant());
    }

    /** After a password reset: every session ends. */
    @Transactional
    public int endAllSessions(Long userId) {
        return repository.revokeAllForUser(userId, null, REASON_PASSWORD_RESET, clock.instant());
    }

    /**
     * For JwtAuthenticationFilter: the session an access token names, with its
     * user, when it is still open.
     */
    @Transactional(readOnly = true)
    public Optional<AuthSession> activeSession(Long sessionId) {
        Instant now = clock.instant();
        return repository.findWithUserById(sessionId).filter(session -> session.isActive(now));
    }

    /** Daily: rows for sessions that ended over a week ago are no longer needed. */
    @Scheduled(cron = "${app.security.session.cleanup-cron:0 30 3 * * *}")
    @Transactional
    public void deleteOldSessions() {
        int deleted = repository.deleteEndedBefore(clock.instant().minus(Duration.ofDays(7)));
        if (deleted > 0) {
            log.info("Deleted {} ended sign-in sessions", deleted);
        }
    }

    private Tokens tokens(AuthSession session, String refreshToken) {
        AppUser user = session.getUser();
        String access = jwtService.generateToken(new AppUserPrincipal(user, session.getId()), session.getId());
        return new Tokens(access, refreshToken, jwtService.expiresInSeconds(), user);
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
