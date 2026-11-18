package com.visionmapping.service.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.service.report.ReportPeriod.Kind;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** V19 RM-FR-06: the reporting period's presets and the custom period's limits. */
class ReportPeriodTest {

    // A Wednesday in the middle of the year's fourth quarter.
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

    @Test
    void withNothingChosenTheReportCoversThisMonthSoFar() {
        ReportPeriod period = ReportPeriod.resolve(null, null, null, TODAY);

        assertThat(period).isEqualTo(new ReportPeriod(Kind.THIS_MONTH, LocalDate.of(2026, 11, 1), TODAY));
        assertThat(period.endsBeforeToday(TODAY)).isFalse();
    }

    @Test
    void presetsCoverTheRunningOrTheWholePreviousWeekMonthAndQuarter() {
        assertThat(ReportPeriod.resolve("this_week", null, null, TODAY))
                .isEqualTo(new ReportPeriod(Kind.THIS_WEEK, LocalDate.of(2026, 11, 16), TODAY));
        assertThat(ReportPeriod.resolve("LAST_WEEK", null, null, TODAY))
                .isEqualTo(new ReportPeriod(Kind.LAST_WEEK, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15)));
        assertThat(ReportPeriod.resolve("LAST_MONTH", null, null, TODAY))
                .isEqualTo(new ReportPeriod(Kind.LAST_MONTH, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
        assertThat(ReportPeriod.resolve("THIS_QUARTER", null, null, TODAY))
                .isEqualTo(new ReportPeriod(Kind.THIS_QUARTER, LocalDate.of(2026, 10, 1), TODAY));
        ReportPeriod lastQuarter = ReportPeriod.resolve("LAST_QUARTER", null, null, TODAY);
        assertThat(lastQuarter).isEqualTo(new ReportPeriod(Kind.LAST_QUARTER, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30)));
        assertThat(lastQuarter.endsBeforeToday(TODAY)).isTrue();
    }

    @Test
    void datesWithoutAPresetMakeACustomPeriodThatIncludesBothEnds() {
        ReportPeriod period = ReportPeriod.resolve(null, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10), TODAY);

        assertThat(period.kind()).isEqualTo(Kind.CUSTOM);
        assertThat(period.contains(LocalDate.of(2026, 11, 1))).isTrue();
        assertThat(period.contains(LocalDate.of(2026, 11, 10))).isTrue();
        assertThat(period.contains(LocalDate.of(2026, 11, 11))).isFalse();
        assertThat(period.contains(null)).isFalse();
    }

    @Test
    void aCustomPeriodNeedsBothDatesInOrderEndingByTodayAndAtMost366Days() {
        assertRefused("CUSTOM", LocalDate.of(2026, 11, 1), null, ReportPeriod.CUSTOM_NEEDS_DATES);
        assertRefused("CUSTOM", LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 1), ReportPeriod.FROM_AFTER_TO);
        assertRefused(null, LocalDate.of(2026, 11, 1), TODAY.plusDays(1), ReportPeriod.ENDS_AFTER_TODAY);
        assertRefused(null, TODAY.minusDays(366), TODAY, ReportPeriod.TOO_LONG);
        assertThat(ReportPeriod.resolve(null, TODAY.minusDays(365), TODAY, TODAY).kind()).isEqualTo(Kind.CUSTOM);
    }

    @Test
    void anUnknownPresetIsRefused() {
        assertRefused("NEXT_DECADE", null, null, ReportPeriod.UNKNOWN_PERIOD);
    }

    private static void assertRefused(String kind, LocalDate from, LocalDate to, String message) {
        assertThatThrownBy(() -> ReportPeriod.resolve(kind, from, to, TODAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(message);
    }
}
