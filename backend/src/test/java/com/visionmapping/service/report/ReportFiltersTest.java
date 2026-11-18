package com.visionmapping.service.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.service.report.ManagementReport.DreamInfo;
import com.visionmapping.service.report.ManagementReport.GoalInfo;
import com.visionmapping.service.report.ManagementReport.GoalNode;
import com.visionmapping.service.report.ManagementReport.StepInfo;
import com.visionmapping.service.report.ManagementReport.StepNode;
import com.visionmapping.service.report.ManagementReport.TaskInfo;
import com.visionmapping.service.report.ManagementReport.TaskNode;
import com.visionmapping.service.report.ReportRules.Health;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** V19 RM-FR-10, RM-FR-11: filters narrow goals, steps and tasks without changing a goal's health. */
class ReportFiltersTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

    @Test
    void noFiltersKeepEverythingAsItIs() {
        List<GoalNode> goals = List.of(goal(1L, WorkStatus.IN_PROGRESS, Priority.HIGH, step(task(Priority.LOW, "Dara"))));
        assertThat(ReportFilters.NONE.isEmpty()).isTrue();
        assertThat(ReportFilters.NONE.apply(goals)).isSameAs(goals);
        assertThat(new ReportFilters(Set.of(), "  ", Set.of(), true).owner()).isNull();
    }

    @Test
    void priorityAndOwnerKeepMatchingTasksTheirStepsAndGoalsOnly() {
        GoalNode mixed = goal(1L, WorkStatus.IN_PROGRESS, Priority.HIGH,
                step(task(Priority.CRITICAL, "Dara"), task(Priority.LOW, "Sok")), step(task(Priority.LOW, "Sok")));
        GoalNode lowOnly = goal(2L, WorkStatus.IN_PROGRESS, Priority.HIGH, step(task(Priority.LOW, "Dara")));
        GoalNode empty = goal(3L, WorkStatus.IN_PROGRESS, Priority.CRITICAL);

        List<GoalNode> critical = new ReportFilters(Set.of(Priority.CRITICAL), null, Set.of(), true).apply(List.of(mixed, lowOnly, empty));
        assertThat(critical).extracting(node -> node.goal().id()).containsExactly(1L, 3L);
        assertThat(critical.get(0).steps()).hasSize(1);
        assertThat(critical.get(0).tasks()).extracting(task -> task.task().owner()).containsExactly("Dara");
        // A goal keeps the health it had on all its work.
        assertThat(critical.get(0).assessment()).isEqualTo(mixed.assessment());

        List<GoalNode> dara = new ReportFilters(Set.of(), " dara ", Set.of(), true).apply(List.of(mixed, lowOnly, empty));
        assertThat(dara).extracting(node -> node.goal().id()).containsExactly(1L, 2L);
    }

    @Test
    void healthAndCompletedGoalsFilterWholeGoals() {
        GoalNode done = goal(1L, WorkStatus.COMPLETED, Priority.HIGH, step(task(Priority.LOW, "Dara", WorkStatus.COMPLETED)));
        GoalNode late = goal(2L, WorkStatus.IN_PROGRESS, Priority.HIGH, step(task(Priority.LOW, "Dara", WorkStatus.BLOCKED)));
        GoalNode unassessed = new GoalNode(late.goal(), late.dream(), late.steps(), null);

        assertThat(new ReportFilters(Set.of(), null, Set.of(Health.RED), true).apply(List.of(done, late, unassessed)))
                .extracting(node -> node.goal().id()).containsExactly(2L);
        assertThat(new ReportFilters(Set.of(), null, Set.of(), false).apply(List.of(done, late)))
                .extracting(node -> node.goal().id()).containsExactly(2L);
    }

    private static GoalNode goal(Long id, WorkStatus status, Priority priority, StepNode... steps) {
        return GoalNode.assessed(new GoalInfo(id, "G-" + id, "Goal " + id, status, priority, BigDecimal.ZERO, TODAY.plusDays(30),
                TODAY.minusDays(1)), new DreamInfo(1L, "Dream"), List.of(steps), TODAY);
    }

    private static StepNode step(TaskNode... tasks) {
        return new StepNode(new StepInfo(10L, "Step", WorkStatus.IN_PROGRESS, Priority.MEDIUM, BigDecimal.ZERO, TODAY.plusDays(20), null),
                List.of(tasks), List.of(), false);
    }

    private static TaskNode task(Priority priority, String owner) {
        return task(priority, owner, WorkStatus.NOT_STARTED);
    }

    private static TaskNode task(Priority priority, String owner, WorkStatus status) {
        return new TaskNode(new TaskInfo(100L, "Task", status, priority, BigDecimal.ZERO, TODAY.plusDays(5), owner, null,
                status == WorkStatus.BLOCKED ? "Waiting" : null, TODAY, null), List.of());
    }
}
