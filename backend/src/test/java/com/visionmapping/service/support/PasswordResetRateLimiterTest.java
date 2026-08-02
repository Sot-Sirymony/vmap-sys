package com.visionmapping.service.support;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.visionmapping.exception.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PasswordResetRateLimiterTest {

    private static final String IP = "203.0.113.7";

    private PasswordResetRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new PasswordResetRateLimiter();
        ReflectionTestUtils.setField(limiter, "perEmailLimit", 3);
        ReflectionTestUtils.setField(limiter, "perEmailWindowMinutes", 15L);
        ReflectionTestUtils.setField(limiter, "perIpLimit", 20);
        ReflectionTestUtils.setField(limiter, "perIpWindowMinutes", 60L);
    }

    @Test
    @DisplayName("allows requests up to the per-address limit")
    void allowsUpToLimit() {
        for (int i = 0; i < 3; i += 1) {
            int attempt = i;
            assertThatCode(() -> limiter.check("pat@example.com", IP))
                    .as("attempt %d", attempt + 1)
                    .doesNotThrowAnyException();
        }
    }

    /** The limit a victim depends on: without it the endpoint floods their inbox. */
    @Test
    @DisplayName("refuses a fourth request for the same address")
    void blocksPastPerEmailLimit() {
        for (int i = 0; i < 3; i += 1) {
            limiter.check("pat@example.com", IP);
        }

        assertThatThrownBy(() -> limiter.check("pat@example.com", IP))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("this address");
    }

    @Test
    @DisplayName("counts each address separately")
    void addressesAreIndependent() {
        for (int i = 0; i < 3; i += 1) {
            limiter.check("pat@example.com", IP);
        }

        assertThatCode(() -> limiter.check("sam@example.com", IP)).doesNotThrowAnyException();
    }

    /**
     * One caller working through a list of addresses stays under every
     * per-address limit, so the IP limit is what stops enumeration.
     */
    @Test
    @DisplayName("refuses a caller working through many addresses")
    void blocksPastPerIpLimit() {
        for (int i = 0; i < 20; i += 1) {
            limiter.check("user" + i + "@example.com", IP);
        }

        assertThatThrownBy(() -> limiter.check("user-last@example.com", IP))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("this network");
    }

    @Test
    @DisplayName("counts each caller separately")
    void callersAreIndependent() {
        for (int i = 0; i < 20; i += 1) {
            limiter.check("user" + i + "@example.com", IP);
        }

        assertThatCode(() -> limiter.check("someone@example.com", "198.51.100.4"))
                .doesNotThrowAnyException();
    }

    /**
     * Behind a proxy the client address can be absent. The per-address limit
     * still has to apply — losing both would leave the endpoint unprotected.
     */
    @Test
    @DisplayName("still limits by address when the caller's IP is unknown")
    void limitsWithoutClientIp() {
        for (int i = 0; i < 3; i += 1) {
            limiter.check("pat@example.com", null);
        }

        assertThatThrownBy(() -> limiter.check("pat@example.com", null))
                .isInstanceOf(TooManyRequestsException.class);
    }

    /**
     * A window that has passed must not keep blocking. Set to zero minutes so
     * each entry is already expired when it is next looked at, which exercises
     * the reset path without the test having to wait.
     */
    @Test
    @DisplayName("lets a caller through again once the window has passed")
    void windowResets() {
        ReflectionTestUtils.setField(limiter, "perEmailWindowMinutes", 0L);
        for (int i = 0; i < 3; i += 1) {
            limiter.check("pat@example.com", IP);
        }

        assertThatCode(() -> limiter.check("pat@example.com", IP)).doesNotThrowAnyException();
    }
}
