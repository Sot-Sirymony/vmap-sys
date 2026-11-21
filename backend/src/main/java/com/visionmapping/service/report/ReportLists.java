package com.visionmapping.service.report;

import com.visionmapping.dto.response.ManagementReportResponse;
import com.visionmapping.dto.response.ManagementReportResponse.GoalHealthRow;
import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.entity.enums.Severity;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.service.report.ManagementReport.BlockerInfo;
import com.visionmapping.service.report.ManagementReport.GoalNode;
import com.visionmapping.service.report.ManagementReport.StepNode;
import com.visionmapping.service.report.ManagementReport.TaskInfo;
import com.visionmapping.service.report.ManagementReport.TaskNode;
import com.visionmapping.service.report.ReportText.Key;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.lang.Nullable;

/**
 * V18 MR-FR-24, V19 RM-FR-42: the report's lists, built once by fixed rules
 * and laid out by the deck and the PDF alike, so both say the same thing.
 * Each item is a heading and a detail line.
 */
final class ReportLists {

    /** One list entry: a bold heading and a muted detail line. */
    record Item(String heading, String detail) {
    }

    private static final Set<ObstacleType> SUPPORT_KINDS = EnumSet.of(ObstacleType.DECISION, ObstacleType.MONEY, ObstacleType.PARTNER);

    private ReportLists() {
    }

    /** MR-FR-24 (5): open blockers most severe first, then Blocked tasks; where, why and who could help. */
    static List<Item> blockers(ManagementReport report, ReportText text) {
        record Located(BlockerInfo obstacle, String where) {
        }
        List<Located> obstacles = new ArrayList<>();
        List<Item> blockedTasks = new ArrayList<>();
        for (GoalNode goal : report.goals()) {
            for (StepNode step : goal.steps()) {
                String where = goal.goal().title() + " › " + step.step().title();
                step.blockers().forEach(obstacle -> obstacles.add(new Located(obstacle, where)));
                for (TaskNode task : step.tasks()) {
                    String taskWhere = where + " › " + task.task().title();
                    task.blockers().forEach(obstacle -> obstacles.add(new Located(obstacle, taskWhere)));
                    if (task.task().status() == WorkStatus.BLOCKED) {
                        String why = ReportRules.blank(task.task().blockerReason()) ? "" : task.task().blockerReason().strip();
                        blockedTasks.add(new Item(text.get(Key.BLOCKED_TASK) + ": " + task.task().title(),
                                where + (why.isEmpty() ? "" : " · " + text.get(Key.WHY) + ": " + why)));
                    }
                }
            }
        }
        obstacles.sort(Comparator.comparingInt((Located located) -> severityRank(located.obstacle().severity())));
        List<Item> items = new ArrayList<>();
        for (Located located : obstacles) {
            BlockerInfo obstacle = located.obstacle();
            String helper = text.helper(obstacle.type());
            items.add(new Item(obstacle.title(), located.where() + " · " + text.kind(obstacle.type()) + ", "
                    + text.severity(obstacle.severity()) + (helper.isEmpty() ? "" : " · " + text.get(Key.SUGGESTED_HELPER) + ": " + helper)));
        }
        items.addAll(blockedTasks);
        return items;
    }

    /** MR-FR-24 (6): overdue tasks, most overdue first, with where, due date, days late and owner. */
    static List<Item> overdue(ManagementReport report, ReportText text) {
        LocalDate today = report.asOf();
        record Late(TaskInfo task, Item item) {
        }
        List<Late> late = new ArrayList<>();
        for (GoalNode goal : report.goals()) {
            for (StepNode step : goal.steps()) {
                for (TaskNode task : step.tasks()) {
                    TaskInfo item = task.task();
                    if (ReportRules.isOverdue(item, today)) {
                        late.add(new Late(item, new Item(item.title(), goal.goal().title() + " › " + step.step().title() + " · "
                                + text.format(Key.DUE, text.date(item.dueDate())) + " ("
                                + text.format(Key.DAYS_OVERDUE, ChronoUnit.DAYS.between(item.dueDate(), today)) + ") · "
                                + text.get(Key.OWNER) + ": " + item.owner())));
                    }
                }
            }
        }
        late.sort(Comparator.comparing((Late entry) -> entry.task().dueDate()));
        return late.stream().map(Late::item).toList();
    }

    /** MR-FR-24 (7): the first open task of each goal not done, in the goal table's order. */
    static List<Item> nextActions(ManagementReport report, ManagementReportResponse summary, ReportText text) {
        List<Item> items = new ArrayList<>();
        for (GoalHealthRow row : summary.goals()) {
            if ("DONE".equals(row.health())) {
                continue;
            }
            report.goals().stream().filter(node -> node.goal().id().equals(row.goalId())).findFirst()
                    .flatMap(node -> node.tasks().map(TaskNode::task).filter(task -> task.status() != WorkStatus.COMPLETED).findFirst())
                    .ifPresent(task -> items.add(new Item(row.goalTitle(),
                            (ReportRules.blank(task.nextAction()) ? task.title() : task.nextAction().strip()) + " · "
                                    + text.get(Key.OWNER) + ": " + task.owner()
                                    + (task.dueDate() == null ? "" : " · " + text.format(Key.DUE, text.date(task.dueDate()))))));
        }
        return items;
    }

    /** MR-FR-24 (8): blockers about a decision, money or a partner, then steps whose gap needs help (V17 BL-FR-22). */
    static List<Item> support(ManagementReport report, ReportText text) {
        List<Item> items = new ArrayList<>();
        for (GoalNode goal : report.goals()) {
            for (StepNode step : goal.steps()) {
                String where = goal.goal().title() + " › " + step.step().title();
                List<BlockerInfo> blockers = new ArrayList<>(step.blockers());
                step.tasks().forEach(task -> blockers.addAll(task.blockers()));
                for (BlockerInfo obstacle : blockers) {
                    if (SUPPORT_KINDS.contains(obstacle.type())) {
                        String helper = text.helper(obstacle.type());
                        items.add(new Item(obstacle.title(), where + " · " + text.kind(obstacle.type())
                                + (helper.isEmpty() ? "" : " · " + text.get(Key.SUGGESTED_HELPER) + ": " + helper)));
                    }
                }
            }
        }
        for (GoalNode goal : report.goals()) {
            goal.steps().stream().filter(StepNode::gapNeedsHelp).forEach(step -> items.add(new Item(step.step().title(),
                    goal.goal().title() + " · " + text.format(Key.GAP_NEEDS_HELP, text.kind(step.step().gapKind()).toLowerCase())
                            + " · " + text.get(Key.SUGGESTED_HELPER) + ": " + text.helper(step.step().gapKind()))));
        }
        return items;
    }

    /** RM-FR-35, RM-FR-41: each What changed line as an item: title (renamed), kind, before → after, where. */
    static List<Item> changes(ManagementReportResponse.Comparison comparison, ReportText text) {
        List<Item> items = new ArrayList<>();
        for (ManagementReportResponse.ChangeItem change : comparison.changes()) {
            String before = text.changeValue(change.kind(), change.before());
            String after = text.changeValue(change.kind(), change.after());
            String values = before.isEmpty() && after.isEmpty() ? "" : " · " + (before.isEmpty() ? after : before + " → " + after);
            items.add(new Item(change.title() + (change.renamed() ? " " + text.get(Key.RENAMED) : ""),
                    text.changeKind(change.kind()) + values + (ReportRules.blank(change.context()) ? "" : " · " + change.context())));
        }
        return items;
    }

    /** The participant's text as lines, with "- " bullets taken off and blank lines left out. */
    static List<String> lines(String body) {
        return body.lines().map(String::strip).filter(line -> !line.isEmpty())
                .map(line -> line.startsWith("- ") ? line.substring(2) : line).toList();
    }

    static int severityRank(@Nullable Severity severity) {
        return severity == null ? Severity.values().length : Severity.values().length - 1 - severity.ordinal();
    }
}
