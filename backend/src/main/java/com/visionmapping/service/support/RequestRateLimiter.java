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
        EXCEL_IMPORT("Too many imports in a short time. Try again later."),
        // V18 MR-NFR-05: each report download builds a whole workbook or deck.
        REPORT_EXPORT("Too many report downloads in a short time. Try again in a few minutes."),
        // V19 RM-NFR-07: share links made per account per day, and views of one shared report per minute.
        REPORT_SHARE("You have made 20 share links today. Try again tomorrow."),
        SHARED_REPORT("Too many requests for this shared report. Try again in a minute."),
        // V20 SV-NFR-06: code checks per person; the hourly and daily send limits are counted in the database.
        STEP_UP_VERIFY("Too many code attempts. Wait a few minutes and ask for a new code."),
        // V14 GA-SEC-09: each Google sign-in step, per network.
        GOOGLE_SIGN_IN("Too many Google sign-in attempts from this network. Try again in a few minutes.");

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
            @Value("${app.security.rate-limit.excel-import-window-minutes:60}") long importWindow,
            @Value("${app.security.rate-limit.report-export-per-user:30}") int reportLimit,
            @Value("${app.security.rate-limit.report-export-window-minutes:10}") long reportWindow,
            @Value("${app.security.rate-limit.report-share-per-user:20}") int shareLimit,
            @Value("${app.security.rate-limit.report-share-window-minutes:1440}") long shareWindow,
            @Value("${app.security.rate-limit.shared-report-per-token:60}") int sharedReportLimit,
            @Value("${app.security.rate-limit.shared-report-window-minutes:1}") long sharedReportWindow,
            @Value("${app.security.rate-limit.step-up-verify-per-user:20}") int stepUpVerifyLimit,
            @Value("${app.security.rate-limit.step-up-verify-window-minutes:10}") long stepUpVerifyWindow,
            @Value("${app.security.rate-limit.google-sign-in-per-ip:60}") int googleLimit,
            @Value("${app.security.rate-limit.google-sign-in-window-minutes:15}") long googleWindow) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.limits = Map.of(
                Action.REGISTRATION, new Limit(registrationLimit, Duration.ofMinutes(registrationWindow)),
                Action.JOIN_CODE, new Limit(joinCodeLimit, Duration.ofMinutes(joinCodeWindow)),
                Action.SHARED_PLAN, new Limit(sharedPlanLimit, Duration.ofMinutes(sharedPlanWindow)),
                Action.EXCEL_IMPORT, new Limit(importLimit, Duration.ofMinutes(importWindow)),
                Action.REPORT_EXPORT, new Limit(reportLimit, Duration.ofMinutes(reportWindow)),
                Action.REPORT_SHARE, new Limit(shareLimit, Duration.ofMinutes(shareWindow)),
                Action.SHARED_REPORT, new Limit(sharedReportLimit, Duration.ofMinutes(sharedReportWindow)),
                Action.STEP_UP_VERIFY, new Limit(stepUpVerifyLimit, Duration.ofMinutes(stepUpVerifyWindow)),
                Action.GOOGLE_SIGN_IN, new Limit(googleLimit, Duration.ofMinutes(googleWindow)));
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
