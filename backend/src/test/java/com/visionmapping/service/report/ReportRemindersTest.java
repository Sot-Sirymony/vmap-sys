package com.visionmapping.service.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.visionmapping.entity.enums.ReportReminder;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

/** V19 RM-FR-49: when a report reminder falls and how long its notice shows. */
class ReportRemindersTest {

    // Wednesday 18 November 2026.
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 11, 18);

    @Test
    void weeklyFallsOnTheLatestChosenWeekdayOnOrBeforeToday() {
        assertThat(ReportReminders.lastDue(ReportReminder.WEEKLY, 3, WEDNESDAY)).isEqualTo(WEDNESDAY);
        assertThat(ReportReminders.lastDue(ReportReminder.WEEKLY, 1, WEDNESDAY)).isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(ReportReminders.lastDue(ReportReminder.WEEKLY, 5, WEDNESDAY)).isEqualTo(LocalDate.of(2026, 11, 13));
    }

    @Test
    void anOffOrIncompleteReminderNeverFalls() {
        assertThat(ReportReminders.lastDue(ReportReminder.NONE, 3, WEDNESDAY)).isNull();
        assertThat(ReportReminders.lastDue(null, null, WEDNESDAY)).isNull();
        assertThat(ReportReminders.lastDue(ReportReminder.WEEKLY, null, WEDNESDAY)).isNull();
        assertThat(ReportReminders.lastDue(ReportReminder.WEEKLY, 0, WEDNESDAY)).isNull();
        assertThat(ReportReminders.lastDue(ReportReminder.WEEKLY, 8, WEDNESDAY)).isNull();
    }

    @Test
    void monthlyFallsOnTheLastWorkingDayOfThisMonthOnceItHasCome() {
        // October 2026 ends on a Saturday, so its last working day is Friday the 30th.
        assertThat(ReportReminders.lastWorkingDay(YearMonth.of(2026, 10))).isEqualTo(LocalDate.of(2026, 10, 30));
        // May 2026 ends on a Sunday.
        assertThat(ReportReminders.lastWorkingDay(YearMonth.of(2026, 5))).isEqualTo(LocalDate.of(2026, 5, 29));
        assertThat(ReportReminders.lastWorkingDay(YearMonth.of(2026, 11))).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(ReportReminders.lastDue(ReportReminder.MONTHLY, null, WEDNESDAY)).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(ReportReminders.lastDue(ReportReminder.MONTHLY, null, LocalDate.of(2026, 11, 30))).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    void theNoticeShowsForAWeekFromItsDay() {
        assertThat(ReportReminders.stillShown(WEDNESDAY, WEDNESDAY)).isTrue();
        assertThat(ReportReminders.stillShown(WEDNESDAY, WEDNESDAY.plusDays(6))).isTrue();
        assertThat(ReportReminders.stillShown(WEDNESDAY, WEDNESDAY.plusDays(7))).isFalse();
        assertThat(ReportReminders.stillShown(WEDNESDAY, WEDNESDAY.minusDays(1))).isFalse();
    }
}
