package com.visionmapping.service.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.visionmapping.dto.response.ManagementReportResponse.HealthReason;
import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.Severity;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.service.report.ManagementReport.BlockerInfo;
import com.visionmapping.service.report.ManagementReport.DreamInfo;
import com.visionmapping.service.report.ManagementReport.GoalInfo;
import com.visionmapping.service.report.ManagementReport.GoalNode;
import com.visionmapping.service.report.ManagementReport.StepInfo;
import com.visionmapping.service.report.ManagementReport.StepNode;
import com.visionmapping.service.report.ManagementReport.TaskInfo;
import com.visionmapping.service.report.ManagementReport.TaskNode;
import com.visionmapping.service.report.ReportRules.Assessment;
import com.visionmapping.service.report.ReportRules.Health;
import com.visionmapping.service.report.ReportRules.Reason;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** V19 RM-FR-01, RM-FR-02: goal health comes with the rules that set it. */
class ReportRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

    @Test
    void aCompletedGoalIsDoneUnlessTasksAreStillOpen() {
        Assessment done = ReportRules.assess(goal(WorkStatus.COMPLETED, 100, TODAY.plusDays(10), task(WorkStatus.COMPLETED, null)), TODAY);
        assertThat(done.health()).isEqualTo(Health.DONE);
        assertThat(done.reasons()).extracting(Reason::code).containsExactly("COMPLETED");

        Assessment open = ReportRules.assess(goal(WorkStatus.COMPLETED, 100, TODAY.plusDays(10),
                task(WorkStatus.COMPLETED, null), task(WorkStatus.NOT_STARTED, null), task(WorkStatus.IN_PROGRESS, null)), TODAY);
        assertThat(open.health()).isEqualTo(Health.DONE_OPEN_WORK);
        assertThat(open.reasons()).containsExactly(new Reason("COMPLETED_OPEN_WORK", 2L, null, null));
    }

    @Test
    void redListsEveryRedRuleAndNotTheAmberOnes() {
        TaskNode blocked = task(WorkStatus.BLOCKED, null);
        TaskNode late = task(WorkStatus.IN_PROGRESS, TODAY.minusDays(1));
        GoalNode node = new GoalNode(goalInfo(WorkStatus.IN_PROGRESS, 10, TODAY.minusDays(3)), new DreamInfo(1L, "Dream"),
                List.of(new StepNode(stepInfo(), List.of(blocked, late),
                        List.of(blocker("No budget", Severity.CRITICAL), blocker("No room", Severity.CRITICAL), blocker("Minor", Severity.LOW)),
                        false)), null);

        Assessment assessment = ReportRules.assess(node, TODAY);

        assertThat(assessment.health()).isEqualTo(Health.RED);
        assertThat(assessment.reasons()).containsExactly(
                new Reason("GOAL_OVERDUE", 3L, null, null),
                new Reason("BLOCKED_TASKS", 1L, null, null),
                new Reason("CRITICAL_BLOCKER", 2L, null, "No budget"));
    }

    @Test
    void amberNamesOverdueWorkOpenBlockersAndASlippingSchedule() {
        // Created 80 days ago and due in 20: 80% of the time gone at 10% progress.
        GoalInfo goal = new GoalInfo(1L, "G-001", "Goal", WorkStatus.IN_PROGRESS, Priority.HIGH, BigDecimal.TEN,
                TODAY.plusDays(20), TODAY.minusDays(80));
        StepInfo lateStep = new StepInfo(2L, "Step", WorkStatus.NOT_STARTED, Priority.HIGH, BigDecimal.ZERO, TODAY.minusDays(2), null);
        GoalNode node = new GoalNode(goal, new DreamInfo(1L, "Dream"), List.of(new StepNode(lateStep,
                List.of(task(WorkStatus.IN_PROGRESS, TODAY.minusDays(1))), List.of(blocker("Unsure", Severity.LOW)), false)), null);

        Assessment assessment = ReportRules.assess(node, TODAY);

        assertThat(assessment.health()).isEqualTo(Health.AMBER);
        assertThat(assessment.reasons()).containsExactly(
                new Reason("OVERDUE_WORK", 2L, null, null),
                new Reason("OPEN_BLOCKERS", 1L, null, null),
                new Reason("BEHIND_SCHEDULE", 10L, 80L, null));
    }

    @Test
    void greenSaysNothingIsWrongAndUnknownDatesNeverCountAsBehind() {
        GoalInfo noDates = new GoalInfo(1L, "G-001", "Goal", WorkStatus.IN_PROGRESS, null, BigDecimal.ZERO, null, null);
        Assessment assessment = ReportRules.assess(new GoalNode(noDates, new DreamInfo(1L, "Dream"), List.of(), null), TODAY);

        assertThat(assessment.health()).isEqualTo(Health.GREEN);
        assertThat(assessment.reasons()).extracting(Reason::code).containsExactly("ON_TRACK");
        // A target date on the day it was created has no time span to measure.
        GoalInfo sameDay = new GoalInfo(1L, "G-001", "Goal", WorkStatus.IN_PROGRESS, null, BigDecimal.ZERO, TODAY, TODAY);
        assertThat(ReportRules.health(new GoalNode(sameDay, new DreamInfo(1L, "Dream"), List.of(), null), TODAY)).isEqualTo(Health.GREEN);
    }

    @Test
    void everyReasonReadsAsASentenceInEnglishAndKhmer() {
        ReportText english = ReportText.of("en");
        assertThat(english.reason(new HealthReason("COMPLETED", null, null, null))).isEqualTo("Completed");
        assertThat(english.reason(new HealthReason("COMPLETED_OPEN_WORK", 2L, null, null))).isEqualTo("Completed, but 2 task(s) are still open");
        assertThat(english.reason(new HealthReason("GOAL_OVERDUE", 3L, null, null))).isEqualTo("Target date passed 3 day(s) ago");
        assertThat(english.reason(new HealthReason("BLOCKED_TASKS", 1L, null, null))).isEqualTo("1 blocked task(s)");
        assertThat(english.reason(new HealthReason("CRITICAL_BLOCKER", 1L, null, "No budget"))).isEqualTo("Critical blocker: No budget");
        assertThat(english.reason(new HealthReason("CRITICAL_BLOCKER", 3L, null, "No budget"))).isEqualTo("Critical blocker: No budget and 2 more");
        assertThat(english.reason(new HealthReason("OVERDUE_WORK", 2L, null, null))).isEqualTo("2 overdue task(s) or step(s)");
        assertThat(english.reason(new HealthReason("OPEN_BLOCKERS", 1L, null, null))).isEqualTo("1 open blocker(s)");
        assertThat(english.reason(new HealthReason("BEHIND_SCHEDULE", 25L, 70L, null))).isEqualTo("Progress 25% with 70% of the time gone");
        assertThat(english.reason(new HealthReason("ON_TRACK", null, null, null))).isEqualTo("No overdue work or open blockers");
        assertThat(english.reason(new HealthReason("SOMETHING_NEW", null, null, null))).isEqualTo("SOMETHING_NEW");
        assertThat(english.reasons(List.of(new HealthReason("COMPLETED", null, null, null),
                new HealthReason("ON_TRACK", null, null, null)), "; ")).isEqualTo("Completed; No overdue work or open blockers");
        assertThat(english.health(Health.DONE_OPEN_WORK)).isEqualTo("Done – open work");

        ReportText khmer = ReportText.of("km");
        assertThat(khmer.reason(new HealthReason("GOAL_OVERDUE", 3L, null, null))).contains("3");
        assertThat(khmer.health(Health.DONE_OPEN_WORK)).isNotEqualTo("Done – open work");
    }

    @Test
    void periodLabelsNameThePresetOrTheDates() {
        ReportText text = ReportText.of("en");
        assertThat(text.periodLabel(ReportPeriod.resolve("THIS_WEEK", null, null, TODAY))).isEqualTo("This week");
        assertThat(text.periodLabel(ReportPeriod.resolve("LAST_WEEK", null, null, TODAY))).isEqualTo("Last week");
        assertThat(text.periodLabel(ReportPeriod.resolve("THIS_MONTH", null, null, TODAY))).isEqualTo("This month");
        assertThat(text.periodLabel(ReportPeriod.resolve("LAST_MONTH", null, null, TODAY))).isEqualTo("Last month");
        assertThat(text.periodLabel(ReportPeriod.resolve("THIS_QUARTER", null, null, TODAY))).isEqualTo("This quarter");
        assertThat(text.periodLabel(ReportPeriod.resolve("LAST_QUARTER", null, null, TODAY))).isEqualTo("Last quarter");
        assertThat(text.periodLabel(ReportPeriod.resolve(null, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10), TODAY)))
                .isEqualTo("1 Nov 2026 to 10 Nov 2026");
    }

    private static GoalNode goal(WorkStatus status, int progress, LocalDate target, TaskNode... tasks) {
        return new GoalNode(goalInfo(status, progress, target), new DreamInfo(1L, "Dream"),
                List.of(new StepNode(stepInfo(), List.of(tasks), List.of(), false)), null);
    }

    private static GoalInfo goalInfo(WorkStatus status, int progress, LocalDate target) {
        return new GoalInfo(1L, "G-001", "Goal", status, Priority.HIGH, BigDecimal.valueOf(progress), target, TODAY.minusDays(1));
    }

    private static StepInfo stepInfo() {
        return new StepInfo(2L, "Step", WorkStatus.IN_PROGRESS, Priority.HIGH, BigDecimal.ZERO, TODAY.plusDays(30), null);
    }

    private static final AtomicLong IDS = new AtomicLong(100);

    private static TaskNode task(WorkStatus status, LocalDate due) {
        return new TaskNode(new TaskInfo(IDS.incrementAndGet(), "Task", status, Priority.MEDIUM, BigDecimal.ZERO, due, "Me", null,
                status == WorkStatus.BLOCKED ? "Waiting" : null, TODAY, null), List.of());
    }

    private static BlockerInfo blocker(String title, Severity severity) {
        return new BlockerInfo(IDS.incrementAndGet(), title, ObstacleType.MONEY, severity, ObstacleStatus.OPEN, null, TODAY, "");
    }
}
