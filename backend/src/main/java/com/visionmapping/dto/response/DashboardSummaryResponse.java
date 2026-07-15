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
        // Scoped to the dashboard's selectable window (default: current month).
        // Separate from the all-time counts above so the Excel export keeps its
        // all-time numbers while the dashboard tiles show the period.
        long tasksDueInPeriod,
        long completedTasksInPeriod,
        Map<String, Long> goalsByStatus,
        Map<String, Long> dreamsByVisionArea,
        Map<String, Long> tasksByStatus,
        Map<String, Long> tasksByPriority,
        Map<String, Long> activeObstaclesByType,
        Map<String, Long> partnersByStatus,
        Map<String, Long> reviewCadence,
        List<TrendPoint> progressTrend,
        List<AreaProgress> visionAreaProgress,
        List<TaskItemResponse> priorityTasks,
        long weeksWithDiligence,
        long moonshotGoals,
        Attention attention
) {

    /** Weekly sample of the running average task progress. */
    public record TrendPoint(LocalDate weekEnd, BigDecimal progress) {
    }

    /** Average goal progress for one vision area, pre-sorted lowest first. */
    public record AreaProgress(String name, BigDecimal progress) {
    }

    /**
     * Places where the map has stopped being a map: the method's own rules,
     * checked against the user's actual records. Each of these is a dead end a
     * user can accumulate silently — a dream with no goals is a wish, a complex
     * step with no tasks is a step nobody can start, a blocked task with no
     * partner is a task with nothing that will unblock it.
     *
     * These are lists, not counts, because the point is to click straight
     * through to the record and fix it.
     */
    public record Attention(
            List<TaskItemResponse> blockedTasksWithoutPartner,
            List<VisionStepResponse> complexStepsWithoutTasks,
            List<DreamResponse> dreamsWithoutGoals,
            List<GoalResponse> goalsWithoutSteps
    ) {
    }
}
