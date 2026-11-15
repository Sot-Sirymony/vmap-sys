package com.visionmapping.service.report;

import com.visionmapping.entity.Goal;
import com.visionmapping.entity.Obstacle;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.service.report.ManagementReport.GoalNode;
import com.visionmapping.service.report.ManagementReport.StepNode;
import com.visionmapping.service.report.ManagementReport.TaskNode;
import com.visionmapping.service.report.ReportText.Key;
import com.visionmapping.util.Copies;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.lang.Nullable;

/**
 * V18 MR-FR-17, MR-FR-18: the report's rows, shared by the Excel report and
 * the deck. One row per task, under its goal and step. A goal without steps,
 * or a step without tasks, still gets one row, so nothing in the plan is hidden.
 */
public final class ReportRows {

    /**
     * One row. Progress, priority and due date are the task's, else the step's,
     * else the goal's. {@code completed}, {@code blocked} and {@code overdue}
     * drive the Excel colours (MR-FR-19).
     */
    public record Row(String dream, String goal, boolean firstOfGoal, String step, String task, List<String> issues,
                      BigDecimal progress, @Nullable Priority priority, @Nullable LocalDate due, String nextAction,
                      boolean completed, boolean blocked, boolean overdue) {

        public Row {
            issues = Copies.list(issues);
        }
    }

    private ReportRows() {
    }

    public static List<Row> build(ManagementReport report, ReportText text) {
        LocalDate today = report.asOf();
        List<Row> rows = new ArrayList<>();
        for (GoalNode node : report.goals()) {
            Goal goal = node.goal();
            String dream = node.dream().getTitle();
            boolean first = true;
            if (node.steps().isEmpty()) {
                List<String> issues = new ArrayList<>();
                overdue(goal.getTargetDate(), goal.getStatus(), today, text).ifPresent(issues::add);
                rows.add(new Row(dream, goal.getTitle(), true, "", "", issues, percent(goal.getProgressPercent()), goal.getPriority(),
                        goal.getTargetDate(), "", goal.getStatus() == WorkStatus.COMPLETED, false,
                        ReportRules.isOverdue(goal, today)));
                continue;
            }
            for (StepNode stepNode : node.steps()) {
                VisionStep step = stepNode.step();
                if (stepNode.tasks().isEmpty()) {
                    List<String> issues = new ArrayList<>(stepNode.blockers().stream().map(obstacle -> describe(obstacle, text)).toList());
                    overdue(step.getTargetDate(), step.getStatus(), today, text).ifPresent(issues::add);
                    rows.add(new Row(dream, goal.getTitle(), first, step.getTitle(), "", issues, percent(step.getProgressPercent()),
                            step.getPriority(), step.getTargetDate(), "", step.getStatus() == WorkStatus.COMPLETED,
                            step.getStatus() == WorkStatus.BLOCKED || !stepNode.blockers().isEmpty(), ReportRules.isOverdue(step, today)));
                    first = false;
                    continue;
                }
                for (TaskNode taskNode : stepNode.tasks()) {
                    TaskItem task = taskNode.task();
                    rows.add(new Row(dream, goal.getTitle(), first, step.getTitle(), task.getTitle(),
                            issues(taskNode, stepNode, today, text), percent(task.getProgressPercent()), task.getPriority(),
                            task.getDueDate(), nextAction(taskNode, stepNode, text), task.getStatus() == WorkStatus.COMPLETED,
                            task.getStatus() == WorkStatus.BLOCKED || !taskNode.blockers().isEmpty() || !stepNode.blockers().isEmpty(),
                            ReportRules.isOverdue(task, today)));
                    first = false;
                }
            }
        }
        return rows;
    }

    /**
     * "Management issues" (BRD 18 Definitions): the blocker reason, open
     * blockers on the task or its step, how overdue it is, and a missing owner
     * or due date. One per line in the cell.
     */
    static List<String> issues(TaskNode taskNode, StepNode stepNode, LocalDate today, ReportText text) {
        TaskItem task = taskNode.task();
        List<String> issues = new ArrayList<>();
        if (task.getStatus() == WorkStatus.BLOCKED && !ReportRules.blank(task.getBlockerReason())) {
            issues.add(text.format(Key.ISSUE_BLOCKED, task.getBlockerReason().strip()));
        }
        Stream.concat(taskNode.blockers().stream(), stepNode.blockers().stream()).map(obstacle -> describe(obstacle, text)).forEach(issues::add);
        overdue(task.getDueDate(), task.getStatus(), today, text).ifPresent(issues::add);
        if (ReportRules.blank(task.getOwner())) {
            issues.add(text.get(Key.ISSUE_NO_OWNER));
        }
        if (task.getDueDate() == null) {
            issues.add(text.get(Key.ISSUE_NO_DUE_DATE));
        }
        return issues;
    }

    /**
     * The task's own Next Action; when empty, the next task in the same step
     * that is not Completed (other than this one), marked "(next task)".
     */
    static String nextAction(TaskNode taskNode, StepNode stepNode, ReportText text) {
        TaskItem task = taskNode.task();
        if (!ReportRules.blank(task.getNextAction())) {
            return task.getNextAction().strip();
        }
        return stepNode.tasks().stream()
                .map(TaskNode::task)
                .filter(other -> !other.getId().equals(task.getId()) && other.getStatus() != WorkStatus.COMPLETED)
                .findFirst()
                .map(other -> text.format(Key.NEXT_TASK_MARK, other.getTitle()))
                .orElse("");
    }

    /** "No budget (Money, Critical)": title, kind and severity only (MR-NFR-02). */
    public static String describe(Obstacle obstacle, ReportText text) {
        return obstacle.getTitle() + " (" + text.kind(obstacle.getObstacleType()) + ", " + text.severity(obstacle.getSeverity()) + ")";
    }

    private static java.util.Optional<String> overdue(@Nullable LocalDate due, WorkStatus status, LocalDate today, ReportText text) {
        if (due == null || status == WorkStatus.COMPLETED || !due.isBefore(today)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(text.format(Key.ISSUE_OVERDUE_BY, ChronoUnit.DAYS.between(due, today)));
    }

    private static BigDecimal percent(@Nullable BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
