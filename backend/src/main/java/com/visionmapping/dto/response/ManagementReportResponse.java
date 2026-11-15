package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * V18 (BRD 18.0.0, MR-FR-07 to MR-FR-13): the Management Report's Summary.
 * Built from the same model as the Excel report and the deck, so the figures
 * always agree (MR-NFR-03).
 */
public record ManagementReportResponse(
        Scope scope,
        LocalDate asOf,
        Headline headline,
        // All six work statuses, in board order, zero when none.
        Map<String, Long> tasksByStatus,
        List<PriorityCount> openTasksByPriority,
        // Open obstacles on a step or task in scope, by kind.
        Map<String, Long> blockersByKind,
        // V17 BL-FR-05: active obstacles in scope that are not on a step yet.
        long unplacedObstacles,
        List<GoalHealthRow> goals,
        List<AttentionItem> attention
) {

    public ManagementReportResponse {
        tasksByStatus = Copies.map(tasksByStatus);
        openTasksByPriority = Copies.list(openTasksByPriority);
        blockersByKind = Copies.map(blockersByKind);
        goals = Copies.list(goals);
        attention = Copies.list(attention);
    }

    /** kind: ALL, AREA or DREAM. label names the area or dream, or is null for ALL. */
    public record Scope(String kind, Long areaId, Long dreamId, String label) {
    }

    public record Headline(BigDecimal overallProgress, Map<String, Long> goalsByHealth, long totalTasks, long completedTasks,
                           long overdueTasks, long blockedTasks, long openBlockers, long dueSoonTasks) {

        public Headline {
            goalsByHealth = Copies.map(goalsByHealth);
        }
    }

    /** Open (not Completed) tasks of one priority, split by whether they are overdue. */
    public record PriorityCount(String priority, long onTrack, long overdue) {
    }

    /** MR-FR-11: health is GREEN, AMBER, RED or DONE. */
    public record GoalHealthRow(Long goalId, String goalCode, String goalTitle, Long dreamId, String dreamTitle, String health,
                                BigDecimal progress, LocalDate targetDate, long overdueTasks, long blockedItems,
                                String nextAction) {
    }

    /**
     * MR-FR-12: kind is CRITICAL_BLOCKER, BLOCKED_TASK, OVERDUE_URGENT, OVERDUE or GAP_NEEDS_HELP. itemType is
     * OBSTACLE, TASK or STEP; stepId and goalId let the page link to the list that holds it.
     */
    public record AttentionItem(String kind, String itemType, Long itemId, String title, String context, Long stepId,
                                Long goalId) {
    }
}
