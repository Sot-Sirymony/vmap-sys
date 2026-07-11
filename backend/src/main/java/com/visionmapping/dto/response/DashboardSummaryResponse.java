package com.visionmapping.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * One payload carrying everything the dashboard renders. The breakdown
 * fields (tasksByStatus onward) used to be computed client-side from seven
 * separate full-list fetches; serving them here lets the dashboard load
 * with a single request.
 */
public record DashboardSummaryResponse(
        long totalVisionAreas,
        long activeDreams,
        long activeGoals,
        long activeTasks,
        long completedTasks,
        long overdueTasks,
        long blockedTasks,
        BigDecimal averageProgress,
        long tasksDueThisWeek,
        Map<String, Long> goalsByStatus,
        Map<String, Long> dreamsByVisionArea,
        Map<String, Long> tasksByStatus,
        Map<String, Long> tasksByPriority,
        Map<String, Long> activeObstaclesByType,
        Map<String, Long> partnersByStatus,
        Map<String, Long> reviewCadence,
        List<TrendPoint> progressTrend,
        List<AreaProgress> visionAreaProgress,
        List<TaskItemResponse> priorityTasks
) {

    /** Weekly sample of the running average task progress. */
    public record TrendPoint(LocalDate weekEnd, BigDecimal progress) {
    }

    /** Average goal progress for one vision area, pre-sorted lowest first. */
    public record AreaProgress(String name, BigDecimal progress) {
    }
}
