package com.visionmapping.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.support.MutableClock;
import io.jsonwebtoken.ExpiredJwtException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

/**
 * Covers the JWT secret fail-fast guard: the built-in development default must be rejected
 * outside the local/test profiles, and a missing/blank secret must always be rejected.
 */
@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private static final String DEFAULT_SECRET = "vision-mapping-development-secret-key-change-before-production";
    private static final String REAL_SECRET = "a-sufficiently-long-and-unique-production-jwt-secret-value";

    @Mock private Environment environment;

    @Test
    void defaultSecretIsAllowedOnLocalProfile() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"local"});
        new JwtService(DEFAULT_SECRET, 1440, environment, Clock.systemUTC());
    }

    @Test
    void defaultSecretIsAllowedOnTestProfile() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"test"});
        new JwtService(DEFAULT_SECRET, 1440, environment, Clock.systemUTC());
    }

    @Test
    void defaultSecretIsRejectedWithNoActiveProfile() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {});
        assertThatThrownBy(() -> new JwtService(DEFAULT_SECRET, 1440, environment, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("built-in development default");
    }

    @Test
    void defaultSecretIsRejectedOnAnyOtherProfile() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"prod"});
        assertThatThrownBy(() -> new JwtService(DEFAULT_SECRET, 1440, environment, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("built-in development default");
    }

    @Test
    void realSecretIsAcceptedOnNonDevProfile() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"prod"});
        new JwtService(REAL_SECRET, 1440, environment, Clock.systemUTC());
    }

    @Test
    void blankSecretIsAlwaysRejected() {
        assertThatThrownBy(() -> new JwtService("   ", 1440, environment, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be set");
    }

    @Test
    void nullSecretIsAlwaysRejected() {
        assertThatThrownBy(() -> new JwtService(null, 1440, environment, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be set");
    }

    /** BRD 13 EJ-FR-22: issue and expiry times come from the injected clock. */
    @Test
    void tokenTimesFollowTheInjectedClock() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"test"});
        MutableClock clock = new MutableClock(Instant.parse("2026-11-01T09:00:00Z"));
        JwtService service = new JwtService(REAL_SECRET, 1, environment, clock);
        AppUser user = AppUser.builder().id(7L).fullName("Pat").email("pat@example.com").passwordHash("hash")
                .role(UserRole.USER).status(UserStatus.ACTIVE).build();
        String token = service.generateToken(new AppUserPrincipal(user), 11L);

        assertThat(service.parse(token).sessionId()).isEqualTo(11L);

        clock.advance(Duration.ofMinutes(2));
        assertThatThrownBy(() -> service.parse(token)).isInstanceOf(ExpiredJwtException.class);
    }
}
