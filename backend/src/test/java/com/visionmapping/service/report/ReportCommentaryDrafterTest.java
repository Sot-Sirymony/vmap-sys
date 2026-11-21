package com.visionmapping.service.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.Severity;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.service.report.ManagementReport.BlockerInfo;
import com.visionmapping.service.report.ManagementReport.DreamInfo;
import com.visionmapping.service.report.ManagementReport.GoalInfo;
import com.visionmapping.service.report.ManagementReport.GoalNode;
import com.visionmapping.service.report.ManagementReport.PeriodFigures;
import com.visionmapping.service.report.ManagementReport.StepInfo;
import com.visionmapping.service.report.ManagementReport.StepNode;
import com.visionmapping.service.report.ManagementReport.TaskInfo;
import com.visionmapping.service.report.ManagementReport.TaskNode;
import com.visionmapping.service.report.ReportCommentaryDrafter.Draft;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** V19 RM-FR-28: the rule-based first draft of the commentary. */
class ReportCommentaryDrafterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

    @Test
    void eachSectionComesFromItsOwnRule() {
        GoalNode stuck = goal(1L, "Collect the data", WorkStatus.IN_PROGRESS, TODAY.minusDays(2), new StepNode(stepInfo(ObstacleType.SKILL),
                List.of(task("Run survey", WorkStatus.BLOCKED, null), task("Book hall", WorkStatus.COMPLETED, TODAY.minusDays(1))),
                List.of(blocker("No budget", ObstacleType.MONEY, Severity.CRITICAL), blocker("Unsure", ObstacleType.DECISION, Severity.LOW)),
                true));
        GoalNode done = goal(2L, "Pick a topic", WorkStatus.COMPLETED, TODAY.plusDays(5),
                new StepNode(stepInfo(null), List.of(task("Choose", WorkStatus.COMPLETED, TODAY.minusDays(40))), List.of(), false));

        Draft draft = ReportCommentaryDrafter.draft(report(List.of(stuck, done)), ReportText.of("en"));

        assertThat(draft.highlights()).isEqualTo("- Collect the data: completed 1 task(s) (Book hall)\n- Pick a topic is done");
        assertThat(draft.risks()).startsWith("- Collect the data is off track: Target date passed 2 day(s) ago; 1 blocked task(s)")
                .contains("- No budget (Money, Critical) on Collect the data › Step");
        assertThat(draft.decisionsNeeded()).contains("- No budget (Money) on Collect the data › Step",
                "- Unsure (Decision) on Collect the data › Step", "- Step: missing skill, no help planned yet");
        assertThat(draft.nextFocus()).isEqualTo("- Collect the data: Run survey");
    }

    @Test
    void anEmptyPlanSuggestsNothingAndLongSectionsAreCut() {
        Draft empty = ReportCommentaryDrafter.draft(report(List.of()), ReportText.of("en"));
        assertThat(empty).isEqualTo(new Draft("", "", "", ""));

        List<GoalNode> many = new ArrayList<>();
        for (long id = 1; id <= 12; id++) {
            many.add(goal(id, "G".repeat(300), WorkStatus.IN_PROGRESS, TODAY.plusDays(30),
                    new StepNode(stepInfo(null), List.of(task("T".repeat(200), WorkStatus.NOT_STARTED, null)), List.of(), false)));
        }
        String focus = ReportCommentaryDrafter.draft(report(many), ReportText.of("en")).nextFocus();
        assertThat(focus.split("\n")).hasSizeLessThanOrEqualTo(ReportCommentaryDrafter.MAX_LINES);
        assertThat(focus).hasSizeLessThanOrEqualTo(ReportCommentaryDrafter.MAX_CHARS).endsWith("…");
    }

    @Test
    void rmFr29_theFactSheetHoldsGoalFiguresAndTheRuleBasedNotesOnly() {
        GoalNode stuck = goal(1L, "Collect the data", WorkStatus.IN_PROGRESS, TODAY.minusDays(2), new StepNode(stepInfo(null),
                List.of(task("Run survey", WorkStatus.BLOCKED, null)), List.of(blocker("No budget", ObstacleType.MONEY, Severity.HIGH)), false));
        String facts = ReportCommentaryDrafter.facts(report(List.of(stuck)), ReportText.of("en"), null);
        assertThat(facts).startsWith("Report period: ").contains("- Collect the data: 0% done, Off track (Target date passed",
                "Rule-based notes, Highlights:\n- nothing", "Rule-based notes, Risks and issues:\n- Collect the data is off track",
                "No budget (Money, High)", "Rule-based notes, Focus for next period:\n- Collect the data: Run survey");
        assertThat(ReportCommentaryDrafter.facts(report(List.of()), ReportText.of("en"), null)).contains("Goals:\n- none in scope");

        List<GoalNode> many = new ArrayList<>();
        for (long id = 1; id <= 60; id++) {
            many.add(goal(id, "G".repeat(300), WorkStatus.IN_PROGRESS, TODAY.plusDays(30),
                    new StepNode(stepInfo(null), List.of(task("T", WorkStatus.NOT_STARTED, null)), List.of(), false)));
        }
        assertThat(ReportCommentaryDrafter.facts(report(many), ReportText.of("en"), null)).hasSize(ReportCommentaryDrafter.MAX_FACTS);
    }

    @Test
    void rmFr29_aiTextIsHeldToTheDraftShape() {
        assertThat(ReportCommentaryDrafter.bullets(null)).isEmpty();
        assertThat(ReportCommentaryDrafter.bullets("  \n")).isEmpty();
        assertThat(ReportCommentaryDrafter.bullets("Plain line\n- dash\n* star\n• dot\n2) number\n3. number")).isEqualTo(
                "- Plain line\n- dash\n- star\n- dot\n- number\n- number");
        String many = String.join("\n", java.util.Collections.nCopies(12, "point"));
        assertThat(ReportCommentaryDrafter.bullets(many).split("\n")).hasSize(ReportCommentaryDrafter.MAX_LINES);
    }

    private static ManagementReport report(List<GoalNode> goals) {
        return new ManagementReport("ALL", null, null, null, TODAY, ReportPeriod.resolve(null, null, null, TODAY), goals, List.of(),
                PeriodFigures.NONE, ReportFilters.NONE, List.of());
    }

    private static GoalNode goal(Long id, String title, WorkStatus status, LocalDate target, StepNode step) {
        return GoalNode.assessed(new GoalInfo(id, "G-" + id, title, status, Priority.HIGH, BigDecimal.ZERO, target, TODAY.minusDays(1)),
                new DreamInfo(1L, "Dream"), List.of(step), TODAY);
    }

    private static StepInfo stepInfo(ObstacleType gap) {
        return new StepInfo(10L, "Step", WorkStatus.IN_PROGRESS, Priority.HIGH, BigDecimal.ZERO, TODAY.plusDays(20), gap);
    }

    private static TaskNode task(String title, WorkStatus status, LocalDate completedOn) {
        return new TaskNode(new TaskInfo(100L, title, status, Priority.MEDIUM, BigDecimal.ZERO, TODAY.plusDays(5), "Me", null,
                status == WorkStatus.BLOCKED ? "Waiting" : null, TODAY, completedOn), List.of());
    }

    private static BlockerInfo blocker(String title, ObstacleType type, Severity severity) {
        return new BlockerInfo(50L, title, type, severity, ObstacleStatus.OPEN, null, TODAY, "");
    }
}
