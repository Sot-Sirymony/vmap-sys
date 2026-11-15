package com.visionmapping.service.report;

import com.visionmapping.entity.Goal;
import com.visionmapping.entity.Obstacle;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.entity.enums.Severity;
import com.visionmapping.entity.enums.WorkStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.lang.Nullable;

/**
 * V18 (BRD 18.0.0, "Definitions"): the rules the Summary tab, the Excel report
 * and the deck share, so their figures always agree (MR-NFR-03).
 */
public final class ReportRules {

    /** Goal health (MR-FR-11), in the order the goal health table sorts by. */
    public enum Health { RED, AMBER, GREEN, DONE }

    /** Amber when progress is more than this many points behind the share of time elapsed (Q-02). */
    static final int BEHIND_POINTS = 20;
    /** "Due soon": due within this many days and not Completed. */
    public static final int DUE_SOON_DAYS = 14;

    private ReportRules() {
    }

    /** Core Business Rule 9. */
    public static boolean isOverdue(TaskItem task, LocalDate today) {
        return task.getStatus() != WorkStatus.COMPLETED && task.getDueDate() != null && task.getDueDate().isBefore(today);
    }

    public static boolean isOverdue(VisionStep step, LocalDate today) {
        return step.getStatus() != WorkStatus.COMPLETED && step.getTargetDate() != null && step.getTargetDate().isBefore(today);
    }

    public static boolean isOverdue(Goal goal, LocalDate today) {
        return goal.getStatus() != WorkStatus.COMPLETED && goal.getTargetDate() != null && goal.getTargetDate().isBefore(today);
    }

    public static boolean isDueSoon(TaskItem task, LocalDate today) {
        return task.getStatus() != WorkStatus.COMPLETED && task.getDueDate() != null && !task.getDueDate().isBefore(today)
                && !task.getDueDate().isAfter(today.plusDays(DUE_SOON_DAYS));
    }

    /** An obstacle that still needs action: Open or In Progress, and not archived. */
    public static boolean isOpen(Obstacle obstacle) {
        return !obstacle.isArchived()
                && (obstacle.getStatus() == ObstacleStatus.OPEN || obstacle.getStatus() == ObstacleStatus.IN_PROGRESS);
    }

    /**
     * MR-FR-11. Done when Completed. Red when the goal is overdue, or holds a
     * Blocked task or an open Critical blocker. Amber when it holds an overdue
     * task or step or any open blocker, or its progress is more than 20 points
     * behind the share of time elapsed since it was created. Green otherwise.
     */
    public static Health health(ManagementReport.GoalNode node, LocalDate today, ZoneId zone) {
        Goal goal = node.goal();
        if (goal.getStatus() == WorkStatus.COMPLETED) {
            return Health.DONE;
        }
        boolean blockedTask = node.tasks().anyMatch(task -> task.task().getStatus() == WorkStatus.BLOCKED);
        boolean criticalBlocker = node.blockers().anyMatch(obstacle -> obstacle.getSeverity() == Severity.CRITICAL);
        if (isOverdue(goal, today) || blockedTask || criticalBlocker) {
            return Health.RED;
        }
        boolean overdueWork = node.tasks().anyMatch(task -> isOverdue(task.task(), today))
                || node.steps().stream().anyMatch(step -> isOverdue(step.step(), today));
        boolean anyBlocker = node.blockers().findAny().isPresent();
        if (overdueWork || anyBlocker || behindSchedule(goal, today, zone)) {
            return Health.AMBER;
        }
        return Health.GREEN;
    }

    /** Progress more than 20 points behind the share of time elapsed between creation and the target date. */
    static boolean behindSchedule(Goal goal, LocalDate today, ZoneId zone) {
        if (goal.getTargetDate() == null || goal.getCreatedAt() == null) {
            return false;
        }
        LocalDate start = goal.getCreatedAt().atZone(zone).toLocalDate();
        long span = ChronoUnit.DAYS.between(start, goal.getTargetDate());
        if (span <= 0) {
            return false;
        }
        double elapsed = Math.min(1, Math.max(0, ChronoUnit.DAYS.between(start, today) / (double) span));
        double progress = Optional.ofNullable(goal.getProgressPercent()).orElse(BigDecimal.ZERO).doubleValue();
        return elapsed * 100 - progress > BEHIND_POINTS;
    }

    /**
     * A goal's next action: the first task not Completed, in step and task
     * order, by its own Next Action, else its title. Null when nothing is open.
     */
    @Nullable
    public static String nextAction(ManagementReport.GoalNode node) {
        return node.tasks()
                .map(ManagementReport.TaskNode::task)
                .filter(task -> task.getStatus() != WorkStatus.COMPLETED)
                .findFirst()
                .map(task -> blank(task.getNextAction()) ? task.getTitle() : task.getNextAction().strip())
                .orElse(null);
    }

    static boolean blank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}
