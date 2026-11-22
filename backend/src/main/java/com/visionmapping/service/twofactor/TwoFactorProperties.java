package com.visionmapping.service.twofactor;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
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
    private final Duration challengeLifetime;
    private final Duration trustedDeviceLifetime;
    private final Duration gracePeriod;

    public TwoFactorProperties(@Value("${app.two-factor.enabled:true}") boolean enabled,
                               @Value("${app.two-factor.secret}") String secret,
                               @Value("${app.two-factor.challenge-minutes:5}") long challengeMinutes,
                               @Value("${app.two-factor.trusted-device-days:30}") long trustedDeviceDays,
                               @Value("${app.two-factor.grace-days:14}") long graceDays) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.two-factor.secret must be set (it defaults to JWT_SECRET).");
        }
        this.enabled = enabled;
        this.secret = secret;
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

    @Override
    public String toString() {
        return "TwoFactorProperties[enabled=" + enabled + "]";
    }
}
