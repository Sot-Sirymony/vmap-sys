package com.visionmapping.service.report;

import com.visionmapping.exception.BusinessRuleException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import org.springframework.lang.Nullable;

/**
 * V19 RM-FR-06: the reporting period, from and to inclusive. Presets end
 * today while the period is still running; past presets cover the whole
 * week, month or quarter.
 */
public record ReportPeriod(Kind kind, LocalDate from, LocalDate to) {

    public enum Kind { THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_MONTH, THIS_QUARTER, LAST_QUARTER, CUSTOM }

    /** A custom period covers at most this many days. */
    public static final int MAX_DAYS = 366;

    /**
     * V19 RM-FR-08: obstacles closed before this date had no closing date;
     * migration V79 used their last update instead, so the report says so
     * for periods that start earlier. Set to the V19 release date.
     */
    public static final LocalDate RESOLVED_AT_RECORDED_FROM = LocalDate.of(2026, 11, 17);

    static final String UNKNOWN_PERIOD = "Unknown report period.";
    static final String CUSTOM_NEEDS_DATES = "A custom period needs a from date and a to date.";
    static final String FROM_AFTER_TO = "The period must start on or before its end.";
    static final String ENDS_AFTER_TODAY = "The period cannot end after today.";
    static final String TOO_LONG = "A custom period can cover at most 366 days.";

    /**
     * Reads the request: a preset name, or from and to dates (a custom
     * period). Neither means this month.
     */
    public static ReportPeriod resolve(@Nullable String kind, @Nullable LocalDate from, @Nullable LocalDate to, LocalDate today) {
        Kind chosen;
        if (kind == null || kind.isBlank()) {
            chosen = from != null || to != null ? Kind.CUSTOM : Kind.THIS_MONTH;
        } else {
            try {
                chosen = Kind.valueOf(kind.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new BusinessRuleException(UNKNOWN_PERIOD);
            }
        }
        return chosen == Kind.CUSTOM ? custom(from, to, today) : preset(chosen, today);
    }

    static ReportPeriod preset(Kind kind, LocalDate today) {
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate quarterStart = today.with(IsoFields.DAY_OF_QUARTER, 1);
        return switch (kind) {
            case THIS_WEEK -> new ReportPeriod(kind, monday, today);
            case LAST_WEEK -> new ReportPeriod(kind, monday.minusWeeks(1), monday.minusDays(1));
            case THIS_MONTH -> new ReportPeriod(kind, today.withDayOfMonth(1), today);
            case LAST_MONTH -> new ReportPeriod(kind, today.withDayOfMonth(1).minusMonths(1), today.withDayOfMonth(1).minusDays(1));
            case THIS_QUARTER -> new ReportPeriod(kind, quarterStart, today);
            case LAST_QUARTER -> new ReportPeriod(kind, quarterStart.minusMonths(3), quarterStart.minusDays(1));
            case CUSTOM -> throw new IllegalArgumentException("A custom period needs dates.");
        };
    }

    private static ReportPeriod custom(@Nullable LocalDate from, @Nullable LocalDate to, LocalDate today) {
        if (from == null || to == null) {
            throw new BusinessRuleException(CUSTOM_NEEDS_DATES);
        }
        if (from.isAfter(to)) {
            throw new BusinessRuleException(FROM_AFTER_TO);
        }
        if (to.isAfter(today)) {
            throw new BusinessRuleException(ENDS_AFTER_TODAY);
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new BusinessRuleException(TOO_LONG);
        }
        return new ReportPeriod(Kind.CUSTOM, from, to);
    }

    public boolean contains(@Nullable LocalDate date) {
        return date != null && !date.isBefore(from) && !date.isAfter(to);
    }

    /** RM-FR-09: status figures cannot be rebuilt for a past day, so they stay as of today. */
    public boolean endsBeforeToday(LocalDate today) {
        return to.isBefore(today);
    }
}
