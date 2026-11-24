package com.visionmapping.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.visionmapping.dto.request.PlanDraftContent;
import com.visionmapping.dto.request.PlanDraftContent.DraftGoal;
import com.visionmapping.dto.request.PlanDraftContent.DraftStep;
import com.visionmapping.dto.request.PlanDraftContent.DraftTask;
import com.visionmapping.dto.response.AiPlanResponses.DraftIssue;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.service.support.EntityLookup;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** V31 (BRD 31 section 5): the rule catalogue on new items, without the database. */
class PlanRuleServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 1, 10);

    private final PlanRuleService service = new PlanRuleService(mock(EntityLookup.class), mock(VisionStepRepository.class));
    private final AppUser user = AppUser.builder().id(1L).build();

    @Test
    void anIncompleteDreamWarnsOncePerMissingPart() {
        PlanRuleService.Report report = service.evaluate(user, Dream.builder().id(5L).title("Own a home").build(),
                new PlanDraftContent(List.of(), null, null, null, false, null, null), TODAY);
        assertThat(kinds(report)).containsExactly("DREAM_NO_WHY", "DREAM_NO_SUCCESS", "DREAM_NO_DATE", "DREAM_NO_PRIORITY", "INNER_SKIPPED");
        assertThat(report.mustFails()).isZero();
        assertThat(report.firstMust()).isNull();
        assertThat(report.shouldRules()).containsExactly("R-02", "R-05");
        assertThat(report.stats()).containsOnlyKeys(PlanRuleService.RULES);
    }

    @Test
    void blankAndPastItemsFailTheirRules() {
        DraftTask task = new DraftTask(null, " ", " ", null, null, null, null, false, false, null, null);
        DraftTask pastTask = new DraftTask("t2", "Old task", "Me", TODAY.minusDays(1), Priority.LOW, null, null, false, false,
                WorkStatus.IN_PROGRESS, null);
        DraftStep first = step("s1", "Earlier", TODAY.plusDays(20), null, List.of());
        DraftStep undated = new DraftStep("s2", null, "", null, "", null, Priority.LOW, false, null, null, false, false,
                List.of(task), null, null, null);
        DraftStep waiting = step("s3", "Later", TODAY.plusDays(5), "s1", List.of(pastTask));
        DraftStep past = step("s4", "Past", TODAY.minusDays(2), null, List.of());
        DraftGoal goal = new DraftGoal("g1", null, " ", null, "Done when saved", null, Priority.HIGH, false, null, false, false,
                List.of(first, undated, waiting, past), false);
        DraftGoal pastGoal = new DraftGoal("g2", null, "Old goal", null, "Done", TODAY.minusDays(3), Priority.HIGH, false, null,
                false, false, List.of(step("s5", "One", TODAY.plusDays(1), null, List.of())), false);

        PlanRuleService.Report report = service.evaluate(user, dream(null), new PlanDraftContent(List.of(goal, pastGoal), null, null, null, false, null, null), TODAY);

        assertThat(kinds(report)).contains("MISSING_TITLE", "GOAL_NO_DATE", "MISSING_OWNER", "MISSING_DATE", "BEFORE_WAITED_STEP",
                "PAST_DATE", "TASK_NO_PRIORITY");
        DraftIssue noDate = find(report, "GOAL_NO_DATE");
        assertThat(noDate.fixAction()).isEqualTo("GO_TO_GOALS");
        assertThat(noDate.fixValue()).isNull();
        DraftIssue taskDate = report.issues().stream()
                .filter(issue -> "MISSING_DATE".equals(issue.kind()) && "TASK".equals(issue.itemType())).findFirst().orElseThrow();
        assertThat(taskDate.fixAction()).isNull();
        assertThat(report.issues().stream().filter(issue -> "PAST_DATE".equals(issue.kind())).map(DraftIssue::itemType))
                .contains("GOAL", "STEP", "TASK");
        assertThat(report.firstMust()).isNotNull();
        assertThat(report.stats().get("R-04").must()).isPositive();
    }

    @Test
    void gapsOutsideKnowledgeSkillAndMoneyNeedNoHelper() {
        DraftStep time = new DraftStep("s1", null, "Write", null, "Me", TODAY.plusDays(3), Priority.LOW, false, "No free evenings",
                null, false, false, List.of(), null, null, "TIME");
        DraftGoal goal = new DraftGoal("g1", null, "Write the note", null, "Draft done", TODAY.plusDays(30), Priority.HIGH, false,
                null, false, false, List.of(time), true);
        PlanRuleService.Report report = service.evaluate(user, dream(TODAY.plusMonths(3)),
                new PlanDraftContent(List.of(goal), null, null, null, false, null, null), TODAY);
        assertThat(kinds(report)).doesNotContain("GAP_NO_HELPER", "GAP_NO_KIND", "MISSING_RESOURCE");
        assertThat(report.mustFails()).isZero();
    }

    /** V31 v1 step 2 (DC-FR-22, DC-FR-42): the inner blockers stage, and a second fix that moves a new parent later. */
    @Test
    void innerBlockersAndMovingAParentLater() {
        DraftTask late = new DraftTask("t1", "Reference table", "Me", TODAY.plusDays(25), Priority.LOW, null, null, false, false, null, null);
        DraftStep newStep = step("s1", "Search literature", TODAY.plusDays(20), null, List.of(late));
        DraftStep lateStep = step("s2", "Write up", TODAY.plusDays(70), null, List.of());
        DraftGoal goal = new DraftGoal("g1", null, "Prepare a note", null, "Draft done", TODAY.plusDays(60), Priority.HIGH, false, null,
                false, false, List.of(newStep, lateStep), true);
        Dream fixed = Dream.builder().id(5L).title("Researcher").whyImportant("Impact").successDefinition("Accepted").priority(Priority.HIGH)
                .targetDate(TODAY.plusDays(50)).scheduleMode(com.visionmapping.entity.enums.ScheduleMode.TOP_DOWN_FIXED).build();

        PlanRuleService.Report skipped = service.evaluate(user, fixed,
                new PlanDraftContent(List.of(goal), null, null, null, false, "SKIPPED", null), TODAY);
        DraftIssue taskConflict = find(skipped, "TASK_AFTER_STEP");
        assertThat(taskConflict.altFixAction()).isEqualTo("MOVE_PARENT_DATE");
        assertThat(taskConflict.altFixValue()).isEqualTo(TODAY.plusDays(25).toString());
        assertThat(find(skipped, "AFTER_GOAL_DATE").altFixValue()).isEqualTo(TODAY.plusDays(70).toString());
        // A fixed dream deadline offers no "move the dream later".
        assertThat(find(skipped, "GOAL_AFTER_DREAM").altFixAction()).isNull();
        assertThat(find(skipped, "INNER_SKIPPED").problem()).isEqualTo("You skipped looking at what might hold you back inside.");

        PlanRuleService.Report ready = service.evaluate(user, dream(TODAY.plusDays(50)),
                new PlanDraftContent(List.of(goal), null, null, null, false, "READY", null), TODAY);
        assertThat(kinds(ready)).doesNotContain("INNER_SKIPPED");
        assertThat(find(ready, "GOAL_AFTER_DREAM").altFixAction()).isEqualTo("MOVE_DREAM_DATE");
    }

    private static DraftStep step(String key, String title, LocalDate date, String after, List<DraftTask> tasks) {
        return new DraftStep(key, null, title, null, "Me", date, Priority.MEDIUM, false, "Laptop", null, false, false, tasks, null,
                after, null);
    }

    private static Dream dream(LocalDate date) {
        return Dream.builder().id(5L).title("Become a researcher").whyImportant("Impact").successDefinition("Accepted")
                .priority(Priority.HIGH).targetDate(date).build();
    }

    private static List<String> kinds(PlanRuleService.Report report) {
        return report.issues().stream().map(DraftIssue::kind).toList();
    }

    private static DraftIssue find(PlanRuleService.Report report, String kind) {
        return report.issues().stream().filter(issue -> kind.equals(issue.kind())).findFirst().orElseThrow();
    }
}
