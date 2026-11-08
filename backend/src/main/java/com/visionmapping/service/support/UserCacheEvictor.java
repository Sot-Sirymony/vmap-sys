package com.visionmapping.service.support;

import com.visionmapping.security.AppUserPrincipal;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Evicts a user's cached reads by advancing a per-user generation stamp that
 * the cache key generator embeds in every key (BR-22). Advancing the stamp
 * makes all of the user's existing entries unreachable at once — no pattern
 * scans against Redis, and the same mechanism works on the test profile's
 * in-memory cache. Orphaned entries fall out via the cache TTL.
 *
 * Generations seed from the boot-time clock and never move below it, so a
 * restart can never resurrect keys written by a previous process.
 */
@Component
public class UserCacheEvictor {

    private final ConcurrentHashMap<Long, AtomicLong> generations = new ConcurrentHashMap<>();
    private final Clock clock;

    public UserCacheEvictor(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** The authenticated user's id, or null on unauthenticated paths (register, login). */
    @Nullable
    public static Long currentUserIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal
                ? principal.getId()
                : null;
    }

    public long generation(Long userId) {
        return stamp(userId).get();
    }

    /**
     * Drops every cached entry of the user. Inside a transaction the bump is
     * deferred to commit: bumping earlier would let a concurrent read cache
     * pre-commit data under the new generation, which no later bump would clear.
     */
    public void evictAll(Long userId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    bump(userId);
                }
            });
        } else {
            bump(userId);
        }
    }

    private void bump(Long userId) {
        stamp(userId).updateAndGet(current -> Math.max(current + 1, nowMillis()));
    }

    private AtomicLong stamp(Long userId) {
        return generations.computeIfAbsent(userId, id -> new AtomicLong(nowMillis()));
    }

    /**
     * Not {@code clock.millis()}: the app's clock ticks in microseconds, and on
     * Java 17 {@code millis()} of a sub-millisecond tick clock divides by zero.
     */
    private long nowMillis() {
        return clock.instant().toEpochMilli();
    }
}
