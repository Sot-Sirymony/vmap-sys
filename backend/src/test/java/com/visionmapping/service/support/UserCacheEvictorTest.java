package com.visionmapping.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class UserCacheEvictorTest {

    private final UserCacheEvictor evictor = new UserCacheEvictor(Clock.systemUTC());

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void generationIsStableUntilEvictedAndIndependentPerUser() {
        long userOne = evictor.generation(1L);
        long userTwo = evictor.generation(2L);
        assertEquals(userOne, evictor.generation(1L));

        evictor.evictAll(1L);

        assertTrue(evictor.generation(1L) > userOne, "eviction must advance the generation");
        assertEquals(userTwo, evictor.generation(2L), "other users' generations must not move");
    }

    @Test
    void evictionInsideTransactionTakesEffectOnlyAtCommit() {
        TransactionSynchronizationManager.initSynchronization();
        long before = evictor.generation(1L);

        evictor.evictAll(1L);
        assertEquals(before, evictor.generation(1L),
                "a bump before commit would let concurrent reads cache pre-commit data under the new generation");

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        assertTrue(evictor.generation(1L) > before, "commit must advance the generation");
    }

    /** BRD 13 EJ-FR-22: generations start from the injected clock, so a restart never reuses old keys. */
    /** The app's real clock ticks in microseconds; Clock.millis() on it divides by zero on Java 17. */
    @Test
    void worksWithTheMicrosecondTickClockTheAppUses() {
        UserCacheEvictor appLike = new UserCacheEvictor(new com.visionmapping.config.TimeConfig().clock());

        long before = appLike.generation(9L);
        appLike.evictAll(9L);

        assertTrue(appLike.generation(9L) > before);
    }

    @Test
    void generationsStartFromTheInjectedClock() {
        Instant boot = Instant.parse("2026-11-01T09:00:00Z");
        UserCacheEvictor fixed = new UserCacheEvictor(Clock.fixed(boot, ZoneOffset.UTC));

        assertEquals(boot.toEpochMilli(), fixed.generation(5L));
        fixed.evictAll(5L);
        assertEquals(boot.toEpochMilli() + 1, fixed.generation(5L), "a bump moves at least one step past the clock");
    }
}
