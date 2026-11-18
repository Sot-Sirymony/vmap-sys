package com.visionmapping.service.report;

import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.service.report.ManagementReport.GoalNode;
import com.visionmapping.service.report.ManagementReport.StepNode;
import com.visionmapping.service.report.ManagementReport.TaskInfo;
import com.visionmapping.service.report.ManagementReport.TaskNode;
import com.visionmapping.service.report.ReportRules.Health;
import com.visionmapping.util.Copies;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.lang.Nullable;

/**
 * V19 RM-FR-10, RM-FR-11: the optional filters below the scope. Empty sets and
 * a blank owner mean "any". A goal's health is a property of the whole goal,
 * so it is judged before tasks are narrowed and never changes with the filter.
 */
public record ReportFilters(Set<Priority> priorities, @Nullable String owner, Set<Health> healths, boolean includeCompleted) {

    public static final ReportFilters NONE = new ReportFilters(Set.of(), null, Set.of(), true);

    public ReportFilters {
        priorities = Copies.set(priorities);
        healths = Copies.set(healths);
        owner = owner == null || owner.isBlank() ? null : owner.strip();
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isEmpty() {
        return priorities.isEmpty() && owner == null && healths.isEmpty() && includeCompleted;
    }

    private boolean narrowsTasks() {
        return !priorities.isEmpty() || owner != null;
    }

    /**
     * RM-FR-11: keeps a goal when it passes the completed and health filters
     * and has at least one task matching the priority and owner filters, or has
     * no tasks and the priority filter takes its own priority (with no owner
     * filter, since a goal has no owner). Steps and tasks are narrowed the same
     * way. Each goal keeps the health assessed on all its work.
     */
    public List<GoalNode> apply(List<GoalNode> goals) {
        if (isEmpty()) {
            return goals;
        }
        List<GoalNode> kept = new ArrayList<>();
        for (GoalNode goal : goals) {
            if (!includeCompleted && goal.goal().status() == WorkStatus.COMPLETED) {
                continue;
            }
            if (!healths.isEmpty() && (goal.assessment() == null || !healths.contains(goal.assessment().health()))) {
                continue;
            }
            if (!narrowsTasks()) {
                kept.add(goal);
                continue;
            }
            List<StepNode> steps = new ArrayList<>();
            for (StepNode step : goal.steps()) {
                List<TaskNode> tasks = step.tasks().stream().filter(task -> matches(task.task())).toList();
                boolean emptyStepMatches = step.tasks().isEmpty() && owner == null && takes(step.step().priority());
                if (!tasks.isEmpty() || emptyStepMatches) {
                    steps.add(new StepNode(step.step(), tasks, step.blockers(), step.gapNeedsHelp()));
                }
            }
            boolean emptyGoalMatches = goal.steps().isEmpty() && owner == null && takes(goal.goal().priority());
            if (!steps.isEmpty() || emptyGoalMatches) {
                kept.add(new GoalNode(goal.goal(), goal.dream(), steps, goal.assessment()));
            }
        }
        return kept;
    }

    private boolean matches(TaskInfo task) {
        return takes(task.priority()) && (owner == null
                || (task.owner() != null && task.owner().strip().toLowerCase(Locale.ROOT).equals(owner.toLowerCase(Locale.ROOT))));
    }

    private boolean takes(@Nullable Priority priority) {
        return priorities.isEmpty() || (priority != null && priorities.contains(priority));
    }
}
