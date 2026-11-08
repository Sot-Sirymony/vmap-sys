package com.visionmapping.service.support;

import com.visionmapping.entity.Goal;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.repository.ProgressRollup;
import com.visionmapping.repository.TaskItemRepository;
import com.visionmapping.repository.VisionStepRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * CLAUDE.md Core Business Rules 10 and 11. Owns the one progress rule the whole method depends on: a parent's progress
 * is the average of its unarchived children, and a parent auto-completes once
 * every child is complete. Recalculating a step rolls the change up into its
 * goal, so callers only need to nudge the level they changed.
 */
@Component
@RequiredArgsConstructor
public class ProgressCalculator {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP);

    private final VisionStepRepository visionStepRepository;
    private final TaskItemRepository taskItemRepository;

    public void recalculateStep(VisionStep step) {
        Objects.requireNonNull(step, "step");
        if (step.isManualProgressOverride()) {
            recalculateGoal(step.getGoal());
            return;
        }
        ProgressRollup tasks = taskItemRepository.rollUpForStep(step.getId(), step.getUser().getId());
        step.applyRollup(average(tasks), tasks.allComplete());
        recalculateGoal(step.getGoal());
    }

    public void recalculateGoal(Goal goal) {
        Objects.requireNonNull(goal, "goal");
        if (goal.isManualProgressOverride()) {
            return;
        }
        ProgressRollup steps = visionStepRepository.rollUpForGoal(goal.getId(), goal.getUser().getId());
        goal.applyRollup(average(steps), steps.allComplete());
    }

    /** Same rounding the per-row version used: one division at scale 2, HALF_UP. */
    public BigDecimal average(ProgressRollup rollup) {
        Objects.requireNonNull(rollup, "rollup");
        if (rollup.isEmpty()) {
            return ZERO;
        }
        return rollup.progressSum().divide(BigDecimal.valueOf(rollup.childCount()), 2, RoundingMode.HALF_UP);
    }

    /**
     * CLAUDE.md Core Business Rule 9: overdue means due before today and not
     * Completed. A task with no due date (possible via Excel import) is never overdue.
     */
    public boolean isOverdue(TaskItem task, LocalDate today) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(today, "today");
        return task.getDueDate() != null
                && task.getDueDate().isBefore(today)
                && task.getStatus() != WorkStatus.COMPLETED;
    }

    public BigDecimal normalizeProgress(@Nullable BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(ONE_HUNDRED) > 0) {
            throw new BusinessRuleException("Progress percent must be between 0 and 100.");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
