package com.visionmapping.service.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.visionmapping.exception.TooManyRequestsException;
import com.visionmapping.service.support.RequestRateLimiter.Action;
import com.visionmapping.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** V10 (SEC-06-02): limits for registration, join codes, share links and imports. */
class RequestRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-09T09:00:00Z"));
    // 2 registrations an hour, 3 join codes in 15 minutes, 2 share views a minute, 1 import an hour.
    private final RequestRateLimiter limiter = new RequestRateLimiter(clock, 2, 60, 3, 15, 2, 1, 1, 60);

    @Test
    void refusesOverTheLimitWithTheSecondsLeftInTheWindow() {
        limiter.check(Action.REGISTRATION, "203.0.113.7");
        limiter.check(Action.REGISTRATION, "203.0.113.7");
        clock.advance(Duration.ofMinutes(10));
        assertThatThrownBy(() -> limiter.check(Action.REGISTRATION, "203.0.113.7"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("Too many new accounts")
                .satisfies(error -> assertThat(((TooManyRequestsException) error).getRetryAfterSeconds()).isEqualTo(50 * 60L));
    }

    @Test
    void countsEachKeyAndEachActionSeparately() {
        limiter.check(Action.EXCEL_IMPORT, "user:1");
        assertThatCode(() -> limiter.check(Action.EXCEL_IMPORT, "user:2")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.check(Action.REGISTRATION, "user:1")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.check(Action.EXCEL_IMPORT, "user:1")).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void opensAgainWhenTheWindowEnds() {
        limiter.check(Action.SHARED_PLAN, "198.51.100.1");
        limiter.check(Action.SHARED_PLAN, "198.51.100.1");
        assertThatThrownBy(() -> limiter.check(Action.SHARED_PLAN, "198.51.100.1")).isInstanceOf(TooManyRequestsException.class);
        clock.advance(Duration.ofMinutes(1));
        assertThatCode(() -> limiter.check(Action.SHARED_PLAN, "198.51.100.1")).doesNotThrowAnyException();
    }

    @Test
    void aJoinCodeGuesserIsStoppedAfterTheLimit() {
        for (int attempt = 0; attempt < 3; attempt++) {
            limiter.check(Action.JOIN_CODE, "ip:192.0.2.9");
        }
        assertThatThrownBy(() -> limiter.check(Action.JOIN_CODE, "ip:192.0.2.9"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("join code");
    }

    @Test
    void anUnknownCallerIsNotCounted() {
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatCode(() -> limiter.check(Action.EXCEL_IMPORT, " ")).doesNotThrowAnyException();
        }
    }
}
