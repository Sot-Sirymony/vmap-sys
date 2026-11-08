package com.visionmapping.service.support;

import com.visionmapping.exception.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * V10 (SEC-07-01, SEC-06-02): limits failed sign-in attempts so passwords
 * cannot be guessed at full speed.
 *
 * <p>Two limits. Per email: after {@code maxFailuresPerAccount} wrong passwords
 * inside the window, that email is refused for {@code lockMinutes}. Per IP:
 * after {@code maxFailuresPerIp} failures inside the window, that caller is
 * refused for the rest of the window — this slows one caller working through
 * many emails (credential stuffing).
 *
 * <p><b>Unknown emails are counted exactly like real ones.</b> The key is the
 * email as typed, checked before any account lookup, so the lock cannot be used
 * to learn which emails are registered (SEC-07-02).
 *
 * <p>Only failures count. A successful sign-in, or a completed password reset,
 * clears the email's counter: reset proves the person owns the mailbox, and it
 * keeps working during a lock, so a real user locked out by someone else
 * guessing at their email always has a way back in.
 *
 * <p>In memory, so limits are per instance — honest for the single-instance
 * Render deployment (see {@link PasswordResetRateLimiter}). The table is
 * bounded for the 512MB instance. When it is full, new keys are not tracked
 * (existing locks keep working). That trades perfect tracking during a flood of
 * distinct keys for keeping sign-in available to everyone else; failing closed
 * here would let anyone lock every user out by filling the table.
 */
@Slf4j
@Component
public class LoginAttemptLimiter {

    static final int MAX_TRACKED_KEYS = 20_000;

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    @Value("${app.security.login.max-failures-per-account:5}")
    private int maxFailuresPerAccount;

    @Value("${app.security.login.max-failures-per-ip:50}")
    private int maxFailuresPerIp;

    @Value("${app.security.login.window-minutes:15}")
    private long windowMinutes;

    @Value("${app.security.login.lock-minutes:15}")
    private long lockMinutes;

    @Autowired
    public LoginAttemptLimiter() {
        this(Clock.systemUTC());
    }

    LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    /** For unit tests, which build the limiter without Spring. */
    LoginAttemptLimiter(Clock clock, int maxFailuresPerAccount, int maxFailuresPerIp, long windowMinutes, long lockMinutes) {
        this(clock);
        this.maxFailuresPerAccount = maxFailuresPerAccount;
        this.maxFailuresPerIp = maxFailuresPerIp;
        this.windowMinutes = windowMinutes;
        this.lockMinutes = lockMinutes;
    }

    /**
     * Call before checking the password.
     *
     * @throws TooManyRequestsException when the email or the caller is locked
     */
    public void checkAllowed(@Nullable String email, @Nullable String clientIp) {
        Instant now = clock.instant();
        refuseIfLocked(accountKey(email), now,
                "Too many sign-in attempts for this email. Try again in %d minutes, or reset your password.");
        if (hasText(clientIp)) {
            refuseIfLocked(ipKey(clientIp), now,
                    "Too many sign-in attempts from this network. Try again in %d minutes.");
        }
    }

    public void recordFailure(@Nullable String email, @Nullable String clientIp) {
        Instant now = clock.instant();
        fail(accountKey(email), maxFailuresPerAccount, Duration.ofMinutes(lockMinutes), now);
        if (hasText(clientIp)) {
            fail(ipKey(clientIp), maxFailuresPerIp, Duration.ofMinutes(windowMinutes), now);
        }
    }

    /** Clears the email's failures. The IP counter is left alone on purpose. */
    public void recordSuccess(String email) {
        entries.remove(accountKey(email));
    }

    private void refuseIfLocked(String key, Instant now, String messageTemplate) {
        Entry entry = entries.get(key);
        if (entry == null || entry.lockedUntil == null || !entry.lockedUntil.isAfter(now)) {
            return;
        }
        long seconds = Math.max(1, Duration.between(now, entry.lockedUntil).toSeconds());
        long minutes = Math.max(1, (seconds + 59) / 60);
        throw new TooManyRequestsException(String.format(messageTemplate, minutes), seconds);
    }

    private void fail(String key, int maxFailures, Duration lock, Instant now) {
        if (!entries.containsKey(key) && entries.size() >= MAX_TRACKED_KEYS && !purgeExpired(now)) {
            log.warn("Sign-in attempt table is full; a new key is not being tracked");
            return;
        }
        entries.compute(key, (ignored, existing) -> {
            Entry entry = existing == null || existing.isExpired(now)
                    ? new Entry(now.plus(Duration.ofMinutes(windowMinutes)))
                    : existing;
            entry.failures++;
            if (entry.failures >= maxFailures) {
                entry.lockedUntil = now.plus(lock);
                entry.failures = 0;
                entry.windowEnds = entry.lockedUntil;
            }
            return entry;
        });
    }

    /** @return true if anything was freed */
    private boolean purgeExpired(Instant now) {
        int before = entries.size();
        entries.entrySet().removeIf(item -> item.getValue().isExpired(now));
        return entries.size() < before;
    }

    private static String accountKey(@Nullable String email) {
        return "email:" + (email == null ? "" : email.trim().toLowerCase());
    }

    private static String ipKey(String clientIp) {
        return "ip:" + clientIp.trim();
    }

    private static boolean hasText(@Nullable String value) {
        return value != null && !value.isBlank();
    }

    int trackedKeys() {
        return entries.size();
    }

    private static final class Entry {
        private Instant windowEnds;
        private int failures;
        private Instant lockedUntil;

        private Entry(Instant windowEnds) {
            this.windowEnds = windowEnds;
        }

        private boolean isExpired(Instant now) {
            boolean lockOver = lockedUntil == null || !lockedUntil.isAfter(now);
            return lockOver && !windowEnds.isAfter(now);
        }
    }
}
