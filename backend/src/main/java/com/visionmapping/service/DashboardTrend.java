package com.visionmapping.service;

import com.visionmapping.dto.response.DashboardSummaryResponse;
import com.visionmapping.entity.ProgressLog;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.lang.Nullable;

/**
 * The twelve-week progress trend on the dashboard, rebuilt from the
 * progress-log history. Plain static code: it needs only the logs, the day and
 * the time zone. Split out of DashboardService in BRD 13 EJ-FR-29.
 */
final class DashboardTrend {

    private DashboardTrend() {
    }

    /**
     * Weekly samples of the running "average task progress" over the last
     * twelve weeks, reconstructed from the progress-log history: each task's
     * latest logged value as of a given day carries forward until its next
     * change, and each week reports the last day that had any data. Mirrors
     * the same Average Progress KPI, but as a trend instead of one number.
     */
    static List<DashboardSummaryResponse.TrendPoint> build(List<ProgressLog> logs, LocalDate today, ZoneId zone) {
        int totalDays = DashboardService.TREND_WEEKS * DashboardService.DAYS_PER_WEEK;
        LocalDate start = start(today);
        List<BigDecimal> dailyAverages = dailyAverageProgress(groupLogsByTask(logs), start, totalDays, zone);
        return trimLeadingEmptyWeeks(weeklySamples(dailyAverages, start));
    }

    /** First day of the twelve-week trend window; also the cut-off for which log rows are loaded. */
    static LocalDate start(LocalDate today) {
        return today.minusDays((long) DashboardService.TREND_WEEKS * DashboardService.DAYS_PER_WEEK - 1L);
    }

    /** Each task's log entries in the order they were written, so "latest as of a day" is a forward scan. */
    private static Map<Long, List<ProgressLog>> groupLogsByTask(List<ProgressLog> logs) {
        Map<Long, List<ProgressLog>> byTask = logs.stream()
                .collect(Collectors.groupingBy(log -> log.getRelatedTask().getId()));
        byTask.values().forEach(entries -> entries.sort(Comparator.comparing(ProgressLog::getLoggedAt)));
        return byTask;
    }

    /** Average of every task's carried-forward progress, one value per day; null on days with no data yet. */
    private static List<BigDecimal> dailyAverageProgress(Map<Long, List<ProgressLog>> logsByTask, LocalDate start, int totalDays,
                                                         ZoneId zone) {
        List<BigDecimal> dailyAverages = new ArrayList<>(totalDays);
        for (int i = 0; i < totalDays; i++) {
            LocalDate cursor = start.plusDays(i);
            List<BigDecimal> valuesAsOfDay = logsByTask.values().stream()
                    .map(entries -> latestProgressAsOf(entries, cursor, zone))
                    .filter(Objects::nonNull)
                    .toList();
            dailyAverages.add(valuesAsOfDay.isEmpty()
                    ? null
                    : valuesAsOfDay.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(valuesAsOfDay.size()), 2, RoundingMode.HALF_UP));
        }
        return dailyAverages;
    }

    /** The last value logged on or before the cursor day, or null if the task had no log yet. */
    @Nullable
    private static BigDecimal latestProgressAsOf(List<ProgressLog> sortedEntries, LocalDate cursor, ZoneId zone) {
        ProgressLog latest = null;
        for (ProgressLog entry : sortedEntries) {
            LocalDate logDate = entry.getLoggedAt().atZone(zone).toLocalDate();
            if (logDate.isAfter(cursor)) {
                break;
            }
            latest = entry;
        }
        return latest == null ? null : latest.getProgressPercentAfter();
    }

    /** One point per week, sampled from the last day of that week that had any data. */
    private static List<DashboardSummaryResponse.TrendPoint> weeklySamples(List<BigDecimal> dailyAverages, LocalDate start) {
        List<DashboardSummaryResponse.TrendPoint> weeklyPoints = new ArrayList<>(DashboardService.TREND_WEEKS);
        for (int week = 0; week < DashboardService.TREND_WEEKS; week++) {
            weeklyPoints.add(new DashboardSummaryResponse.TrendPoint(
                    start.plusDays(week * (long) DashboardService.DAYS_PER_WEEK + 6),
                    lastKnownAverageInWeek(dailyAverages, week)));
        }
        return weeklyPoints;
    }

    @Nullable
    private static BigDecimal lastKnownAverageInWeek(List<BigDecimal> dailyAverages, int week) {
        for (int day = week * DashboardService.DAYS_PER_WEEK + 6; day >= week * DashboardService.DAYS_PER_WEEK; day--) {
            if (dailyAverages.get(day) != null) {
                return dailyAverages.get(day);
            }
        }
        return null;
    }

    /** Drops the weeks before the first logged data; gaps after that render as zero. */
    private static List<DashboardSummaryResponse.TrendPoint> trimLeadingEmptyWeeks(
            List<DashboardSummaryResponse.TrendPoint> weeklyPoints) {
        int firstDataIndex = 0;
        while (firstDataIndex < weeklyPoints.size() && weeklyPoints.get(firstDataIndex).progress() == null) {
            firstDataIndex++;
        }
        if (firstDataIndex == weeklyPoints.size()) {
            return List.of();
        }
        return weeklyPoints.subList(firstDataIndex, weeklyPoints.size()).stream()
                .map(point -> new DashboardSummaryResponse.TrendPoint(
                        point.weekEnd(), point.progress() == null ? DashboardService.ZERO : point.progress()))
                .toList();
    }
}
