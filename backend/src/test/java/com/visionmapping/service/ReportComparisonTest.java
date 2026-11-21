package com.visionmapping.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.visionmapping.dto.response.ManagementReportResponse;
import com.visionmapping.dto.response.ManagementReportResponse.ChangeItem;
import com.visionmapping.dto.response.ManagementReportResponse.Comparison;
import com.visionmapping.dto.response.ManagementReportResponse.GoalChange;
import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.Severity;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.service.report.ManagementReport;
import com.visionmapping.service.report.ManagementReport.BlockerInfo;
import com.visionmapping.service.report.ManagementReport.DreamInfo;
import com.visionmapping.service.report.ManagementReport.GoalInfo;
import com.visionmapping.service.report.ManagementReport.GoalNode;
import com.visionmapping.service.report.ManagementReport.PeriodFigures;
import com.visionmapping.service.report.ManagementReport.StepInfo;
import com.visionmapping.service.report.ManagementReport.StepNode;
import com.visionmapping.service.report.ManagementReport.TaskInfo;
import com.visionmapping.service.report.ManagementReport.TaskNode;
import com.visionmapping.service.report.ReportCommentaryDrafter;
import com.visionmapping.service.report.ReportComparison;
import com.visionmapping.service.report.ReportFilters;
import com.visionmapping.service.report.ReportPeriod;
import com.visionmapping.service.report.ReportText;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** V19 RM-FR-32 to RM-FR-37: what changed between two reports, matched by id. */
class ReportComparisonTest {

    private static final LocalDate NOVEMBER = LocalDate.of(2026, 11, 30);
    private static final LocalDate DECEMBER = LocalDate.of(2026, 12, 31);
    // toResponse uses none of the service's collaborators.
    private final ManagementReportService service = new ManagementReportService(null, null, null, null, null, null, null, null, null, null);

    /** The target experience: November, then December. */
    private Comparison compareNovemberWithDecember() {
        ManagementReport before = report(NOVEMBER, List.of(
                goal(1L, "Prepare concept note", "34", "2027-01-10", step(10L, List.of(
                        task(100L, "Search PubMed", WorkStatus.NOT_STARTED, "2027-01-05", List.of()),
                        task(101L, "Write rationale", WorkStatus.NOT_STARTED, "2027-01-01", List.of())),
                        List.of(blocker(500L, "No access to journals", Severity.LOW))), NOVEMBER),
                goal(2L, "Present to mentor", "50", "2027-01-10", step(20L, List.of(
                        task(200L, "Book a meeting", WorkStatus.IN_PROGRESS, "2027-01-08", List.of())), List.of()), NOVEMBER),
                goal(3L, "Old goal", "18", "2027-02-01", step(30L, List.of(), List.of()), NOVEMBER)));
        ManagementReport now = report(DECEMBER, List.of(
                goal(1L, "Prepare the concept note", "46", "2027-01-10", step(10L, List.of(
                        task(100L, "Search PubMed", WorkStatus.COMPLETED, "2027-01-05", List.of()),
                        task(101L, "Write rationale", WorkStatus.NOT_STARTED, "2027-01-15", List.of())), List.of()), DECEMBER),
                goal(2L, "Present to mentor", "50", "2027-01-24", step(20L, List.of(
                        task(200L, "Book a meeting", WorkStatus.BLOCKED, "2027-01-08",
                                List.of(blocker(501L, "Mentor away", Severity.CRITICAL)))), List.of()), DECEMBER),
                goal(4L, "Learn AI tools", "42", "2027-03-01", step(40L, List.of(), List.of()), DECEMBER)));
        ManagementReportResponse beforeSummary = service.toResponse(before);
        return ReportComparison.compare(new ReportComparison.Earlier(7L, "R-001", "November progress", before, beforeSummary),
                now, service.toResponse(now), Map.of(3L, "ARCHIVED"));
    }

    @Test
    void rmc13_headlineFiguresChangeBySignedPoints() {
        Comparison comparison = compareNovemberWithDecember();

        assertThat(comparison.reportId()).isEqualTo(7L);
        assertThat(comparison.asOf()).isEqualTo(NOVEMBER);
        assertThat(comparison.headline().overallProgress()).isEqualByComparingTo("12.0");
        assertThat(comparison.headline().completedTasks()).isEqualTo(1);
        assertThat(comparison.headline().blockedTasks()).isEqualTo(1);
        assertThat(ReportText.signed(comparison.headline().overallProgress())).isEqualTo("+12");
        assertThat(ReportText.signed(-2L)).isEqualTo("−2");
        assertThat(ReportText.signed(0L)).isEqualTo("0");
        assertThat(ReportText.signed(new BigDecimal("-0.5"))).isEqualTo("−0.5");
    }

    @Test
    void rmc12_goalsShowHealthBeforeProgressChangeTargetMovedNewAndRemoved() {
        Comparison comparison = compareNovemberWithDecember();

        GoalChange concept = comparison.goal(1L);
        assertThat(concept.healthBefore()).isEqualTo("AMBER");
        assertThat(concept.progressChange()).isEqualByComparingTo("12.0");
        assertThat(concept.previousTitle()).isEqualTo("Prepare concept note");
        assertThat(concept.targetBefore()).isNull();
        assertThat(comparison.goal(2L).targetBefore()).isEqualTo(LocalDate.parse("2027-01-10"));
        assertThat(comparison.goal(4L).isNew()).isTrue();
        assertThat(comparison.goal(99L)).isNull();
        assertThat(comparison.removed()).singleElement()
                .satisfies(removed -> assertThat(removed.title()).isEqualTo("Old goal"))
                .satisfies(removed -> assertThat(removed.reason()).isEqualTo("ARCHIVED"));

        ManagementReportResponse.GoalHealthRow row = new ManagementReportResponse.GoalHealthRow(1L, "G-1", "Prepare the concept note",
                1L, "Dream", "GREEN", new BigDecimal("46.0"), LocalDate.parse("2027-01-10"), 0, 0, null, List.of());
        assertThat(ReportText.of("en").goalChange(concept, row)).isEqualTo("At risk → On track · +12 points · (renamed)");
        ManagementReportResponse.GoalHealthRow mentor = new ManagementReportResponse.GoalHealthRow(2L, "G-2", "Present to mentor",
                1L, "Dream", "RED", new BigDecimal("50.0"), LocalDate.parse("2027-01-24"), 0, 1, null, List.of());
        assertThat(ReportText.of("en").goalChange(comparison.goal(2L), mentor))
                .isEqualTo("On track → Off track · Target 10 Jan 2027 → 24 Jan 2027");
        assertThat(ReportText.of("en").goalChange(comparison.goal(4L), row)).isEqualTo("New");
        assertThat(ReportText.of("en").goalChange(null, row)).isEmpty();
        assertThat(ReportText.of("en").removedReason("MOVED")).isEqualTo("Moved to another dream");
        assertThat(ReportText.of("en").removedReason(null)).isEmpty();
    }

    @Test
    void rmFr35_whatChangedIsListedMostImportantFirst() {
        Comparison comparison = compareNovemberWithDecember();

        assertThat(comparison.changes()).extracting(ChangeItem::kind, ChangeItem::itemType, ChangeItem::itemId, ChangeItem::before,
                ChangeItem::after).containsExactly(
                tuple("GOAL_WORSE", "GOAL", 2L, "GREEN", "RED"),
                tuple("DUE_LATER", "GOAL", 2L, "2027-01-10", "2027-01-24"),
                tuple("DUE_LATER", "TASK", 101L, "2027-01-01", "2027-01-15"),
                tuple("NEW_BLOCKER", "OBSTACLE", 501L, null, "CRITICAL"),
                tuple("BLOCKER_CLOSED", "OBSTACLE", 500L, null, null),
                tuple("GOAL_BETTER", "GOAL", 1L, "AMBER", "GREEN"),
                tuple("TASK_COMPLETED", "TASK", 100L, null, null));
        assertThat(comparison.totalChanges()).isEqualTo(7);
        // RM-FR-37: still the same goal after a rename, shown with "(renamed)".
        assertThat(comparison.changes().get(5).renamed()).isTrue();
        assertThat(comparison.changes().get(5).title()).isEqualTo("Prepare the concept note");
        assertThat(comparison.changes().get(2).context()).isEqualTo("Prepare the concept note › Step 10");

        ReportText text = ReportText.of("en");
        assertThat(text.changeKind("DUE_LATER")).isEqualTo("Date moved later");
        assertThat(text.changeValue("DUE_LATER", "2027-01-15")).isEqualTo("15 Jan 2027");
        assertThat(text.changeValue("GOAL_BETTER", "GREEN")).isEqualTo("On track");
        assertThat(text.changeValue("NEW_BLOCKER", "CRITICAL")).isEqualTo("Critical");
        assertThat(text.changeValue("TASK_COMPLETED", null)).isEmpty();
        assertThat(text.changeKind("SOMETHING_NEW")).isEqualTo("SOMETHING_NEW");
    }

    @Test
    void rmFr28_aGoalThatMovedToOnTrackIsAHighlight() {
        Comparison comparison = compareNovemberWithDecember();
        ManagementReport now = report(DECEMBER, List.of(goal(1L, "Prepare the concept note", "46", "2027-01-10",
                step(10L, List.of(task(101L, "Write rationale", WorkStatus.NOT_STARTED, "2027-01-15", List.of())), List.of()), DECEMBER)));

        String highlights = ReportCommentaryDrafter.draft(now, ReportText.of("en"), comparison).highlights();

        assertThat(highlights).isEqualTo("- Prepare the concept note moved to On track");
        assertThat(ReportCommentaryDrafter.draft(now, ReportText.of("en")).highlights()).isEmpty();
    }

    @Test
    void identicalReportsHaveNoChangesAndManyChangesAreCapped() {
        List<TaskNode> tasks = new ArrayList<>();
        List<TaskNode> done = new ArrayList<>();
        for (long id = 1; id <= 520; id++) {
            tasks.add(task(1000 + id, "Task " + id, WorkStatus.NOT_STARTED, "2027-01-05", List.of()));
            done.add(task(1000 + id, "Task " + id, WorkStatus.COMPLETED, "2027-01-05", List.of()));
        }
        ManagementReport before = report(NOVEMBER, List.of(goal(1L, "Goal", "10", "2027-02-01", step(10L, tasks, List.of()), NOVEMBER)));
        ManagementReport after = report(DECEMBER, List.of(goal(1L, "Goal", "10", "2027-02-01", step(10L, done, List.of()), DECEMBER)));
        ReportComparison.Earlier earlier = new ReportComparison.Earlier(1L, "R-001", "Before", before, service.toResponse(before));

        Comparison same = ReportComparison.compare(earlier, before, service.toResponse(before), Map.of());
        Comparison many = ReportComparison.compare(earlier, after, service.toResponse(after), Map.of());

        assertThat(same.changes()).isEmpty();
        assertThat(same.removed()).isEmpty();
        assertThat(same.goal(1L).progressChange()).isEqualByComparingTo("0");
        assertThat(many.changes()).hasSize(500);
        assertThat(many.totalChanges()).isEqualTo(520);
    }

    // ---------- builders ----------

    private static ManagementReport report(LocalDate asOf, List<GoalNode> goals) {
        return new ManagementReport("DREAM", null, 1L, "Dream", asOf, new ReportPeriod(ReportPeriod.Kind.THIS_MONTH, asOf.withDayOfMonth(1), asOf),
                goals, List.of(), PeriodFigures.NONE, ReportFilters.NONE, List.of());
    }

    private static GoalNode goal(Long id, String title, String progress, String target, StepNode step, LocalDate today) {
        return GoalNode.assessed(new GoalInfo(id, "G-" + id, title, WorkStatus.IN_PROGRESS, Priority.HIGH, new BigDecimal(progress),
                LocalDate.parse(target), null), new DreamInfo(1L, "Dream"), List.of(step), today);
    }

    private static StepNode step(Long id, List<TaskNode> tasks, List<BlockerInfo> blockers) {
        return new StepNode(new StepInfo(id, "Step " + id, WorkStatus.IN_PROGRESS, Priority.HIGH, BigDecimal.ZERO,
                LocalDate.parse("2027-02-01"), null), tasks, blockers, false);
    }

    private static TaskNode task(Long id, String title, WorkStatus status, String due, List<BlockerInfo> blockers) {
        return new TaskNode(new TaskInfo(id, title, status, Priority.MEDIUM, BigDecimal.ZERO, LocalDate.parse(due), "Me", null,
                status == WorkStatus.BLOCKED ? "Waiting" : null, null, null), blockers);
    }

    private static BlockerInfo blocker(Long id, String title, Severity severity) {
        return new BlockerInfo(id, title, ObstacleType.PARTNER, severity, ObstacleStatus.OPEN, null, NOVEMBER, "");
    }
}
