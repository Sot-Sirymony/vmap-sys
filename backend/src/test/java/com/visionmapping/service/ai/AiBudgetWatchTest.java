package com.visionmapping.service.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.visionmapping.entity.AiSettings;
import com.visionmapping.repository.AiUsageLogRepository;
import com.visionmapping.repository.SecurityEventRepository;
import com.visionmapping.security.audit.SecurityEventService;
import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityOutcome;
import com.visionmapping.service.AiSettingsService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** V10 (SEC-09-04): AI spending alerts at 80% and 100% of the budget, and on a one-hour spike. */
class AiBudgetWatchTest {

    private static final Instant NOW = Instant.parse("2026-11-22T09:07:00Z");
    private static final Instant HOUR_START = Instant.parse("2026-11-22T08:00:00Z");
    private static final Instant HOUR_END = Instant.parse("2026-11-22T09:00:00Z");

    private final AiUsageLogRepository usage = mock(AiUsageLogRepository.class);
    private final AiSettingsService aiSettings = mock(AiSettingsService.class);
    private final AiAccessService aiAccess = mock(AiAccessService.class);
    private final SecurityEventRepository events = mock(SecurityEventRepository.class);
    private final SecurityEventService securityEvents = mock(SecurityEventService.class);
    private AiBudgetWatch watch;

    @BeforeEach
    void setUp() {
        AiSettings settings = new AiSettings();
        settings.setMonthlyBudgetUsd(new BigDecimal("20.00"));
        when(aiSettings.current()).thenReturn(settings);
        when(usage.totalCostBetween(any(), any())).thenReturn(BigDecimal.ZERO);
        watch = new AiBudgetWatch(usage, aiSettings, aiAccess, events, securityEvents,
                Clock.fixed(NOW, ZoneOffset.UTC), 80, new BigDecimal("1.00"), new BigDecimal("5"));
    }

    @Test
    void underEightyPercentRaisesNothing() {
        when(aiAccess.spendThisMonth()).thenReturn(new BigDecimal("15.99"));

        watch.check();

        verify(securityEvents, never()).recordAnonymous(any(), any(), any(), any(), anyString());
    }

    @Test
    void eightyPercentRaisesAWarningOnce() {
        when(aiAccess.spendThisMonth()).thenReturn(new BigDecimal("16.40"));

        watch.check();

        verify(securityEvents).recordAnonymous(eq(SecurityEventType.AI_BUDGET_WARNING), eq(SecurityOutcome.SUCCESS),
                isNull(), isNull(), eq("monthly-80 2026-11: spend this month $16.40 is 82% of the $20.00 budget"));
    }

    @Test
    void aWarningAlreadyRaisedThisMonthIsNotRaisedAgain() {
        when(aiAccess.spendThisMonth()).thenReturn(new BigDecimal("17.00"));
        when(events.existsByEventTypeAndDetailStartingWith(SecurityEventType.AI_BUDGET_WARNING, "monthly-80 2026-11"))
                .thenReturn(true);

        watch.check();

        verify(securityEvents, never()).recordAnonymous(any(), any(), any(), any(), anyString());
    }

    @Test
    void reachingTheBudgetRaisesItsOwnWarning() {
        when(aiAccess.spendThisMonth()).thenReturn(new BigDecimal("20.03"));

        watch.check();

        verify(securityEvents).recordAnonymous(eq(SecurityEventType.AI_BUDGET_WARNING), any(), isNull(), isNull(),
                startsWith("monthly-100 2026-11: spend this month $20.03 reached the $20.00 budget"));
    }

    @Test
    void noBudgetMeansNoMonthlyWarning() {
        AiSettings settings = new AiSettings();
        when(aiSettings.current()).thenReturn(settings);
        when(aiAccess.spendThisMonth()).thenReturn(new BigDecimal("50.00"));

        watch.check();

        verify(securityEvents, never()).recordAnonymous(any(), any(), any(), any(), anyString());
    }

    @Test
    void anHourFarAboveTheWeeksAverageIsASpike() {
        when(aiAccess.spendThisMonth()).thenReturn(BigDecimal.ONE);
        when(usage.totalCostBetween(HOUR_START, HOUR_END)).thenReturn(new BigDecimal("2.50"));
        when(usage.totalCostBetween(HOUR_START.minusSeconds(7 * 24 * 3600), HOUR_START)).thenReturn(new BigDecimal("16.80"));

        watch.check();

        verify(securityEvents).recordAnonymous(eq(SecurityEventType.AI_BUDGET_WARNING), any(), isNull(), isNull(),
                eq("spike 2026-11-22T08:00:00Z: the hour from 2026-11-22T08:00:00Z cost $2.50, "
                        + "against an average hour of $0.10 over the week before"));
    }

    @Test
    void aSmallHourIsNeverASpikeEvenWithNoHistory() {
        when(aiAccess.spendThisMonth()).thenReturn(BigDecimal.ONE);
        when(usage.totalCostBetween(HOUR_START, HOUR_END)).thenReturn(new BigDecimal("0.90"));

        watch.check();

        verify(securityEvents, never()).recordAnonymous(any(), any(), any(), any(), anyString());
    }

    @Test
    void aBusyHourInABusyWeekIsNotASpike() {
        when(aiAccess.spendThisMonth()).thenReturn(BigDecimal.ONE);
        when(usage.totalCostBetween(HOUR_START, HOUR_END)).thenReturn(new BigDecimal("2.00"));
        when(usage.totalCostBetween(HOUR_START.minusSeconds(7 * 24 * 3600), HOUR_START)).thenReturn(new BigDecimal("168.00"));

        watch.check();

        verify(securityEvents, never()).recordAnonymous(any(), any(), any(), any(), anyString());
    }

    @Test
    void aFailingCheckDoesNotThrow() {
        when(aiSettings.current()).thenThrow(new IllegalStateException("database down"));

        watch.check();

        verify(securityEvents, never()).recordAnonymous(any(), any(), any(), any(), anyString());
    }
}
