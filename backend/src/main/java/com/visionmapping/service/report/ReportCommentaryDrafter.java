package com.visionmapping.service.report;

import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.entity.enums.Severity;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.service.report.ManagementReport.BlockerInfo;
import com.visionmapping.service.report.ManagementReport.GoalNode;
import com.visionmapping.service.report.ManagementReport.StepNode;
import com.visionmapping.service.report.ManagementReport.TaskInfo;
import com.visionmapping.service.report.ManagementReport.TaskNode;
import com.visionmapping.service.report.ReportRules.Assessment;
import com.visionmapping.service.report.ReportRules.Health;
import com.visionmapping.dto.response.ManagementReportResponse.HealthReason;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * V19 RM-FR-28: a first draft of the four commentary sections, built from the
 * report with fixed rules (no AI). Each line starts with "- " so it reads as
 * a bullet. A section with nothing to say is empty.
 *
 * <ul>
 *   <li>Highlights: tasks completed in the period, by goal, goals that moved
 *       to On track since the compared report (phase 3), and goals done.</li>
 *   <li>Risks and issues: goals off track with their reasons, then open
 *       Critical and High blockers.</li>
 *   <li>Decisions and support: open Decision, Money and Partner blockers, then
 *       steps whose gap has no help planned (V17 BL-FR-22).</li>
 *   <li>Focus: the next action of each goal that is not done.</li>
 * </ul>
 */
public final class ReportCommentaryDrafter {

    /** The draft's four sections, each "" when there is nothing to say. */
    public record Draft(String highlights, String risks, String decisionsNeeded, String nextFocus) {
    }

    static final int MAX_LINES = 8;
    static final int MAX_CHARS = 2000;
    private static final int MAX_TITLES = 3;
    private static final Set<ObstacleType> SUPPORT_KINDS = EnumSet.of(ObstacleType.DECISION, ObstacleType.MONEY, ObstacleType.PARTNER);

    private ReportCommentaryDrafter() {
    }

    public static Draft draft(ManagementReport report, ReportText text) {
        return draft(report, text, null);
    }

    /** With a comparison, Highlights also names the goals that moved to On track since it. */
    public static Draft draft(ManagementReport report, ReportText text,
                              @org.springframework.lang.Nullable com.visionmapping.dto.response.ManagementReportResponse.Comparison comparison) {
        return new Draft(highlights(report, text, comparison), risks(report, text), support(report, text), focus(report, text));
    }

    private static String highlights(ManagementReport report, ReportText text,
                                     @org.springframework.lang.Nullable com.visionmapping.dto.response.ManagementReportResponse.Comparison comparison) {
        List<String> lines = new ArrayList<>();
        for (GoalNode goal : report.goals()) {
            List<String> done = goal.tasks().map(TaskNode::task)
                    .filter(task -> task.status() == WorkStatus.COMPLETED && report.period().contains(task.completedOn()))
                    .map(TaskInfo::title).toList();
            if (!done.isEmpty()) {
                String titles = String.join(", ", done.stream().limit(MAX_TITLES).toList()) + (done.size() > MAX_TITLES ? ", …" : "");
                lines.add(text.format(ReportText.Key.DRAFT_COMPLETED_TASKS, goal.goal().title(), done.size(), titles));
            }
        }
        if (comparison != null) {
            for (GoalNode goal : report.goals()) {
                var change = comparison.goal(goal.goal().id());
                if (health(goal, report) == Health.GREEN && change != null && change.healthBefore() != null
                        && Health.valueOf(change.healthBefore()).compareTo(Health.GREEN) < 0) {
                    lines.add(text.format(ReportText.Key.DRAFT_GOAL_ON_TRACK, goal.goal().title()));
                }
            }
        }
        for (GoalNode goal : report.goals()) {
            if (health(goal, report) == Health.DONE) {
                lines.add(text.format(ReportText.Key.DRAFT_GOAL_DONE, goal.goal().title()));
            }
        }
        return join(lines);
    }

    private static String risks(ManagementReport report, ReportText text) {
        List<String> lines = new ArrayList<>();
        for (GoalNode goal : report.goals()) {
            Assessment assessment = ReportRules.assessmentOf(goal, report.asOf());
            if (assessment.health() == Health.RED) {
                List<HealthReason> reasons = assessment.reasons().stream()
                        .map(reason -> new HealthReason(reason.code(), reason.value(), reason.elapsed(), reason.title())).toList();
                lines.add(text.format(ReportText.Key.DRAFT_RISK_GOAL, goal.goal().title(), text.reasons(reasons, "; ")));
            }
        }
        located(report).filter(entry -> entry.blocker().severity() == Severity.CRITICAL || entry.blocker().severity() == Severity.HIGH)
                .forEach(entry -> lines.add(text.format(ReportText.Key.DRAFT_RISK_BLOCKER, entry.blocker().title(),
                        text.kind(entry.blocker().type()), text.severity(entry.blocker().severity()), entry.where())));
        return join(lines);
    }

    private static String support(ManagementReport report, ReportText text) {
        List<String> lines = new ArrayList<>();
        located(report).filter(entry -> SUPPORT_KINDS.contains(entry.blocker().type()))
                .forEach(entry -> lines.add(text.format(ReportText.Key.DRAFT_SUPPORT_BLOCKER, entry.blocker().title(),
                        text.kind(entry.blocker().type()), entry.where())));
        for (GoalNode goal : report.goals()) {
            goal.steps().stream().filter(StepNode::gapNeedsHelp).forEach(step -> lines.add(text.format(ReportText.Key.DRAFT_SUPPORT_GAP,
                    step.step().title(), text.kind(step.step().gapKind()).toLowerCase(java.util.Locale.ROOT))));
        }
        return join(lines);
    }

    private static String focus(ManagementReport report, ReportText text) {
        List<String> lines = new ArrayList<>();
        for (GoalNode goal : report.goals()) {
            Health health = health(goal, report);
            String next = ReportRules.nextAction(goal);
            if (health != Health.DONE && next != null) {
                lines.add(text.format(ReportText.Key.DRAFT_FOCUS, goal.goal().title(), next));
            }
        }
        return join(lines);
    }

    /** An open blocker and "Goal › Step" (› Task) where it sits. */
    private record Located(BlockerInfo blocker, String where) {
    }

    private static Stream<Located> located(ManagementReport report) {
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
        return all.stream();
    }

    private static Health health(GoalNode goal, ManagementReport report) {
        return ReportRules.health(goal, report.asOf());
    }

    /** RM-FR-29: the longest fact sheet sent to Claude. */
    static final int MAX_FACTS = 12_000;

    /**
     * RM-FR-29: the facts the AI draft may read, and nothing else: the period,
     * each goal's title, progress, health and reasons, and the rule-based
     * draft (task and blocker titles, blocker kinds, next actions). These all
     * come from the report model, which holds only allow-listed fields
     * (D-19-2), so journals, conflict notes, criticism, partner contacts, AI
     * conversations and coach comments can never reach the request.
     */
    public static String facts(ManagementReport report, ReportText text,
                               @org.springframework.lang.Nullable com.visionmapping.dto.response.ManagementReportResponse.Comparison comparison) {
        StringBuilder facts = new StringBuilder("Report period: ").append(report.period().from()).append(" to ")
                .append(report.period().to()).append(", figures as of ").append(report.asOf()).append(".\nGoals:\n");
        if (report.goals().isEmpty()) {
            facts.append("- none in scope\n");
        }
        for (GoalNode goal : report.goals()) {
            Assessment assessment = ReportRules.assessmentOf(goal, report.asOf());
            facts.append("- ").append(goal.goal().title()).append(": ")
                    .append(goal.goal().progress().setScale(0, java.math.RoundingMode.HALF_UP).toPlainString()).append("% done, ")
                    .append(text.health(assessment.health()));
            if (!assessment.reasons().isEmpty()) {
                List<HealthReason> reasons = assessment.reasons().stream()
                        .map(reason -> new HealthReason(reason.code(), reason.value(), reason.elapsed(), reason.title())).toList();
                facts.append(" (").append(text.reasons(reasons, "; ")).append(')');
            }
            facts.append('\n');
        }
        Draft rules = draft(report, text, comparison);
        section(facts, "Highlights", rules.highlights());
        section(facts, "Risks and issues", rules.risks());
        section(facts, "Decisions and support needed", rules.decisionsNeeded());
        section(facts, "Focus for next period", rules.nextFocus());
        return facts.length() <= MAX_FACTS ? facts.toString() : facts.substring(0, MAX_FACTS);
    }

    private static void section(StringBuilder facts, String title, String lines) {
        facts.append("Rule-based notes, ").append(title).append(":\n").append(lines.isEmpty() ? "- nothing" : lines).append('\n');
    }

    /**
     * RM-FR-29: text Claude wrote for one section, held to the same shape as
     * the rule-based draft: non-blank lines, each starting with "- ", at most
     * eight, within 2,000 characters.
     */
    public static String bullets(@org.springframework.lang.Nullable String written) {
        if (written == null) {
            return "";
        }
        List<String> lines = written.lines().map(String::strip)
                .map(line -> line.replaceFirst("^([-*•]|\\d+[.)])\\s*", "").strip())
                .filter(line -> !line.isEmpty())
                .map(line -> "- " + line).toList();
        return join(lines);
    }

    /** At most eight lines, cut to fit the 2,000-character section (RM-FR-26). */
    private static String join(List<String> lines) {
        String joined = String.join("\n", lines.stream().limit(MAX_LINES).toList());
        return joined.length() <= MAX_CHARS ? joined : joined.substring(0, MAX_CHARS - 1) + "…";
    }
}
