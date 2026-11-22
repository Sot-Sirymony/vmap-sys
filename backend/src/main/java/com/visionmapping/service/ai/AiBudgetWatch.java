package com.visionmapping.service.ai;

import com.visionmapping.entity.AiSettings;
import com.visionmapping.repository.AiUsageLogRepository;
import com.visionmapping.repository.SecurityEventRepository;
import com.visionmapping.security.audit.SecurityEventService;
import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityOutcome;
import com.visionmapping.service.AiSettingsService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * V10 (SEC-09-04): alerts when AI spending runs high, before the monthly cap
 * stops AI for everyone.
 *
 * <p>Every {@code app.ai.budget-alerts.cron} (15 minutes) it checks:
 * <ul>
 *   <li><b>Monthly budget:</b> spend this month at {@code warn-percent} (80) and at
 *       100% of the budget, once each per month;</li>
 *   <li><b>Spike:</b> the last full hour cost at least {@code spike-min-usd} ($1)
 *       and at least {@code spike-factor} (5) times the average hour of the
 *       seven days before it, once per hour. A sudden jump usually means a bug,
 *       a stuck loop or someone abusing the AI.</li>
 * </ul>
 *
 * <p>Each warning is an {@code AI_BUDGET_WARNING} security event, so it shows in
 * the security log and {@code SecurityAlertService} sends it to the log and the
 * alert email. The event's detail starts with a fixed key (for example
 * {@code monthly-80 2026-11}), which is how a warning is raised only once even
 * across restarts. The detail holds amounts only, never names or text.
 */
@Slf4j
@Component
public class AiBudgetWatch {

    private static final Duration BASELINE = Duration.ofDays(7);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final AiUsageLogRepository usage;
    private final AiSettingsService aiSettings;
    private final AiAccessService aiAccess;
    private final SecurityEventRepository events;
    private final SecurityEventService securityEvents;
    private final Clock clock;
    private final int warnPercent;
    private final BigDecimal spikeMinUsd;
    private final BigDecimal spikeFactor;

    public AiBudgetWatch(AiUsageLogRepository usage, AiSettingsService aiSettings, AiAccessService aiAccess,
                         SecurityEventRepository events, SecurityEventService securityEvents, Clock clock,
                         @Value("${app.ai.budget-alerts.warn-percent:80}") int warnPercent,
                         @Value("${app.ai.budget-alerts.spike-min-usd:1.00}") BigDecimal spikeMinUsd,
                         @Value("${app.ai.budget-alerts.spike-factor:5}") BigDecimal spikeFactor) {
        if (warnPercent < 1 || warnPercent >= 100) {
            throw new IllegalStateException("AI_BUDGET_WARN_PERCENT must be between 1 and 99.");
        }
        this.usage = usage;
        this.aiSettings = aiSettings;
        this.aiAccess = aiAccess;
        this.events = events;
        this.securityEvents = securityEvents;
        this.clock = clock;
        this.warnPercent = warnPercent;
        this.spikeMinUsd = spikeMinUsd;
        this.spikeFactor = spikeFactor;
    }

    @Scheduled(cron = "${app.ai.budget-alerts.cron:0 */15 * * * *}")
    public void check() {
        try {
            checkMonthlyBudget();
            checkSpike();
        } catch (RuntimeException exception) {
            // A failed check must never affect anything else; the next run tries again.
            log.error("Could not check AI spending", exception);
        }
    }

    void checkMonthlyBudget() {
        AiSettings settings = aiSettings.current();
        BigDecimal budget = settings.getMonthlyBudgetUsd();
        if (budget == null || budget.signum() <= 0) {
            return;
        }
        BigDecimal spend = aiAccess.spendThisMonth();
        String month = YearMonth.now(clock).toString();
        BigDecimal percent = spend.multiply(HUNDRED).divide(budget, 0, RoundingMode.DOWN);
        if (percent.compareTo(HUNDRED) >= 0) {
            raiseOnce("monthly-100 " + month, "spend this month " + usd(spend) + " reached the "
                    + usd(budget) + " budget; AI is paused until next month or a higher budget");
        } else if (percent.intValue() >= warnPercent) {
            raiseOnce("monthly-" + warnPercent + " " + month, "spend this month " + usd(spend) + " is "
                    + percent.toPlainString() + "% of the " + usd(budget) + " budget");
        }
    }

    void checkSpike() {
        Instant hourEnd = clock.instant().truncatedTo(ChronoUnit.HOURS);
        Instant hourStart = hourEnd.minus(Duration.ofHours(1));
        BigDecimal lastHour = usage.totalCostBetween(hourStart, hourEnd);
        if (lastHour.compareTo(spikeMinUsd) < 0) {
            return;
        }
        BigDecimal before = usage.totalCostBetween(hourStart.minus(BASELINE), hourStart);
        BigDecimal averageHour = before.divide(BigDecimal.valueOf(BASELINE.toHours()), 6, RoundingMode.HALF_UP);
        if (lastHour.compareTo(averageHour.multiply(spikeFactor)) >= 0) {
            raiseOnce("spike " + hourStart, "the hour from " + hourStart + " cost " + usd(lastHour)
                    + ", against an average hour of " + usd(averageHour) + " over the week before");
        }
    }

    private void raiseOnce(String key, String message) {
        if (events.existsByEventTypeAndDetailStartingWith(SecurityEventType.AI_BUDGET_WARNING, key)) {
            return;
        }
        securityEvents.recordAnonymous(SecurityEventType.AI_BUDGET_WARNING, SecurityOutcome.SUCCESS, null, null,
                key + ": " + message);
    }

    private static String usd(BigDecimal amount) {
        return "$" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
