package com.visionmapping.service.report;

import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.service.report.ManagementReport.BlockerInfo;
import com.visionmapping.service.report.ManagementReport.GoalInfo;
import com.visionmapping.service.report.ManagementReport.GoalNode;
import com.visionmapping.service.report.ManagementReport.StepInfo;
import com.visionmapping.service.report.ManagementReport.StepNode;
import com.visionmapping.service.report.ManagementReport.TaskInfo;
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
     * drive the Excel colours (MR-FR-19). V19 RM-FR-13: the ids let the
     * Details tab link to the item; {@code dueSoon} feeds its quick filter.
     */
    public record Row(Long goalId, @Nullable Long stepId, @Nullable Long taskId, String dream, String goal, boolean firstOfGoal,
                      String step, String task, List<String> issues, BigDecimal progress, @Nullable Priority priority,
                      @Nullable LocalDate due, String nextAction, boolean completed, boolean blocked, boolean overdue,
                      boolean dueSoon) {

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
            GoalInfo goal = node.goal();
            String dream = node.dream().title();
            boolean first = true;
            if (node.steps().isEmpty()) {
                List<String> issues = new ArrayList<>();
                overdue(goal.targetDate(), goal.status(), today, text).ifPresent(issues::add);
                rows.add(new Row(goal.id(), null, null, dream, goal.title(), true, "", "", issues, percent(goal.progress()),
                        goal.priority(), goal.targetDate(), "", goal.status() == WorkStatus.COMPLETED, false,
                        ReportRules.isOverdue(goal, today), dueSoon(goal.targetDate(), goal.status(), today)));
                continue;
            }
            for (StepNode stepNode : node.steps()) {
                StepInfo step = stepNode.step();
                if (stepNode.tasks().isEmpty()) {
                    List<String> issues = new ArrayList<>(stepNode.blockers().stream().map(obstacle -> describe(obstacle, text)).toList());
                    overdue(step.targetDate(), step.status(), today, text).ifPresent(issues::add);
                    rows.add(new Row(goal.id(), step.id(), null, dream, goal.title(), first, step.title(), "", issues,
                            percent(step.progress()), step.priority(), step.targetDate(), "", step.status() == WorkStatus.COMPLETED,
                            step.status() == WorkStatus.BLOCKED || !stepNode.blockers().isEmpty(), ReportRules.isOverdue(step, today),
                            dueSoon(step.targetDate(), step.status(), today)));
                    first = false;
                    continue;
                }
                for (TaskNode taskNode : stepNode.tasks()) {
                    TaskInfo task = taskNode.task();
                    rows.add(new Row(goal.id(), step.id(), task.id(), dream, goal.title(), first, step.title(), task.title(),
                            issues(taskNode, stepNode, today, text), percent(task.progress()), task.priority(),
                            task.dueDate(), nextAction(taskNode, stepNode, text), task.status() == WorkStatus.COMPLETED,
                            task.status() == WorkStatus.BLOCKED || !taskNode.blockers().isEmpty() || !stepNode.blockers().isEmpty(),
                            ReportRules.isOverdue(task, today), ReportRules.isDueSoon(task, today)));
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
        TaskInfo task = taskNode.task();
        List<String> issues = new ArrayList<>();
        if (task.status() == WorkStatus.BLOCKED && !ReportRules.blank(task.blockerReason())) {
            issues.add(text.format(Key.ISSUE_BLOCKED, task.blockerReason().strip()));
        }
        Stream.concat(taskNode.blockers().stream(), stepNode.blockers().stream()).map(obstacle -> describe(obstacle, text)).forEach(issues::add);
        overdue(task.dueDate(), task.status(), today, text).ifPresent(issues::add);
        if (ReportRules.blank(task.owner())) {
            issues.add(text.get(Key.ISSUE_NO_OWNER));
        }
        if (task.dueDate() == null) {
            issues.add(text.get(Key.ISSUE_NO_DUE_DATE));
        }
        return issues;
    }

    /**
     * The task's own Next Action; when empty, the next task in the same step
     * that is not Completed (other than this one), marked "(next task)".
     */
    static String nextAction(TaskNode taskNode, StepNode stepNode, ReportText text) {
        TaskInfo task = taskNode.task();
        if (!ReportRules.blank(task.nextAction())) {
            return task.nextAction().strip();
        }
        return stepNode.tasks().stream()
                .map(TaskNode::task)
                .filter(other -> !other.id().equals(task.id()) && other.status() != WorkStatus.COMPLETED)
                .findFirst()
                .map(other -> text.format(Key.NEXT_TASK_MARK, other.title()))
                .orElse("");
    }

    /** "No budget (Money, Critical)": title, kind and severity only (MR-NFR-02). */
    public static String describe(BlockerInfo obstacle, ReportText text) {
        return obstacle.title() + " (" + text.kind(obstacle.type()) + ", " + text.severity(obstacle.severity()) + ")";
    }

    private static java.util.Optional<String> overdue(@Nullable LocalDate due, WorkStatus status, LocalDate today, ReportText text) {
        if (due == null || status == WorkStatus.COMPLETED || !due.isBefore(today)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(text.format(Key.ISSUE_OVERDUE_BY, ChronoUnit.DAYS.between(due, today)));
    }

    private static boolean dueSoon(@Nullable LocalDate due, WorkStatus status, LocalDate today) {
        return status != WorkStatus.COMPLETED && due != null && !due.isBefore(today)
                && !due.isAfter(today.plusDays(ReportRules.DUE_SOON_DAYS));
    }

    private static BigDecimal percent(@Nullable BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
