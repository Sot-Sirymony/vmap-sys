package com.visionmapping.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.visionmapping.entity.enums.DreamStatus;
import com.visionmapping.entity.enums.LifecycleStatus;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * BRD 13 Phase 2 (EAC-01, EAC-02): the core entities keep their own rules.
 * These tests use only entity methods — no service, no repository — so they
 * prove that no caller can put an entity into an invalid state.
 */
class CoreEntityRulesTest {

    private static final Instant FIRST = Instant.parse("2026-11-01T09:00:00Z");
    private static final Instant LATER = Instant.parse("2026-11-05T09:00:00Z");
    private static final Clock FIRST_CLOCK = Clock.fixed(FIRST, ZoneOffset.UTC);
    private static final Clock LATER_CLOCK = Clock.fixed(LATER, ZoneOffset.UTC);

    @Nested
    class TaskItemRules {

        @Test
        void cannotBeBlockedWithoutAReason() {
            TaskItem task = task(WorkStatus.IN_PROGRESS, null);

            assertThatThrownBy(() -> task.changeStatus(WorkStatus.BLOCKED, FIRST_CLOCK))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Blocked tasks must include a blocker reason.");
            assertThat(task.getStatus()).isEqualTo(WorkStatus.IN_PROGRESS);
        }

        @Test
        void aBlankReasonDoesNotCount() {
            TaskItem task = task(WorkStatus.IN_PROGRESS, "   ");

            assertThatThrownBy(() -> task.changeStatus(WorkStatus.BLOCKED, FIRST_CLOCK))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        void canBeBlockedWithAReason() {
            TaskItem task = task(WorkStatus.IN_PROGRESS, "Waiting for the mentor's reply");

            task.changeStatus(WorkStatus.BLOCKED, FIRST_CLOCK);

            assertThat(task.getStatus()).isEqualTo(WorkStatus.BLOCKED);
        }

        @Test
        void completingSetsFullProgressAndTheCompletionTime() {
            TaskItem task = task(WorkStatus.IN_PROGRESS, null);

            task.changeStatus(WorkStatus.COMPLETED, FIRST_CLOCK);

            assertThat(task.getProgressPercent()).isEqualByComparingTo("100");
            assertThat(task.getCompletedAt()).isEqualTo(FIRST);
        }

        @Test
        void completingAgainKeepsTheFirstCompletionTime() {
            TaskItem task = task(WorkStatus.IN_PROGRESS, null);
            task.changeStatus(WorkStatus.COMPLETED, FIRST_CLOCK);

            task.changeStatus(WorkStatus.COMPLETED, LATER_CLOCK);

            assertThat(task.getCompletedAt()).isEqualTo(FIRST);
        }

        @Test
        void reopeningClearsTheCompletionTime() {
            TaskItem task = task(WorkStatus.IN_PROGRESS, null);
            task.changeStatus(WorkStatus.COMPLETED, FIRST_CLOCK);

            task.changeStatus(WorkStatus.IN_PROGRESS, LATER_CLOCK);

            assertThat(task.getCompletedAt()).isNull();
        }

        @Test
        void aNewTaskFromTheBuilderIsCheckedBeforeItsFirstSave() {
            TaskItem blocked = task(WorkStatus.BLOCKED, null);
            TaskItem completed = task(WorkStatus.COMPLETED, null);

            assertThatThrownBy(() -> blocked.checkNewTask(FIRST_CLOCK)).isInstanceOf(BusinessRuleException.class);
            completed.checkNewTask(FIRST_CLOCK);
            assertThat(completed.getProgressPercent()).isEqualByComparingTo("100");
            assertThat(completed.getCompletedAt()).isEqualTo(FIRST);
        }

        @Test
        void aRefusedUpdateLeavesEveryFieldAsItWas() {
            VisionStep originalStep = VisionStep.builder().id(1L).build();
            TaskItem task = task(WorkStatus.IN_PROGRESS, null);
            task.setStep(originalStep);
            VisionStep otherStep = VisionStep.builder().id(2L).build();

            TaskItem.Details blockedWithoutReason = new TaskItem.Details(otherStep, "New title", "New description",
                    "Someone else", Priority.LOW, "B", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31),
                    WorkStatus.BLOCKED, new BigDecimal("80.00"), BigDecimal.TEN, BigDecimal.ONE, " ", "Next", null);

            assertThatThrownBy(() -> task.update(blockedWithoutReason, FIRST_CLOCK))
                    .isInstanceOf(BusinessRuleException.class);
            assertThat(task.getStep()).isSameAs(originalStep);
            assertThat(task.getTitle()).isEqualTo("Search PubMed");
            assertThat(task.getDescription()).isNull();
            assertThat(task.getOwner()).isEqualTo("Me");
            assertThat(task.getPriority()).isEqualTo(Priority.HIGH);
            assertThat(task.getLetterRank()).isNull();
            assertThat(task.getDueDate()).isEqualTo(LocalDate.of(2026, 11, 30));
            assertThat(task.getStatus()).isEqualTo(WorkStatus.IN_PROGRESS);
            assertThat(task.getProgressPercent()).isEqualByComparingTo("40");
            assertThat(task.getBlockerReason()).isNull();
            assertThat(task.getNextAction()).isNull();
        }

        @Test
        void anAcceptedUpdateWritesEveryFieldThenAppliesTheCompletionRule() {
            TaskItem task = task(WorkStatus.IN_PROGRESS, null);
            VisionStep step = VisionStep.builder().id(2L).build();

            task.update(new TaskItem.Details(step, "Save 15 articles", "From both databases", "Me", Priority.CRITICAL,
                    "A", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 20), WorkStatus.COMPLETED,
                    new BigDecimal("60.00"), BigDecimal.TEN, BigDecimal.ONE, null, "Summarise", null), FIRST_CLOCK);

            assertThat(task.getStep()).isSameAs(step);
            assertThat(task.getTitle()).isEqualTo("Save 15 articles");
            assertThat(task.getPriority()).isEqualTo(Priority.CRITICAL);
            assertThat(task.getStatus()).isEqualTo(WorkStatus.COMPLETED);
            assertThat(task.getProgressPercent()).isEqualByComparingTo("100");
            assertThat(task.getCompletedAt()).isEqualTo(FIRST);
        }

        @Test
        void reachingFullProgressFromAProgressLogCompletesTheTask() {
            TaskItem task = task(WorkStatus.IN_PROGRESS, null);

            task.recordProgress(new BigDecimal("100.00"), FIRST_CLOCK);

            assertThat(task.getStatus()).isEqualTo(WorkStatus.COMPLETED);
            assertThat(task.getCompletedAt()).isEqualTo(FIRST);
        }

        @Test
        void partialProgressFromAProgressLogKeepsTheStatus() {
            TaskItem task = task(WorkStatus.IN_PROGRESS, null);

            task.recordProgress(new BigDecimal("55.00"), FIRST_CLOCK);

            assertThat(task.getStatus()).isEqualTo(WorkStatus.IN_PROGRESS);
            assertThat(task.getProgressPercent()).isEqualByComparingTo("55");
            assertThat(task.getCompletedAt()).isNull();
        }

        @Test
        void archiveAndRestoreOnlyMoveTheFlag() {
            TaskItem task = task(WorkStatus.IN_PROGRESS, null);

            task.archive();
            assertThat(task.isArchived()).isTrue();
            assertThat(task.getStatus()).isEqualTo(WorkStatus.IN_PROGRESS);

            task.restore();
            assertThat(task.isArchived()).isFalse();
        }

        @Test
        void argumentsAreCheckedFirst() {
            TaskItem task = task(WorkStatus.IN_PROGRESS, null);

            assertThatThrownBy(() -> task.changeStatus(null, FIRST_CLOCK)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> task.update(null, FIRST_CLOCK)).isInstanceOf(NullPointerException.class);
            assertThat(task.getStatus()).isEqualTo(WorkStatus.IN_PROGRESS);
        }
    }

    @Nested
    class RollupRules {

        @Test
        void aStepTakesTheAverageAndCompletesWhenAllTasksAreComplete() {
            VisionStep step = VisionStep.builder().status(WorkStatus.IN_PROGRESS).progressPercent(BigDecimal.ZERO).build();

            step.applyRollup(new BigDecimal("50.00"), false);
            assertThat(step.getProgressPercent()).isEqualByComparingTo("50");
            assertThat(step.getStatus()).isEqualTo(WorkStatus.IN_PROGRESS);

            step.applyRollup(new BigDecimal("100.00"), true);
            assertThat(step.getStatus()).isEqualTo(WorkStatus.COMPLETED);
        }

        @Test
        void aGoalTakesTheAverageAndCompletesWhenAllStepsAreComplete() {
            Goal goal = Goal.builder().status(WorkStatus.NOT_STARTED).progressPercent(BigDecimal.ZERO).build();

            goal.applyRollup(new BigDecimal("100.00"), true);

            assertThat(goal.getProgressPercent()).isEqualByComparingTo("100");
            assertThat(goal.getStatus()).isEqualTo(WorkStatus.COMPLETED);
        }
    }

    @Nested
    class ArchiveRules {

        @Test
        void anArchivedDreamComesBackPaused() {
            Dream dream = Dream.builder().status(DreamStatus.ACTIVE).build();

            dream.archive();
            assertThat(dream.isArchived()).isTrue();
            assertThat(dream.getStatus()).isEqualTo(DreamStatus.ARCHIVED);

            dream.restore();
            assertThat(dream.isArchived()).isFalse();
            assertThat(dream.getStatus()).isEqualTo(DreamStatus.PAUSED);
        }

        @Test
        void restoringADreamThatIsNotArchivedChangesNothing() {
            Dream dream = Dream.builder().status(DreamStatus.ARCHIVED).build();

            dream.restore();

            assertThat(dream.getStatus()).isEqualTo(DreamStatus.ARCHIVED);
        }

        @Test
        void anArchivedVisionAreaComesBackPaused() {
            VisionArea area = VisionArea.builder().status(LifecycleStatus.ACTIVE).build();

            area.archive();
            assertThat(area.getStatus()).isEqualTo(LifecycleStatus.ARCHIVED);

            area.restore();
            assertThat(area.isArchived()).isFalse();
            assertThat(area.getStatus()).isEqualTo(LifecycleStatus.PAUSED);
        }

        @Test
        void goalsAndStepsOnlyMoveTheFlag() {
            Goal goal = Goal.builder().status(WorkStatus.IN_PROGRESS).build();
            VisionStep step = VisionStep.builder().status(WorkStatus.WAITING).build();

            goal.archive();
            step.archive();
            assertThat(goal.isArchived()).isTrue();
            assertThat(step.isArchived()).isTrue();
            assertThat(goal.getStatus()).isEqualTo(WorkStatus.IN_PROGRESS);
            assertThat(step.getStatus()).isEqualTo(WorkStatus.WAITING);

            goal.restore();
            step.restore();
            assertThat(goal.isArchived()).isFalse();
            assertThat(step.isArchived()).isFalse();
        }
    }

    private static TaskItem task(WorkStatus status, String blockerReason) {
        return TaskItem.builder()
                .title("Search PubMed")
                .owner("Me")
                .priority(Priority.HIGH)
                .dueDate(LocalDate.of(2026, 11, 30))
                .status(status)
                .progressPercent(new BigDecimal("40.00"))
                .blockerReason(blockerReason)
                .build();
    }
}
