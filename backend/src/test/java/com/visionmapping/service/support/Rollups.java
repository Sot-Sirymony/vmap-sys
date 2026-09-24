package com.visionmapping.service.support;

import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.repository.ProgressRollup;
import java.math.BigDecimal;
import java.util.List;

/**
 * Turns the children a test declares into the aggregate the roll-up queries
 * return, so a test can keep saying "the step has these two tasks" instead of
 * hand-computing a count, a sum and a completed tally.
 */
public final class Rollups {

    private Rollups() {
    }

    public static ProgressRollup ofTasks(List<TaskItem> tasks) {
        return new ProgressRollup(
                tasks.size(),
                tasks.stream().map(TaskItem::getProgressPercent).reduce(BigDecimal.ZERO, BigDecimal::add),
                tasks.stream().filter(task -> task.getStatus() == WorkStatus.COMPLETED).count());
    }

    public static ProgressRollup ofSteps(List<VisionStep> steps) {
        return new ProgressRollup(
                steps.size(),
                steps.stream().map(VisionStep::getProgressPercent).reduce(BigDecimal.ZERO, BigDecimal::add),
                steps.stream().filter(step -> step.getStatus() == WorkStatus.COMPLETED).count());
    }
}
