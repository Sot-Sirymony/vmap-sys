package com.visionmapping.service.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.visionmapping.exception.TooManyRequestsException;
import com.visionmapping.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** V10 (SEC-07-01): sign-in attempt limits. */
class LoginAttemptLimiterTest {

    private static final String EMAIL = "person@example.com";
    private static final String IP = "203.0.113.9";

    private MutableClock clock;
    private LoginAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-06T09:00:00Z"));
        limiter = new LoginAttemptLimiter(clock, 5, 50, 15, 15);
    }

    @Test
    void locksTheEmailAfterFiveFailuresAndTellsHowLongToWait() {
        failTimes(EMAIL, IP, 4);
        assertThatCode(() -> limiter.checkAllowed(EMAIL, IP)).doesNotThrowAnyException();

        limiter.recordFailure(EMAIL, IP);

        assertThatThrownBy(() -> limiter.checkAllowed(EMAIL, IP))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("15 minutes")
                .satisfies(error -> assertThat(((TooManyRequestsException) error).getRetryAfterSeconds())
                        .isEqualTo(15 * 60L));
    }

    @Test
    void theLockEndsAfterTheLockTime() {
        failTimes(EMAIL, IP, 5);

        clock.advance(Duration.ofMinutes(15).plusSeconds(1));

        assertThatCode(() -> limiter.checkAllowed(EMAIL, IP)).doesNotThrowAnyException();
    }

    @Test
    void theLockAppliesFromAnyNetworkAndIgnoresEmailCase() {
        failTimes(EMAIL, IP, 5);

        assertThatThrownBy(() -> limiter.checkAllowed("  PERSON@example.com ", "198.51.100.1"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void failuresOutsideTheWindowDoNotAddUp() {
        failTimes(EMAIL, IP, 4);
        clock.advance(Duration.ofMinutes(16));
        failTimes(EMAIL, IP, 4);

        assertThatCode(() -> limiter.checkAllowed(EMAIL, IP)).doesNotThrowAnyException();
    }

    @Test
    void successClearsTheEmailCounter() {
        failTimes(EMAIL, IP, 4);
        limiter.recordSuccess(EMAIL);
        failTimes(EMAIL, IP, 4);

        assertThatCode(() -> limiter.checkAllowed(EMAIL, IP)).doesNotThrowAnyException();
    }

    @Test
    void recordSuccessLiftsAnActiveLock() {
        failTimes(EMAIL, IP, 5);

        limiter.recordSuccess(EMAIL);

        assertThatCode(() -> limiter.checkAllowed(EMAIL, IP)).doesNotThrowAnyException();
    }

    @Test
    void oneNetworkTryingManyEmailsIsStopped() {
        for (int i = 0; i < 50; i++) {
            limiter.recordFailure("user" + i + "@example.com", IP);
        }

        assertThatThrownBy(() -> limiter.checkAllowed("fresh@example.com", IP))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("network");
        // Another network with the same fresh email is unaffected.
        assertThatCode(() -> limiter.checkAllowed("fresh@example.com", "198.51.100.1")).doesNotThrowAnyException();
    }

    @Test
    void worksWithoutAnIp() {
        failTimes(EMAIL, null, 5);

        assertThatThrownBy(() -> limiter.checkAllowed(EMAIL, null)).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void expiredEntriesAreFreedWhenTheTableIsFull() {
        for (int i = 0; i < LoginAttemptLimiter.MAX_TRACKED_KEYS; i++) {
            limiter.recordFailure("user" + i + "@example.com", null);
        }
        assertThat(limiter.trackedKeys()).isEqualTo(LoginAttemptLimiter.MAX_TRACKED_KEYS);

        clock.advance(Duration.ofMinutes(16));
        failTimes(EMAIL, null, 5);

        assertThat(limiter.trackedKeys()).isEqualTo(1);
        assertThatThrownBy(() -> limiter.checkAllowed(EMAIL, null)).isInstanceOf(TooManyRequestsException.class);
    }

    private void failTimes(String email, String ip, int times) {
        for (int i = 0; i < times; i++) {
            limiter.recordFailure(email, ip);
        }
    }
}
