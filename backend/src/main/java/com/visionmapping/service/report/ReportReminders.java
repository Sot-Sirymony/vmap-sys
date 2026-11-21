package com.visionmapping.service.report;

import com.visionmapping.entity.enums.ReportReminder;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import org.springframework.lang.Nullable;

/**
 * V19 (RM-FR-49): when a report reminder falls. Weekly is the chosen weekday;
 * monthly is the last working day, Monday to Friday (public holidays are not
 * known, so they count as working days).
 */
public final class ReportReminders {

    /** The in-app notice shows for a week from its day, until a report is finalised. */
    public static final int NOTICE_DAYS = 7;

    private ReportReminders() {
    }

    /** The latest reminder day on or before today, or null when the reminder is off or incomplete. */
    @Nullable
    public static LocalDate lastDue(@Nullable ReportReminder cadence, @Nullable Integer weekday, LocalDate today) {
        if (cadence == ReportReminder.WEEKLY && weekday != null && weekday >= 1 && weekday <= 7) {
            return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.of(weekday)));
        }
        if (cadence == ReportReminder.MONTHLY) {
            LocalDate thisMonth = lastWorkingDay(YearMonth.from(today));
            return thisMonth.isAfter(today) ? lastWorkingDay(YearMonth.from(today).minusMonths(1)) : thisMonth;
        }
        return null;
    }

    public static LocalDate lastWorkingDay(YearMonth month) {
        LocalDate day = month.atEndOfMonth();
        // Saturday is 6 and Sunday 7 (ISO day numbers).
        while (day.getDayOfWeek().getValue() > DayOfWeek.FRIDAY.getValue()) {
            day = day.minusDays(1);
        }
        return day;
    }

    /** Whether a reminder that fell on dueOn is still shown today. */
    public static boolean stillShown(LocalDate dueOn, LocalDate today) {
        return !dueOn.isAfter(today) && dueOn.plusDays(NOTICE_DAYS).isAfter(today);
    }
}
