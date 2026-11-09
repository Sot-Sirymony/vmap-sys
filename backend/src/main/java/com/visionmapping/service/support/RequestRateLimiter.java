package com.visionmapping.service.support;

import com.visionmapping.exception.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * V10 (SEC-06-02): fixed-window limits for the endpoints that can be abused
 * by repetition: registration, cohort join codes, opening a share link and
 * Excel import. Sign-in, password reset and AI requests keep their own
 * limiters. Over a limit, the caller gets 429 with how long to wait.
 *
 * <p>Counts live in memory on this one instance, like the other limiters; a
 * restart clears them, which errs towards letting real users back in.
 */
@Slf4j
@Component
public class RequestRateLimiter {

    /** The protected actions, each with its own counts. */
    public enum Action {
        REGISTRATION("Too many new accounts from this network. Try again later."),
        JOIN_CODE("Too many join code attempts. Try again later."),
        SHARED_PLAN("Too many requests for shared plans from this network. Try again in a minute."),
        EXCEL_IMPORT("Too many imports in a short time. Try again later.");

        private final String message;

        Action(String message) {
            this.message = message;
        }
    }

    static final int MAX_TRACKED_KEYS = 20_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Map<Action, Limit> limits;

    public RequestRateLimiter(
            Clock clock,
            @Value("${app.security.rate-limit.registration-per-ip:10}") int registrationLimit,
            @Value("${app.security.rate-limit.registration-window-minutes:60}") long registrationWindow,
            @Value("${app.security.rate-limit.join-code-per-key:10}") int joinCodeLimit,
            @Value("${app.security.rate-limit.join-code-window-minutes:15}") long joinCodeWindow,
            @Value("${app.security.rate-limit.shared-plan-per-ip:30}") int sharedPlanLimit,
            @Value("${app.security.rate-limit.shared-plan-window-minutes:1}") long sharedPlanWindow,
            @Value("${app.security.rate-limit.excel-import-per-user:10}") int importLimit,
            @Value("${app.security.rate-limit.excel-import-window-minutes:60}") long importWindow) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.limits = Map.of(
                Action.REGISTRATION, new Limit(registrationLimit, Duration.ofMinutes(registrationWindow)),
                Action.JOIN_CODE, new Limit(joinCodeLimit, Duration.ofMinutes(joinCodeWindow)),
                Action.SHARED_PLAN, new Limit(sharedPlanLimit, Duration.ofMinutes(sharedPlanWindow)),
                Action.EXCEL_IMPORT, new Limit(importLimit, Duration.ofMinutes(importWindow)));
    }

    /**
     * Counts one attempt by {@code key} (an IP address or a user id).
     *
     * @throws TooManyRequestsException over the limit, with the seconds left in the window
     */
    public void check(Action action, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        Limit limit = limits.get(action);
        Instant now = clock.instant();
        String compositeKey = action.name() + ":" + key;
        if (!windows.containsKey(compositeKey) && windows.size() >= MAX_TRACKED_KEYS) {
            windows.values().removeIf(window -> !window.endsAt().isAfter(now));
            if (windows.size() >= MAX_TRACKED_KEYS) {
                // Fail closed: a full table means a flood, not normal use.
                log.warn("Rate-limit table is full; refusing {}", action);
                throw new TooManyRequestsException(action.message, limit.window().toSeconds());
            }
        }
        Window window = windows.compute(compositeKey, (ignored, existing) ->
                existing == null || !existing.endsAt().isAfter(now)
                        ? new Window(now.plus(limit.window()), 1)
                        : new Window(existing.endsAt(), existing.count() + 1));
        if (window.count() > limit.max()) {
            long wait = Math.max(1, Duration.between(now, window.endsAt()).toSeconds());
            throw new TooManyRequestsException(action.message, wait);
        }
    }

    private record Limit(int max, Duration window) {
    }

    private record Window(Instant endsAt, int count) {
    }
}
