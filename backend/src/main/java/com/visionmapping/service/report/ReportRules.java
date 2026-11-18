package com.visionmapping.service.report;

import com.visionmapping.entity.Obstacle;
import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.entity.enums.Severity;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.service.report.ManagementReport.BlockerInfo;
import com.visionmapping.service.report.ManagementReport.GoalInfo;
import com.visionmapping.service.report.ManagementReport.GoalNode;
import com.visionmapping.service.report.ManagementReport.StepInfo;
import com.visionmapping.service.report.ManagementReport.TaskInfo;
import com.visionmapping.service.report.ManagementReport.TaskNode;
import com.visionmapping.util.Copies;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * V18 (BRD 18.0.0, "Definitions"): the rules the Summary tab, the Excel report
 * and the deck share, so their figures always agree (MR-NFR-03).
 */
public final class ReportRules {

    /**
     * Goal health (MR-FR-11), in the order the goal health table sorts by.
     * V19 RM-FR-02: DONE_OPEN_WORK is a Completed goal that still has open
     * tasks; it sorts before the goals that need nothing.
     */
    public enum Health { RED, AMBER, DONE_OPEN_WORK, GREEN, DONE }

    /**
     * V19 RM-FR-01: why a goal has its health. The code says which rule
     * applied; value, elapsed and title fill in its words:
     * <ul>
     *   <li>COMPLETED, ON_TRACK: no values</li>
     *   <li>COMPLETED_OPEN_WORK: value = open tasks</li>
     *   <li>GOAL_OVERDUE: value = days past the target date</li>
     *   <li>BLOCKED_TASKS, OVERDUE_WORK, OPEN_BLOCKERS: value = how many</li>
     *   <li>CRITICAL_BLOCKER: value = how many, title = the first one's title</li>
     *   <li>BEHIND_SCHEDULE: value = progress (%), elapsed = share of time gone (%)</li>
     * </ul>
     */
    public record Reason(String code, @Nullable Long value, @Nullable Long elapsed, @Nullable String title) {

        static Reason of(String code) {
            return new Reason(code, null, null, null);
        }

        static Reason of(String code, long value) {
            return new Reason(code, value, null, null);
        }
    }

    /** A goal's health and every rule that set it, at the level that decided it. */
    public record Assessment(Health health, List<Reason> reasons) {

        public Assessment {
            reasons = Copies.list(reasons);
        }
    }

    /** Amber when progress is more than this many points behind the share of time elapsed (Q-02). */
    static final int BEHIND_POINTS = 20;
    /** "Due soon": due within this many days and not Completed. */
    public static final int DUE_SOON_DAYS = 14;

    private ReportRules() {
    }

    /** Core Business Rule 9. */
    public static boolean isOverdue(TaskInfo task, LocalDate today) {
        return overdue(task.status(), task.dueDate(), today);
    }

    public static boolean isOverdue(StepInfo step, LocalDate today) {
        return overdue(step.status(), step.targetDate(), today);
    }

    public static boolean isOverdue(GoalInfo goal, LocalDate today) {
        return overdue(goal.status(), goal.targetDate(), today);
    }

    private static boolean overdue(WorkStatus status, @Nullable LocalDate due, LocalDate today) {
        return status != WorkStatus.COMPLETED && due != null && due.isBefore(today);
    }

    public static boolean isDueSoon(TaskInfo task, LocalDate today) {
        return task.status() != WorkStatus.COMPLETED && task.dueDate() != null && !task.dueDate().isBefore(today)
                && !task.dueDate().isAfter(today.plusDays(DUE_SOON_DAYS));
    }

    /** An obstacle that still needs action: Open or In Progress, and not archived. */
    public static boolean isOpen(Obstacle obstacle) {
        return !obstacle.isArchived()
                && (obstacle.getStatus() == ObstacleStatus.OPEN || obstacle.getStatus() == ObstacleStatus.IN_PROGRESS);
    }

    public static Health health(GoalNode node, LocalDate today) {
        return assessmentOf(node, today).health();
    }

    /** The goal's stored assessment (judged before filtering, RM-FR-11), else one made now. */
    public static Assessment assessmentOf(GoalNode node, LocalDate today) {
        return node.assessment() != null ? node.assessment() : assess(node, today);
    }

    /**
     * MR-FR-11, RM-FR-01, RM-FR-02. Done when Completed (Done – open work if
     * tasks are still open). Red when the goal is overdue, or holds a Blocked
     * task or an open Critical blocker. Amber when it holds an overdue task or
     * step or any open blocker, or its progress is more than 20 points behind
     * the share of time elapsed since it was created. Green otherwise. The
     * reasons list every rule that applied at the deciding level.
     */
    public static Assessment assess(GoalNode node, LocalDate today) {
        GoalInfo goal = node.goal();
        if (goal.status() == WorkStatus.COMPLETED) {
            long open = node.tasks().filter(task -> task.task().status() != WorkStatus.COMPLETED).count();
            return open > 0
                    ? new Assessment(Health.DONE_OPEN_WORK, List.of(Reason.of("COMPLETED_OPEN_WORK", open)))
                    : new Assessment(Health.DONE, List.of(Reason.of("COMPLETED")));
        }
        List<Reason> red = new ArrayList<>();
        if (isOverdue(goal, today)) {
            red.add(Reason.of("GOAL_OVERDUE", ChronoUnit.DAYS.between(goal.targetDate(), today)));
        }
        long blockedTasks = node.tasks().filter(task -> task.task().status() == WorkStatus.BLOCKED).count();
        if (blockedTasks > 0) {
            red.add(Reason.of("BLOCKED_TASKS", blockedTasks));
        }
        List<BlockerInfo> critical = node.blockers().filter(blocker -> blocker.severity() == Severity.CRITICAL).toList();
        if (!critical.isEmpty()) {
            red.add(new Reason("CRITICAL_BLOCKER", (long) critical.size(), null, critical.get(0).title()));
        }
        if (!red.isEmpty()) {
            return new Assessment(Health.RED, red);
        }
        List<Reason> amber = new ArrayList<>();
        long overdueWork = node.tasks().filter(task -> isOverdue(task.task(), today)).count()
                + node.steps().stream().filter(step -> isOverdue(step.step(), today)).count();
        if (overdueWork > 0) {
            amber.add(Reason.of("OVERDUE_WORK", overdueWork));
        }
        long blockers = node.blockers().count();
        if (blockers > 0) {
            amber.add(Reason.of("OPEN_BLOCKERS", blockers));
        }
        double share = elapsedShare(goal, today);
        double progress = goal.progress().doubleValue();
        if (share >= 0 && share * 100 - progress > BEHIND_POINTS) {
            amber.add(new Reason("BEHIND_SCHEDULE", Math.round(progress), Math.round(share * 100), null));
        }
        return amber.isEmpty() ? new Assessment(Health.GREEN, List.of(Reason.of("ON_TRACK"))) : new Assessment(Health.AMBER, amber);
    }

    /**
     * The share of time elapsed between creation and the target date, 0 to 1;
     * -1 without a creation date, a target date, or a target after creation.
     */
    private static double elapsedShare(GoalInfo goal, LocalDate today) {
        if (goal.targetDate() == null || goal.createdOn() == null) {
            return -1;
        }
        long span = ChronoUnit.DAYS.between(goal.createdOn(), goal.targetDate());
        if (span <= 0) {
            return -1;
        }
        return Math.min(1, Math.max(0, ChronoUnit.DAYS.between(goal.createdOn(), today) / (double) span));
    }

    /**
     * A goal's next action: the first task not Completed, in step and task
     * order, by its own Next Action, else its title. Null when nothing is open.
     */
    @Nullable
    public static String nextAction(GoalNode node) {
        return node.tasks()
                .map(TaskNode::task)
                .filter(task -> task.status() != WorkStatus.COMPLETED)
                .findFirst()
                .map(task -> blank(task.nextAction()) ? task.title() : task.nextAction().strip())
                .orElse(null);
    }

    static boolean blank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}
