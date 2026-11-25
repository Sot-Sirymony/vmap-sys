package com.visionmapping.service;

import com.visionmapping.dto.response.DashboardSummaryResponse;
import com.visionmapping.entity.StreamlineItem;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.enums.StreamlineDecision;
import com.visionmapping.entity.enums.StreamlineStatus;
import com.visionmapping.repository.StreamlineItemRepository;
import com.visionmapping.repository.StreamlineReviewRepository;
import com.visionmapping.service.support.CycleTimeCalculator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * V27 SR-FR-13: the dashboard's Streamline card — open reviews, items cut and
 * brought back this quarter (counts, Q-27-7), and the median cycle time over the
 * last 90 days with a weekly trend.
 */
@Component
@RequiredArgsConstructor
public class StreamlineDashboard {

    static final int TREND_WEEKS = 8;

    private final StreamlineReviewRepository reviews;
    private final StreamlineItemRepository items;
    private final CycleTimeCalculator cycleTime;
    private final StreamlineNudges nudges;
    private final Clock clock;

    public DashboardSummaryResponse.Streamline build(Long userId, Collection<TaskItem> tasks) {
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.now(clock);
        LocalDate quarterStart = today.withMonth(((today.getMonthValue() - 1) / 3) * 3 + 1).withDayOfMonth(1);
        List<StreamlineItem> cut = items.findArchivedSince(userId, quarterStart.atStartOfDay(zone).toInstant()).stream()
                .filter(item -> item.getDecision() == StreamlineDecision.DELETE || item.getDecision() == StreamlineDecision.MERGE)
                .toList();
        long addedBack = cut.stream().filter(item -> item.getRestoredAt() != null).count();
        List<DashboardSummaryResponse.CyclePoint> trend = new ArrayList<>();
        LocalDate weekEnd = today;
        for (int week = 0; week < TREND_WEEKS; week++) {
            LocalDate from = weekEnd.minusDays(6);
            LocalDate start = from;
            LocalDate end = weekEnd;
            List<BigDecimal> values = tasks.stream()
                    .filter(task -> task.cycleTimeDays() != null)
                    .filter(task -> {
                        LocalDate done = task.getCompletedAt().atZone(zone).toLocalDate();
                        return !done.isBefore(start) && !done.isAfter(end);
                    })
                    .map(TaskItem::cycleTimeDays)
                    .toList();
            trend.add(0, new DashboardSummaryResponse.CyclePoint(weekEnd, CycleTimeCalculator.median(values), values.size()));
            weekEnd = from.minusDays(1);
        }
        return new DashboardSummaryResponse.Streamline(reviews.countByUser_IdAndStatus(userId, StreamlineStatus.OPEN),
                cut.size(), addedBack, cycleTime.medianCycleDays(tasks), trend, nudges.nudgeFor(userId, today).orElse(null));
    }
}
