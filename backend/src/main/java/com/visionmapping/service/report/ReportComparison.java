package com.visionmapping.service.report;

import com.visionmapping.dto.response.ManagementReportResponse;
import com.visionmapping.dto.response.ManagementReportResponse.ChangeItem;
import com.visionmapping.dto.response.ManagementReportResponse.Comparison;
import com.visionmapping.dto.response.ManagementReportResponse.GoalChange;
import com.visionmapping.dto.response.ManagementReportResponse.GoalHealthRow;
import com.visionmapping.dto.response.ManagementReportResponse.Headline;
import com.visionmapping.dto.response.ManagementReportResponse.HeadlineChange;
import com.visionmapping.dto.response.ManagementReportResponse.RemovedGoal;
import com.visionmapping.entity.enums.Severity;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.service.report.ManagementReport.BlockerInfo;
import com.visionmapping.service.report.ManagementReport.GoalNode;
import com.visionmapping.service.report.ManagementReport.StepNode;
import com.visionmapping.service.report.ManagementReport.TaskNode;
import com.visionmapping.service.report.ReportRules.Health;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.lang.Nullable;

/**
 * V19 RM-FR-32 to RM-FR-37: what changed between an earlier final report and
 * this one. It compares two report models and their Summaries only; it never
 * queries the plan. Goals, steps, tasks and blockers are matched by id, so a
 * renamed item is still the same item (RM-FR-37).
 *
 * <p>What changed, most important first (RM-FR-35): goals whose health got
 * worse, due and target dates moved later, new Critical and High blockers,
 * blockers no longer open, goals whose health got better, tasks completed.
 * Health gets better in the order Off track, At risk, Done – open work, On
 * track, Done (the {@link Health} order).
 */
public final class ReportComparison {

    /** Change lines kept; the page shows 15 and the Changes sheet all of these. */
    static final int MAX_CHANGES = 500;

    /** The compared report's identity and contents. */
    public record Earlier(Long reportId, String code, String name, ManagementReport report, ManagementReportResponse summary) {
    }

    private ReportComparison() {
    }

    /**
     * @param removedReasons why goals of the earlier report left the scope, by goal id, when the caller knows
     */
    public static Comparison compare(Earlier earlier, ManagementReport report, ManagementReportResponse summary,
                                     Map<Long, String> removedReasons) {
        Map<Long, GoalHealthRow> before = byId(earlier.summary().goals(), GoalHealthRow::goalId);
        Map<Long, GoalHealthRow> now = byId(summary.goals(), GoalHealthRow::goalId);

        List<GoalChange> goals = new ArrayList<>();
        for (GoalHealthRow row : summary.goals()) {
            GoalHealthRow old = before.get(row.goalId());
            if (old == null) {
                goals.add(new GoalChange(row.goalId(), true, null, null, null, null));
                continue;
            }
            goals.add(new GoalChange(row.goalId(), false, old.health(), row.progress().subtract(old.progress()),
                    Objects.equals(old.targetDate(), row.targetDate()) ? null : old.targetDate(),
                    old.goalTitle().equals(row.goalTitle()) ? null : old.goalTitle()));
        }
        List<RemovedGoal> removed = earlier.summary().goals().stream()
                .filter(row -> !now.containsKey(row.goalId()))
                .map(row -> new RemovedGoal(row.goalId(), row.goalTitle(), removedReasons.get(row.goalId())))
                .toList();

        List<ChangeItem> changes = changes(earlier, report, summary, before);
        return new Comparison(earlier.reportId(), earlier.code(), earlier.name(), earlier.report().asOf(),
                headline(earlier.summary().headline(), summary.headline()), goals, removed,
                changes.stream().limit(MAX_CHANGES).toList(), changes.size());
    }

    private static HeadlineChange headline(Headline before, Headline now) {
        return new HeadlineChange(now.overallProgress().subtract(before.overallProgress()),
                now.totalTasks() - before.totalTasks(), now.completedTasks() - before.completedTasks(),
                now.overdueTasks() - before.overdueTasks(), now.blockedTasks() - before.blockedTasks(),
                now.openBlockers() - before.openBlockers(), now.dueSoonTasks() - before.dueSoonTasks());
    }

    private static List<ChangeItem> changes(Earlier earlier, ManagementReport report, ManagementReportResponse summary,
                                            Map<Long, GoalHealthRow> before) {
        List<ChangeItem> worse = new ArrayList<>();
        List<ChangeItem> better = new ArrayList<>();
        for (GoalHealthRow row : summary.goals()) {
            GoalHealthRow old = before.get(row.goalId());
            if (old == null) {
                continue;
            }
            int change = Health.valueOf(row.health()).compareTo(Health.valueOf(old.health()));
            ChangeItem item = new ChangeItem(change < 0 ? "GOAL_WORSE" : "GOAL_BETTER", "GOAL", row.goalId(), row.goalTitle(),
                    row.dreamTitle(), old.health(), row.health(), !old.goalTitle().equals(row.goalTitle()));
            if (change < 0) {
                worse.add(item);
            } else if (change > 0) {
                better.add(item);
            }
        }
        List<ChangeItem> all = new ArrayList<>(worse);
        all.addAll(datesMovedLater(earlier.report(), report));
        all.addAll(blockers(earlier.report(), report));
        all.addAll(better);
        all.addAll(tasksCompleted(earlier.report(), report));
        return all;
    }

    /** A dated item: its id, title, where it sits and its date. */
    private record Dated(String type, Long id, String title, String context, @Nullable LocalDate date) {
    }

    /** RM-FR-35: goals, then steps, then tasks whose date moved later; a date added or removed is not a move. */
    private static List<ChangeItem> datesMovedLater(ManagementReport earlier, ManagementReport report) {
        Map<String, Dated> before = byId(dated(earlier), dated -> dated.type() + dated.id());
        List<ChangeItem> items = new ArrayList<>();
        for (Dated now : dated(report)) {
            Dated old = before.get(now.type() + now.id());
            if (old != null && old.date() != null && now.date() != null && now.date().isAfter(old.date())) {
                items.add(new ChangeItem("DUE_LATER", now.type(), now.id(), now.title(), now.context(), old.date().toString(),
                        now.date().toString(), !old.title().equals(now.title())));
            }
        }
        return items;
    }

    private static List<Dated> dated(ManagementReport report) {
        List<Dated> goals = new ArrayList<>();
        List<Dated> steps = new ArrayList<>();
        List<Dated> tasks = new ArrayList<>();
        for (GoalNode goal : report.goals()) {
            goals.add(new Dated("GOAL", goal.goal().id(), goal.goal().title(), goal.dream().title(), goal.goal().targetDate()));
            for (StepNode step : goal.steps()) {
                steps.add(new Dated("STEP", step.step().id(), step.step().title(), goal.goal().title(), step.step().targetDate()));
                for (TaskNode task : step.tasks()) {
                    tasks.add(new Dated("TASK", task.task().id(), task.task().title(),
                            goal.goal().title() + " › " + step.step().title(), task.task().dueDate()));
                }
            }
        }
        List<Dated> all = new ArrayList<>(goals);
        all.addAll(steps);
        all.addAll(tasks);
        return all;
    }

    /** An open blocker and where it sits. */
    private record Located(BlockerInfo blocker, String where) {
    }

    /** RM-FR-35: new Critical and High blockers, then blockers open before and not open now. */
    private static List<ChangeItem> blockers(ManagementReport earlier, ManagementReport report) {
        Map<Long, Located> before = byId(located(earlier), located -> located.blocker().id());
        Map<Long, Located> now = byId(located(report), located -> located.blocker().id());
        List<ChangeItem> items = new ArrayList<>();
        now.values().stream()
                .filter(located -> !before.containsKey(located.blocker().id()))
                .filter(located -> located.blocker().severity() == Severity.CRITICAL || located.blocker().severity() == Severity.HIGH)
                .forEach(located -> items.add(new ChangeItem("NEW_BLOCKER", "OBSTACLE", located.blocker().id(), located.blocker().title(),
                        located.where(), null, located.blocker().severity().name(), false)));
        before.values().stream()
                .filter(located -> !now.containsKey(located.blocker().id()))
                .forEach(located -> items.add(new ChangeItem("BLOCKER_CLOSED", "OBSTACLE", located.blocker().id(),
                        located.blocker().title(), located.where(), null, null, false)));
        return items;
    }

    private static List<Located> located(ManagementReport report) {
        List<Located> all = new ArrayList<>();
        for (GoalNode goal : report.goals()) {
            for (StepNode step : goal.steps()) {
                String where = goal.goal().title() + " › " + step.step().title();
                step.blockers().forEach(blocker -> all.add(new Located(blocker, where)));
                for (TaskNode task : step.tasks()) {
                    task.blockers().forEach(blocker -> all.add(new Located(blocker, where + " › " + task.task().title())));
                }
            }
        }
        return all;
    }

    /** RM-FR-35: tasks Completed now that were not Completed (or not there) in the earlier report. */
    private static List<ChangeItem> tasksCompleted(ManagementReport earlier, ManagementReport report) {
        Map<Long, TaskNode> before = byId(earlier.tasks().toList(), task -> task.task().id());
        List<ChangeItem> items = new ArrayList<>();
        for (GoalNode goal : report.goals()) {
            for (StepNode step : goal.steps()) {
                for (TaskNode task : step.tasks()) {
                    TaskNode old = before.get(task.task().id());
                    boolean doneBefore = old != null && old.task().status() == WorkStatus.COMPLETED;
                    if (task.task().status() == WorkStatus.COMPLETED && !doneBefore) {
                        items.add(new ChangeItem("TASK_COMPLETED", "TASK", task.task().id(), task.task().title(),
                                goal.goal().title() + " › " + step.step().title(), null, null,
                                old != null && !old.task().title().equals(task.task().title())));
                    }
                }
            }
        }
        return items;
    }

    private static <T, K> Map<K, T> byId(List<T> values, Function<T, K> id) {
        return values.stream().collect(Collectors.toMap(id, Function.identity(), (first, second) -> first, LinkedHashMap::new));
    }
}
