package com.visionmapping.service.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.visionmapping.exception.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

/** ASVS F-04: counts in Redis, with the in-memory counts as the fallback. */
class SharedCountersTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-22T08:00:00Z"), ZoneOffset.UTC);

    // ---------- SharedCounters itself ----------

    @Test
    void whenOffEveryCallSaysUnavailable() {
        SharedCounters counters = SharedCounters.disabled();

        assertThat(counters.enabled()).isFalse();
        assertThatThrownBy(() -> counters.hit("k", Duration.ofMinutes(1))).isInstanceOf(SharedCounters.Unavailable.class);
        assertThatThrownBy(() -> counters.remaining("k")).isInstanceOf(SharedCounters.Unavailable.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aHitReturnsTheCountAndTheTimeLeft() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(List.of(3L, 42_000L));

        SharedCounters.Hit hit = new SharedCounters(redis).hit("email:a@example.com", Duration.ofMinutes(15));

        assertThat(hit.count()).isEqualTo(3);
        assertThat(hit.remaining()).isEqualTo(Duration.ofSeconds(42));
        verify(redis).execute(any(RedisScript.class), eq(List.of(SharedCounters.redisKey("email:a@example.com"))), eq("900000"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aRedisFailureBecomesUnavailable() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThatThrownBy(() -> new SharedCounters(redis).hit("k", Duration.ofMinutes(1)))
                .isInstanceOf(SharedCounters.Unavailable.class);
    }

    @Test
    void keysDoNotHoldTheEmailOrAddressInClear() {
        String key = SharedCounters.redisKey("login-lock:email:someone@example.com");

        assertThat(key).startsWith("vms:limit:").doesNotContain("someone").hasSize("vms:limit:".length() + 64);
    }

    @Test
    void aMissingKeyHasNoTimeLeft() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.getExpire(anyString(), eq(TimeUnit.MILLISECONDS))).thenReturn(-2L);

        assertThat(new SharedCounters(redis).remaining("k")).isEmpty();
    }

    // ---------- the limiters with Redis on ----------

    @Test
    void aLockInRedisRefusesSignInEvenOnAFreshInstance() {
        SharedCounters counters = mock(SharedCounters.class);
        when(counters.enabled()).thenReturn(true);
        when(counters.remaining(anyString())).thenReturn(Optional.empty());
        when(counters.remaining("login-lock:email:locked@example.com")).thenReturn(Optional.of(Duration.ofMinutes(9)));
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock, 5, 50, 15, 15, counters);

        assertThatThrownBy(() -> limiter.checkAllowed("Locked@Example.com ", "10.0.0.1"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("9 minutes");
    }

    @Test
    void theFifthFailureInRedisSetsTheLock() {
        SharedCounters counters = mock(SharedCounters.class);
        when(counters.enabled()).thenReturn(true);
        when(counters.hit(eq("login-fail:email:a@example.com"), any())).thenReturn(new SharedCounters.Hit(5, Duration.ofMinutes(3)));
        when(counters.hit(eq("login-fail:ip:10.0.0.1"), any())).thenReturn(new SharedCounters.Hit(5, Duration.ofMinutes(3)));
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock, 5, 50, 15, 15, counters);

        limiter.recordFailure("a@example.com", "10.0.0.1");

        verify(counters).set("login-lock:email:a@example.com", Duration.ofMinutes(15));
        verify(counters).delete("login-fail:email:a@example.com");
        verify(counters, never()).set(eq("login-lock:ip:10.0.0.1"), any());
    }

    @Test
    void aSuccessfulSignInClearsTheFailuresAndLockInRedis() {
        SharedCounters counters = mock(SharedCounters.class);
        when(counters.enabled()).thenReturn(true);
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock, 5, 50, 15, 15, counters);

        limiter.recordSuccess("A@example.com");

        verify(counters).delete("login-fail:email:a@example.com", "login-lock:email:a@example.com");
    }

    @Test
    void whenRedisIsDownSignInLocksStillWorkInMemory() {
        SharedCounters counters = mock(SharedCounters.class);
        when(counters.enabled()).thenReturn(true);
        when(counters.hit(anyString(), any())).thenThrow(new SharedCounters.Unavailable("down", null));
        when(counters.remaining(anyString())).thenThrow(new SharedCounters.Unavailable("down", null));
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock, 5, 50, 15, 15, counters);

        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("b@example.com", null);
        }

        assertThatThrownBy(() -> limiter.checkAllowed("b@example.com", null)).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void aRateLimitCountedInRedisRefusesOverTheLimit() {
        SharedCounters counters = mock(SharedCounters.class);
        when(counters.enabled()).thenReturn(true);
        when(counters.hit(eq("rate:REGISTRATION:10.0.0.9"), any())).thenReturn(new SharedCounters.Hit(3, Duration.ofSeconds(90)));
        RequestRateLimiter limiter = limiter(counters);

        assertThatThrownBy(() -> limiter.check(RequestRateLimiter.Action.REGISTRATION, "10.0.0.9"))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(error -> assertThat(((TooManyRequestsException) error).getRetryAfterSeconds()).isEqualTo(90L));
    }

    @Test
    void whenRedisIsDownRateLimitsStillWorkInMemory() {
        SharedCounters counters = mock(SharedCounters.class);
        when(counters.enabled()).thenReturn(true);
        when(counters.hit(anyString(), any())).thenThrow(new SharedCounters.Unavailable("down", null));
        RequestRateLimiter limiter = limiter(counters);

        limiter.check(RequestRateLimiter.Action.REGISTRATION, "10.0.0.8");
        limiter.check(RequestRateLimiter.Action.REGISTRATION, "10.0.0.8");

        assertThatThrownBy(() -> limiter.check(RequestRateLimiter.Action.REGISTRATION, "10.0.0.8"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void passwordResetRequestsAreCountedInRedis() {
        SharedCounters counters = mock(SharedCounters.class);
        when(counters.enabled()).thenReturn(true);
        when(counters.hit(eq("reset:email:c@example.com"), any())).thenReturn(new SharedCounters.Hit(4, Duration.ofMinutes(10)));
        PasswordResetRateLimiter limiter = new PasswordResetRateLimiter(clock, counters);
        ReflectionTestUtils.setField(limiter, "perEmailLimit", 3);
        ReflectionTestUtils.setField(limiter, "perEmailWindowMinutes", 15L);

        assertThatThrownBy(() -> limiter.check("c@example.com", null)).isInstanceOf(TooManyRequestsException.class);
    }

    private RequestRateLimiter limiter(SharedCounters counters) {
        return new RequestRateLimiter(clock, counters, 2, 60, 3, 15, 2, 1, 1, 60, 2, 10, 2, 1440, 2, 1, 2, 10, 2, 15, 3, 15, 2, 60);
    }
}
