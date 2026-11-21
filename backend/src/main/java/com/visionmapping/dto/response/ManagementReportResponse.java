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
        // V19 RM-FR-06, RM-FR-09: the reporting period.
        Period period,
        // V19 RM-FR-08: what happened in the period.
        PeriodFigures periodFigures,
        Headline headline,
        // All six work statuses, in board order, zero when none.
        Map<String, Long> tasksByStatus,
        List<PriorityCount> openTasksByPriority,
        // Open obstacles on a step or task in scope, by kind.
        Map<String, Long> blockersByKind,
        // V17 BL-FR-05: active obstacles in scope that are not on a step yet.
        long unplacedObstacles,
        List<GoalHealthRow> goals,
        List<AttentionItem> attention,
        // MR-FR-14: weekly average task progress for the last 12 weeks, from the progress log; empty without history.
        List<DashboardSummaryResponse.TrendPoint> trend,
        // V19 RM-FR-10: the filters applied, and the owners in scope to choose from.
        Filters filters,
        List<String> owners,
        // V19 RM-FR-32 to RM-FR-37: the changes since an earlier final report, or null when not compared.
        Comparison comparison
) {

    public ManagementReportResponse {
        tasksByStatus = Copies.map(tasksByStatus);
        openTasksByPriority = Copies.list(openTasksByPriority);
        blockersByKind = Copies.map(blockersByKind);
        goals = Copies.list(goals);
        attention = Copies.list(attention);
        trend = Copies.list(trend);
        owners = Copies.list(owners);
    }

    /** The same Summary, compared with an earlier report (or not, for null). */
    public ManagementReportResponse withComparison(Comparison value) {
        return new ManagementReportResponse(scope, asOf, period, periodFigures, headline, tasksByStatus, openTasksByPriority,
                blockersByKind, unplacedObstacles, goals, attention, trend, filters, owners, value);
    }

    /**
     * V19 RM-FR-32 to RM-FR-37: what changed since the final report {@code reportId}, frozen on {@code asOf}.
     * Items are matched by id. changes is most important first; totalChanges counts them all.
     */
    public record Comparison(Long reportId, String code, String name, LocalDate asOf, HeadlineChange headline,
                             List<GoalChange> goals, List<RemovedGoal> removed, List<ChangeItem> changes, int totalChanges) {

        public Comparison {
            goals = Copies.list(goals);
            removed = Copies.list(removed);
            changes = Copies.list(changes);
        }

        /** The change for one goal id, or null when the goal is not in this report. */
        public GoalChange goal(Long goalId) {
            return goals == null ? null : goals.stream().filter(change -> change.goalId().equals(goalId)).findFirst().orElse(null);
        }
    }

    /** RM-FR-33: each headline figure now minus the compared report's. */
    public record HeadlineChange(BigDecimal overallProgress, long totalTasks, long completedTasks, long overdueTasks,
                                 long blockedTasks, long openBlockers, long dueSoonTasks) {
    }

    /**
     * RM-FR-34, RM-FR-37: isNew when the goal was not in the compared report (the other fields are then null).
     * healthBefore and progressChange (points); targetBefore only when the target date changed; previousTitle only
     * when the goal was renamed.
     */
    public record GoalChange(Long goalId, boolean isNew, String healthBefore, BigDecimal progressChange, LocalDate targetBefore,
                             String previousTitle) {
    }

    /**
     * RM-FR-34: a goal in the compared report that is not in this one. reason is COMPLETED_ARCHIVED, ARCHIVED,
     * MOVED (to a dream outside the scope) or null when not known.
     */
    public record RemovedGoal(Long goalId, String title, String reason) {
    }

    /**
     * RM-FR-35: one line of What changed. kind is GOAL_WORSE, DUE_LATER, NEW_BLOCKER, BLOCKER_CLOSED, GOAL_BETTER or
     * TASK_COMPLETED; itemType GOAL, STEP, TASK or OBSTACLE. before and after are a health or an ISO date, or null.
     * renamed: the item's title changed since the compared report (RM-FR-37).
     */
    public record ChangeItem(String kind, String itemType, Long itemId, String title, String context, String before,
                             String after, boolean renamed) {
    }

    /** V19 RM-FR-10: empty lists and a null owner mean "any". */
    public record Filters(List<String> priorities, String owner, List<String> healths, boolean includeCompleted) {

        public Filters {
            priorities = Copies.list(priorities);
            healths = Copies.list(healths);
        }
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

    /**
     * V19 RM-FR-06, RM-FR-09. kind: THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_MONTH, THIS_QUARTER, LAST_QUARTER or
     * CUSTOM; from and to inclusive. statusAsOfToday: the period ended before today, so status figures are
     * today's. closedBeforeRecording: the period starts before closing dates were recorded (migration V79).
     */
    public record Period(String kind, LocalDate from, LocalDate to, boolean statusAsOfToday, boolean closedBeforeRecording) {
    }

    /**
     * V19 RM-FR-08, on the tasks and placed blockers in scope. progressGained: points of average task progress
     * gained in the period. tasksBecameOverdue: due in the period, before today, and not Completed by its end.
     */
    public record PeriodFigures(long tasksCompleted, BigDecimal progressGained, long blockersOpened, long blockersClosed,
                                long tasksBecameOverdue) {
    }

    /**
     * V19 RM-FR-01: one rule that set a goal's health. code is COMPLETED, COMPLETED_OPEN_WORK, GOAL_OVERDUE,
     * BLOCKED_TASKS, CRITICAL_BLOCKER, OVERDUE_WORK, OPEN_BLOCKERS, BEHIND_SCHEDULE or ON_TRACK; value, elapsed
     * and title fill in its words (see ReportRules.Reason).
     */
    public record HealthReason(String code, Long value, Long elapsed, String title) {
    }

    /** Open (not Completed) tasks of one priority, split by whether they are overdue. */
    public record PriorityCount(String priority, long onTrack, long overdue) {
    }

    /** MR-FR-11: health is RED, AMBER, DONE_OPEN_WORK (V19 RM-FR-02), GREEN or DONE, with its reasons (RM-FR-01). */
    public record GoalHealthRow(Long goalId, String goalCode, String goalTitle, Long dreamId, String dreamTitle, String health,
                                BigDecimal progress, LocalDate targetDate, long overdueTasks, long blockedItems,
                                String nextAction, List<HealthReason> reasons) {

        public GoalHealthRow {
            reasons = Copies.list(reasons);
        }
    }

    /**
     * MR-FR-12: kind is CRITICAL_BLOCKER, BLOCKED_TASK, OVERDUE_URGENT, OVERDUE, REOPEN_OR_FINISH (V19 RM-FR-02)
     * or GAP_NEEDS_HELP. itemType is OBSTACLE, TASK, STEP or GOAL; stepId (null for a goal) and goalId let the page
     * link to the list that holds it.
     */
    public record AttentionItem(String kind, String itemType, Long itemId, String title, String context, Long stepId,
                                Long goalId) {
    }
}
