package com.visionmapping.service.support;

import com.visionmapping.entity.Goal;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.repository.TaskItemRepository;
import com.visionmapping.repository.VisionStepRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Owns the one progress rule the whole method depends on: a parent's progress
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
        if (step.isManualProgressOverride()) {
            recalculateGoal(step.getGoal());
            return;
        }
        List<TaskItem> tasks = taskItemRepository.findByStep_IdAndUser_IdAndArchivedFalse(step.getId(), step.getUser().getId());
        step.setProgressPercent(average(tasks, TaskItem::getProgressPercent));
        if (!tasks.isEmpty() && tasks.stream().allMatch(task -> task.getStatus() == WorkStatus.COMPLETED)) {
            step.setStatus(WorkStatus.COMPLETED);
        }
        recalculateGoal(step.getGoal());
    }

    public void recalculateGoal(Goal goal) {
        if (goal.isManualProgressOverride()) {
            return;
        }
        List<VisionStep> steps = visionStepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(goal.getId(), goal.getUser().getId());
        goal.setProgressPercent(average(steps, VisionStep::getProgressPercent));
        if (!steps.isEmpty() && steps.stream().allMatch(step -> step.getStatus() == WorkStatus.COMPLETED)) {
            goal.setStatus(WorkStatus.COMPLETED);
        }
    }

    public <T> BigDecimal average(List<T> values, Function<T, BigDecimal> progressGetter) {
        if (values.isEmpty()) {
            return ZERO;
        }
        return values.stream()
                .map(progressGetter)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    public boolean isOverdue(TaskItem task, LocalDate today) {
        return task.getDueDate().isBefore(today) && task.getStatus() != WorkStatus.COMPLETED;
    }

    public BigDecimal normalizeProgress(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(ONE_HUNDRED) > 0) {
            throw new BusinessRuleException("Progress percent must be between 0 and 100.");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
