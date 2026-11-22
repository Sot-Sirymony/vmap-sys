package com.visionmapping.service.twofactor;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * V21 (TF-NFR-11, D-21-2, D-21-4): two-factor sign-in settings. The HMAC
 * secret is never logged and has no public accessor.
 */
@Slf4j
@Component
public class TwoFactorProperties {

    private final boolean enabled;
    private final String secret;
    /** SEC-04-04: the secret before the last rotation, or null. */
    private final String previousSecret;
    private final Duration challengeLifetime;
    private final Duration trustedDeviceLifetime;
    private final Duration gracePeriod;

    /** For tests: no previous secret. */
    public TwoFactorProperties(boolean enabled, String secret, long challengeMinutes, long trustedDeviceDays, long graceDays) {
        this(enabled, secret, "", challengeMinutes, trustedDeviceDays, graceDays);
    }

    @Autowired
    public TwoFactorProperties(@Value("${app.two-factor.enabled:true}") boolean enabled,
                               @Value("${app.two-factor.secret}") String secret,
                               @Value("${app.two-factor.secret-previous:}") String previousSecret,
                               @Value("${app.two-factor.challenge-minutes:5}") long challengeMinutes,
                               @Value("${app.two-factor.trusted-device-days:30}") long trustedDeviceDays,
                               @Value("${app.two-factor.grace-days:14}") long graceDays) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.two-factor.secret must be set (it defaults to JWT_SECRET).");
        }
        this.enabled = enabled;
        this.secret = secret;
        this.previousSecret = previousSecret == null || previousSecret.isBlank() || previousSecret.equals(secret)
                ? null : previousSecret;
        this.challengeLifetime = Duration.ofMinutes(challengeMinutes);
        this.trustedDeviceLifetime = Duration.ofDays(trustedDeviceDays);
        this.gracePeriod = Duration.ofDays(graceDays);
        if (!enabled) {
            log.warn("Two-factor sign-in is switched off (app.two-factor.enabled=false): no second step is asked.");
        }
    }

    /** TF-NFR-11: off means no second step and no requirement for admins and coaches. */
    public boolean enabled() {
        return enabled;
    }

    /** TF-FR-11: how long a sign-in challenge lasts. */
    public Duration challengeLifetime() {
        return challengeLifetime;
    }

    /** D-21-4: how long a remembered browser is trusted. */
    public Duration trustedDeviceLifetime() {
        return trustedDeviceLifetime;
    }

    /** D-21-2: how long admins and coaches may skip setup. */
    public Duration gracePeriod() {
        return gracePeriod;
    }

    /** HMAC of a short code (recovery or email) with the server secret. */
    public String hash(String code) {
        return TwoFactorCodes.hmac(code, secret);
    }

    /**
     * SEC-04-04: whether a stored hash belongs to this code, with the current
     * secret or, during a rotation, the previous one. Both are always compared,
     * in constant time.
     */
    public boolean matches(String storedHash, String code) {
        boolean current = TwoFactorCodes.sameHash(storedHash, hash(code));
        boolean previous = previousSecret != null && TwoFactorCodes.sameHash(storedHash, TwoFactorCodes.hmac(code, previousSecret));
        return current | previous;
    }

    @Override
    public String toString() {
        return "TwoFactorProperties[enabled=" + enabled + "]";
    }
}
