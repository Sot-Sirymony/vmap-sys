package com.visionmapping.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.visionmapping.dto.request.TaskItemRequest;
import com.visionmapping.dto.response.DashboardSummaryResponse;
import com.visionmapping.dto.response.TaskItemResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.Partner;
import com.visionmapping.entity.ProgressLog;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionArea;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.DreamStatus;
import com.visionmapping.entity.enums.LifecycleStatus;
import com.visionmapping.entity.enums.PartnerStatus;
import com.visionmapping.entity.enums.PartnerSupportType;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.CommunicationMessageRepository;
import com.visionmapping.repository.DreamRepository;
import com.visionmapping.repository.GoalRepository;
import com.visionmapping.repository.ObstacleRepository;
import com.visionmapping.repository.PartnerRepository;
import com.visionmapping.repository.ProgressLogRepository;
import com.visionmapping.repository.ReviewRepository;
import com.visionmapping.repository.TaskItemRepository;
import com.visionmapping.repository.VisionAreaRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.util.UserScope;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pure unit tests for the business rules in {@link VisionMappingService}, complementing the
 * existing MockMvc integration tests. Repositories are mocked; the mapper is real since it has
 * no dependencies of its own.
 */
@ExtendWith(MockitoExtension.class)
class VisionMappingServiceTest {

    @Mock private UserScope userScope;
    @Mock private VisionAreaRepository visionAreaRepository;
    @Mock private DreamRepository dreamRepository;
    @Mock private GoalRepository goalRepository;
    @Mock private VisionStepRepository visionStepRepository;
    @Mock private TaskItemRepository taskItemRepository;
    @Mock private PartnerRepository partnerRepository;
    @Mock private CommunicationMessageRepository communicationMessageRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private ObstacleRepository obstacleRepository;
    @Mock private ProgressLogRepository progressLogRepository;

    private VisionMappingService service;
    private AppUser testUser;

    @BeforeEach
    void setUp() {
        service = new VisionMappingService(userScope, new VisionMappingMapper(), visionAreaRepository,
                dreamRepository, goalRepository, visionStepRepository, taskItemRepository, partnerRepository,
                communicationMessageRepository, reviewRepository, obstacleRepository, progressLogRepository,
                Clock.systemDefaultZone());

        testUser = AppUser.builder()
                .id(1L)
                .fullName("Test User")
                .email("test@example.com")
                .passwordHash("hash")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        lenient().when(userScope.currentUser()).thenReturn(testUser);
    }

    private VisionArea visionArea(Long id) {
        return VisionArea.builder().id(id).user(testUser).code("VA-001").name("Career")
                .priority(Priority.HIGH).status(LifecycleStatus.ACTIVE).build();
    }

    private Dream dream(Long id, VisionArea area) {
        return Dream.builder().id(id).user(testUser).visionArea(area).code("D-001").title("Dream")
                .dreamType(com.visionmapping.entity.enums.DreamType.LONG_TERM).priority(Priority.HIGH)
                .status(DreamStatus.ACTIVE).build();
    }

    private Goal goal(Long id, Dream dream, WorkStatus status, BigDecimal progress, boolean manualOverride) {
        return Goal.builder().id(id).user(testUser).dream(dream).code("G-001").title("Goal")
                .priority(Priority.HIGH).status(status).progressPercent(progress)
                .manualProgressOverride(manualOverride).build();
    }

    private VisionStep step(Long id, Goal goal, WorkStatus status, BigDecimal progress, boolean complex, boolean manualOverride) {
        return VisionStep.builder().id(id).user(testUser).goal(goal).code("S-001").title("Step")
                .sequenceNumber(1).complex(complex).priority(Priority.HIGH).status(status)
                .progressPercent(progress).manualProgressOverride(manualOverride).build();
    }

    private TaskItem task(Long id, VisionStep step, WorkStatus status, BigDecimal progress) {
        return TaskItem.builder().id(id).user(testUser).step(step).code("T-001").title("Task")
                .owner("Owner").priority(Priority.HIGH).dueDate(LocalDate.now().plusDays(5))
                .status(status).progressPercent(progress).build();
    }

    // --- Progress rollup -------------------------------------------------

    @Test
    void updatingOneTaskRecalculatesStepAndGoalAsAverageOfChildren() {
        Goal goal = goal(10L, dream(1L, visionArea(1L)), WorkStatus.NOT_STARTED, BigDecimal.ZERO, false);
        VisionStep step = step(20L, goal, WorkStatus.NOT_STARTED, BigDecimal.ZERO, false, false);
        TaskItem task1 = task(30L, step, WorkStatus.IN_PROGRESS, BigDecimal.valueOf(40));
        TaskItem task2 = task(31L, step, WorkStatus.IN_PROGRESS, BigDecimal.valueOf(60));

        when(taskItemRepository.findById(30L)).thenReturn(Optional.of(task1));
        when(taskItemRepository.findByStep_IdAndUser_IdAndArchivedFalse(20L, 1L)).thenReturn(List.of(task1, task2));
        when(visionStepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(10L, 1L)).thenReturn(List.of(step));

        TaskItemResponse response = service.updateTaskStatus(30L, "COMPLETED");

        assertThat(response.progressPercent()).isEqualByComparingTo("100.00");
        assertThat(step.getProgressPercent()).isEqualByComparingTo("80.00");
        assertThat(step.getStatus()).isEqualTo(WorkStatus.NOT_STARTED);
        assertThat(goal.getProgressPercent()).isEqualByComparingTo("80.00");
        assertThat(goal.getStatus()).isEqualTo(WorkStatus.NOT_STARTED);
    }

    @Test
    void allChildTasksCompletedAutoCompletesStepAndGoal() {
        Goal goal = goal(10L, dream(1L, visionArea(1L)), WorkStatus.NOT_STARTED, BigDecimal.ZERO, false);
        VisionStep step = step(20L, goal, WorkStatus.NOT_STARTED, BigDecimal.ZERO, false, false);
        TaskItem task1 = task(30L, step, WorkStatus.IN_PROGRESS, BigDecimal.valueOf(90));
        TaskItem task2 = task(31L, step, WorkStatus.COMPLETED, BigDecimal.valueOf(100));

        when(taskItemRepository.findById(30L)).thenReturn(Optional.of(task1));
        when(taskItemRepository.findByStep_IdAndUser_IdAndArchivedFalse(20L, 1L)).thenReturn(List.of(task1, task2));
        when(visionStepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(10L, 1L)).thenReturn(List.of(step));

        service.updateTaskStatus(30L, "COMPLETED");

        assertThat(step.getStatus()).isEqualTo(WorkStatus.COMPLETED);
        assertThat(step.getProgressPercent()).isEqualByComparingTo("100.00");
        assertThat(goal.getStatus()).isEqualTo(WorkStatus.COMPLETED);
        assertThat(goal.getProgressPercent()).isEqualByComparingTo("100.00");
    }

    @Test
    void manualProgressOverrideOnStepSkipsAverageRecalculation() {
        Goal goal = goal(10L, dream(1L, visionArea(1L)), WorkStatus.NOT_STARTED, BigDecimal.ZERO, false);
        VisionStep step = step(20L, goal, WorkStatus.NOT_STARTED, BigDecimal.valueOf(42), false, true);
        TaskItem task = task(30L, step, WorkStatus.NOT_STARTED, BigDecimal.valueOf(10));

        when(taskItemRepository.findById(30L)).thenReturn(Optional.of(task));
        when(visionStepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(10L, 1L)).thenReturn(List.of(step));

        service.updateTaskStatus(30L, "IN_PROGRESS");

        assertThat(step.getProgressPercent()).isEqualByComparingTo("42.00");
        assertThat(goal.getProgressPercent()).isEqualByComparingTo("42.00");
    }

    // --- Overdue detection -------------------------------------------------

    @Test
    void dashboardCountsOverdueTasksExcludingCompletedOnes() {
        VisionStep step = step(20L, goal(10L, dream(1L, visionArea(1L)), WorkStatus.NOT_STARTED, BigDecimal.ZERO, false),
                WorkStatus.NOT_STARTED, BigDecimal.ZERO, false, false);
        TaskItem overdue = task(30L, step, WorkStatus.IN_PROGRESS, BigDecimal.ZERO);
        overdue.setDueDate(LocalDate.now().minusDays(1));
        TaskItem overdueButCompleted = task(31L, step, WorkStatus.COMPLETED, BigDecimal.valueOf(100));
        overdueButCompleted.setDueDate(LocalDate.now().minusDays(1));
        TaskItem dueThisWeek = task(32L, step, WorkStatus.NOT_STARTED, BigDecimal.ZERO);
        dueThisWeek.setDueDate(LocalDate.now().plusDays(3));

        when(visionAreaRepository.findByUser_IdAndArchivedFalse(1L)).thenReturn(List.of());
        when(dreamRepository.findByUser_IdAndArchivedFalse(1L)).thenReturn(List.of());
        when(goalRepository.findByUser_IdAndArchivedFalse(1L)).thenReturn(List.of());
        when(taskItemRepository.findByUser_IdAndArchivedFalse(1L)).thenReturn(List.of(overdue, overdueButCompleted, dueThisWeek));

        DashboardSummaryResponse summary = service.buildDashboardSummary();

        assertThat(summary.overdueTasks()).isEqualTo(1);
        assertThat(summary.completedTasks()).isEqualTo(1);
        assertThat(summary.activeTasks()).isEqualTo(2);
        assertThat(summary.tasksDueThisWeek()).isEqualTo(1);
    }

    @Test
    void dashboardAggregatesGoalStatusCountsAndAverageProgress() {
        Dream dream = dream(1L, visionArea(1L));
        Goal g1 = goal(10L, dream, WorkStatus.IN_PROGRESS, BigDecimal.valueOf(50), false);
        Goal g2 = goal(11L, dream, WorkStatus.COMPLETED, BigDecimal.valueOf(100), false);
        Goal g3 = goal(12L, dream, WorkStatus.IN_PROGRESS, BigDecimal.valueOf(30), false);

        when(visionAreaRepository.findByUser_IdAndArchivedFalse(1L)).thenReturn(List.of());
        when(dreamRepository.findByUser_IdAndArchivedFalse(1L)).thenReturn(List.of());
        when(goalRepository.findByUser_IdAndArchivedFalse(1L)).thenReturn(List.of(g1, g2, g3));
        when(taskItemRepository.findByUser_IdAndArchivedFalse(1L)).thenReturn(List.of());

        DashboardSummaryResponse summary = service.buildDashboardSummary();

        assertThat(summary.averageProgress()).isEqualByComparingTo("60.00");
        assertThat(summary.goalsByStatus()).containsEntry("IN_PROGRESS", 2L).containsEntry("COMPLETED", 1L);
        assertThat(summary.activeGoals()).isEqualTo(2);
    }

    // --- Restore pulls archived parents back with the child -----------------

    @Test
    void restoringTaskRestoresArchivedParentChain() {
        VisionArea area = visionArea(1L);
        area.setArchived(true);
        area.setStatus(LifecycleStatus.ARCHIVED);
        Dream dream = dream(1L, area);
        dream.setArchived(true);
        dream.setStatus(DreamStatus.ARCHIVED);
        Goal goal = goal(10L, dream, WorkStatus.NOT_STARTED, BigDecimal.ZERO, false);
        goal.setArchived(true);
        VisionStep step = step(20L, goal, WorkStatus.NOT_STARTED, BigDecimal.ZERO, false, false);
        step.setArchived(true);
        TaskItem task = task(30L, step, WorkStatus.NOT_STARTED, BigDecimal.ZERO);
        task.setArchived(true);
        when(taskItemRepository.findById(30L)).thenReturn(Optional.of(task));
        when(taskItemRepository.findByStep_IdAndUser_IdAndArchivedFalse(20L, 1L)).thenReturn(List.of(task));
        when(visionStepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(10L, 1L)).thenReturn(List.of(step));

        service.restoreTask(30L);

        assertThat(task.isArchived()).isFalse();
        assertThat(step.isArchived()).isFalse();
        assertThat(goal.isArchived()).isFalse();
        assertThat(dream.isArchived()).isFalse();
        assertThat(area.isArchived()).isFalse();
        // The pre-archive status is unknown, so restored parents come back Paused.
        assertThat(dream.getStatus()).isEqualTo(DreamStatus.PAUSED);
        assertThat(area.getStatus()).isEqualTo(LifecycleStatus.PAUSED);
    }

    // --- Archive impact counts only what a cascade would newly archive ------

    @Test
    void archiveImpactCountsOnlyUnarchivedDescendants() {
        VisionArea area = visionArea(1L);
        Dream dream = dream(1L, area);
        Goal goal = goal(10L, dream, WorkStatus.NOT_STARTED, BigDecimal.ZERO, false);
        VisionStep activeStep = step(20L, goal, WorkStatus.NOT_STARTED, BigDecimal.ZERO, false, false);
        VisionStep archivedStep = step(21L, goal, WorkStatus.NOT_STARTED, BigDecimal.ZERO, false, false);
        archivedStep.setArchived(true);
        TaskItem activeTask = task(30L, activeStep, WorkStatus.NOT_STARTED, BigDecimal.ZERO);
        TaskItem archivedTask = task(31L, activeStep, WorkStatus.NOT_STARTED, BigDecimal.ZERO);
        archivedTask.setArchived(true);
        when(visionAreaRepository.findById(1L)).thenReturn(Optional.of(area));
        when(dreamRepository.findByVisionArea_IdAndUser_Id(1L, 1L)).thenReturn(List.of(dream));
        when(goalRepository.findByDream_IdAndUser_Id(1L, 1L)).thenReturn(List.of(goal));
        when(visionStepRepository.findByGoal_IdAndUser_Id(10L, 1L)).thenReturn(List.of(activeStep, archivedStep));
        when(taskItemRepository.findByStep_IdAndUser_Id(20L, 1L)).thenReturn(List.of(activeTask, archivedTask));
        when(taskItemRepository.findByStep_IdAndUser_Id(21L, 1L)).thenReturn(List.of());

        var impact = service.visionAreaArchiveImpact(1L);

        assertThat(impact.dreams()).isEqualTo(1);
        assertThat(impact.goals()).isEqualTo(1);
        assertThat(impact.steps()).isEqualTo(1);
        assertThat(impact.tasks()).isEqualTo(1);
    }

    // --- Diligence checklist is all-or-none ---------------------------------

    @Test
    void partialDiligenceChecklistIsRejected() {
        com.visionmapping.dto.request.ReviewRequest request = new com.visionmapping.dto.request.ReviewRequest(
                com.visionmapping.entity.enums.ReviewType.WEEKLY, LocalDate.now(), null, null,
                "Summary", null, null, null, null, null,
                true, true, null, null, null, null);

        assertThatThrownBy(() -> service.createReview(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Answer every diligence question, or skip the whole checklist.");
    }

    @Test
    void fullOrSkippedDiligenceChecklistIsAccepted() {
        when(reviewRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        com.visionmapping.dto.request.ReviewRequest full = new com.visionmapping.dto.request.ReviewRequest(
                com.visionmapping.entity.enums.ReviewType.WEEKLY, LocalDate.now(), null, null,
                "Summary", null, null, null, null, null,
                true, false, true, true, false, "Tempo weeks slipped");
        com.visionmapping.dto.request.ReviewRequest skipped = new com.visionmapping.dto.request.ReviewRequest(
                com.visionmapping.entity.enums.ReviewType.DAILY, LocalDate.now(), null, null,
                "Summary", null, null, null, null, null,
                null, null, null, null, null, null);

        assertThat(service.createReview(full).diligenceWorkedPlan()).isFalse();
        assertThat(service.createReview(skipped).diligenceClearVision()).isNull();
    }

    // --- Blocked task requires a reason ------------------------------------

    @Test
    void creatingBlockedTaskWithoutReasonThrows() {
        VisionStep step = step(20L, goal(10L, dream(1L, visionArea(1L)), WorkStatus.NOT_STARTED, BigDecimal.ZERO, false),
                WorkStatus.NOT_STARTED, BigDecimal.ZERO, false, false);
        when(visionStepRepository.findById(20L)).thenReturn(Optional.of(step));
        when(taskItemRepository.findByUser_Id(1L)).thenReturn(List.of());

        TaskItemRequest request = new TaskItemRequest(20L, "Blocked task", null, "Owner", Priority.HIGH, null,
                LocalDate.now().plusDays(5), WorkStatus.BLOCKED, BigDecimal.TEN, null, null, "   ", null);

        assertThatThrownBy(() -> service.createTask(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Blocked tasks must include a blocker reason.");
    }

    @Test
    void creatingBlockedTaskWithReasonSucceeds() {
        VisionStep step = step(20L, goal(10L, dream(1L, visionArea(1L)), WorkStatus.NOT_STARTED, BigDecimal.ZERO, false),
                WorkStatus.NOT_STARTED, BigDecimal.ZERO, false, false);
        when(visionStepRepository.findById(20L)).thenReturn(Optional.of(step));
        when(taskItemRepository.findByUser_Id(1L)).thenReturn(List.of());
        when(taskItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskItemRepository.findByStep_IdAndUser_IdAndArchivedFalse(20L, 1L)).thenReturn(List.of());
        when(visionStepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(10L, 1L)).thenReturn(List.of());

        TaskItemRequest request = new TaskItemRequest(20L, "Blocked task", null, "Owner", Priority.HIGH, null,
                LocalDate.now().plusDays(5), WorkStatus.BLOCKED, BigDecimal.TEN, null, null, "Waiting on mentor", null);

        TaskItemResponse response = service.createTask(request);

        assertThat(response.status()).isEqualTo(WorkStatus.BLOCKED);
        assertThat(response.blockerReason()).isEqualTo("Waiting on mentor");
    }

    // --- Goal completion rule ----------------------------------------------

    @Test
    void completingGoalWithIncompleteStepsThrowsUnlessManualOverride() {
        Goal goal = goal(10L, dream(1L, visionArea(1L)), WorkStatus.IN_PROGRESS, BigDecimal.ZERO, false);
        VisionStep incompleteStep = step(20L, goal, WorkStatus.IN_PROGRESS, BigDecimal.ZERO, false, false);
        when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));
        when(visionStepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(10L, 1L)).thenReturn(List.of(incompleteStep));

        assertThatThrownBy(() -> service.updateGoalStatus(10L, "COMPLETED", false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A goal cannot be completed until all steps are completed, unless manualOverride is true.");
    }

    @Test
    void completingGoalWithManualOverrideSucceedsAndFlagsOverride() {
        Goal goal = goal(10L, dream(1L, visionArea(1L)), WorkStatus.IN_PROGRESS, BigDecimal.ZERO, false);
        VisionStep incompleteStep = step(20L, goal, WorkStatus.IN_PROGRESS, BigDecimal.ZERO, false, false);
        when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));
        lenient().when(visionStepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(10L, 1L)).thenReturn(List.of(incompleteStep));

        service.updateGoalStatus(10L, "COMPLETED", true);

        assertThat(goal.getStatus()).isEqualTo(WorkStatus.COMPLETED);
        assertThat(goal.isManualProgressOverride()).isTrue();
    }

    // --- Complex step requires at least one task ---------------------------

    @Test
    void completingComplexStepWithNoTasksThrows() {
        Goal goal = goal(10L, dream(1L, visionArea(1L)), WorkStatus.NOT_STARTED, BigDecimal.ZERO, false);
        VisionStep complexStep = step(20L, goal, WorkStatus.IN_PROGRESS, BigDecimal.ZERO, true, false);
        when(visionStepRepository.findById(20L)).thenReturn(Optional.of(complexStep));
        when(taskItemRepository.findByStep_IdAndUser_IdAndArchivedFalse(20L, 1L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.updateStepStatus(20L, "COMPLETED", false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A complex step must have at least one task before it can be completed.");
    }

    @Test
    void completingComplexStepWithATaskSucceeds() {
        Goal goal = goal(10L, dream(1L, visionArea(1L)), WorkStatus.NOT_STARTED, BigDecimal.ZERO, false);
        VisionStep complexStep = step(20L, goal, WorkStatus.IN_PROGRESS, BigDecimal.ZERO, true, false);
        TaskItem existingTask = task(30L, complexStep, WorkStatus.COMPLETED, BigDecimal.valueOf(100));
        when(visionStepRepository.findById(20L)).thenReturn(Optional.of(complexStep));
        when(taskItemRepository.findByStep_IdAndUser_IdAndArchivedFalse(20L, 1L)).thenReturn(List.of(existingTask));
        when(visionStepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(10L, 1L)).thenReturn(List.of(complexStep));

        service.updateStepStatus(20L, "COMPLETED", false);

        assertThat(complexStep.getStatus()).isEqualTo(WorkStatus.COMPLETED);
    }

    // --- Progress normalization ----------------------------------------------

    @Test
    void progressPercentBelowZeroThrows() {
        VisionStep step = step(20L, goal(10L, dream(1L, visionArea(1L)), WorkStatus.NOT_STARTED, BigDecimal.ZERO, false),
                WorkStatus.NOT_STARTED, BigDecimal.ZERO, false, false);
        when(visionStepRepository.findById(20L)).thenReturn(Optional.of(step));
        lenient().when(taskItemRepository.findByUser_Id(1L)).thenReturn(List.of());

        TaskItemRequest request = new TaskItemRequest(20L, "Task", null, "Owner", Priority.HIGH, null,
                LocalDate.now().plusDays(5), WorkStatus.NOT_STARTED, BigDecimal.valueOf(-5), null, null, null, null);

        assertThatThrownBy(() -> service.createTask(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Progress percent must be between 0 and 100.");
    }

    @Test
    void progressPercentAboveOneHundredThrows() {
        VisionStep step = step(20L, goal(10L, dream(1L, visionArea(1L)), WorkStatus.NOT_STARTED, BigDecimal.ZERO, false),
                WorkStatus.NOT_STARTED, BigDecimal.ZERO, false, false);
        when(visionStepRepository.findById(20L)).thenReturn(Optional.of(step));
        lenient().when(taskItemRepository.findByUser_Id(1L)).thenReturn(List.of());

        TaskItemRequest request = new TaskItemRequest(20L, "Task", null, "Owner", Priority.HIGH, null,
                LocalDate.now().plusDays(5), WorkStatus.NOT_STARTED, BigDecimal.valueOf(150), null, null, null, null);

        assertThatThrownBy(() -> service.createTask(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Progress percent must be between 0 and 100.");
    }

    // --- User data scoping ---------------------------------------------------

    @Test
    void fetchingAnotherUsersTaskThrowsResourceNotFound() {
        AppUser otherUser = AppUser.builder().id(2L).fullName("Other").email("other@example.com")
                .passwordHash("hash").role(UserRole.USER).status(UserStatus.ACTIVE).build();
        VisionStep step = VisionStep.builder().id(20L).user(otherUser).goal(goal(10L, dream(1L, visionArea(1L)),
                        WorkStatus.NOT_STARTED, BigDecimal.ZERO, false))
                .code("S-001").title("Step").sequenceNumber(1).complex(false).priority(Priority.HIGH)
                .status(WorkStatus.NOT_STARTED).progressPercent(BigDecimal.ZERO).build();
        TaskItem foreignTask = TaskItem.builder().id(30L).user(otherUser).step(step).code("T-001").title("Task")
                .owner("Owner").priority(Priority.HIGH).dueDate(LocalDate.now().plusDays(5))
                .status(WorkStatus.NOT_STARTED).progressPercent(BigDecimal.ZERO).build();

        when(taskItemRepository.findById(30L)).thenReturn(Optional.of(foreignTask));

        assertThatThrownBy(() -> service.getTask(30L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Task not found: 30");
    }

    // --- prepareTask completion side effects ---------------------------------

    @Test
    void movingTaskToCompletedSetsProgressAndCompletedAt() {
        VisionStep step = step(20L, goal(10L, dream(1L, visionArea(1L)), WorkStatus.NOT_STARTED, BigDecimal.ZERO, false),
                WorkStatus.NOT_STARTED, BigDecimal.ZERO, false, false);
        when(visionStepRepository.findById(20L)).thenReturn(Optional.of(step));
        when(taskItemRepository.findByUser_Id(1L)).thenReturn(List.of());
        when(taskItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskItemRepository.findByStep_IdAndUser_IdAndArchivedFalse(20L, 1L)).thenReturn(List.of());
        when(visionStepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(10L, 1L)).thenReturn(List.of());

        TaskItemRequest request = new TaskItemRequest(20L, "Task", null, "Owner", Priority.HIGH, null,
                LocalDate.now().plusDays(5), WorkStatus.COMPLETED, BigDecimal.valueOf(40), null, null, null, null);

        TaskItemResponse response = service.createTask(request);

        assertThat(response.progressPercent()).isEqualByComparingTo("100.00");
        assertThat(response.completedAt()).isNotNull();
    }

    @Test
    void movingTaskAwayFromCompletedClearsCompletedAt() {
        VisionStep step = step(20L, goal(10L, dream(1L, visionArea(1L)), WorkStatus.NOT_STARTED, BigDecimal.ZERO, false),
                WorkStatus.NOT_STARTED, BigDecimal.ZERO, false, false);
        TaskItem completedTask = task(30L, step, WorkStatus.COMPLETED, BigDecimal.valueOf(100));
        completedTask.setCompletedAt(java.time.Instant.now());

        when(taskItemRepository.findById(30L)).thenReturn(Optional.of(completedTask));
        when(taskItemRepository.findByStep_IdAndUser_IdAndArchivedFalse(20L, 1L)).thenReturn(List.of(completedTask));
        when(visionStepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(10L, 1L)).thenReturn(List.of(step));

        TaskItemResponse response = service.updateTaskStatus(30L, "IN_PROGRESS");

        assertThat(response.completedAt()).isNull();
    }

    // --- Delete / archive ---------------------------------------------------

    @Test
    void archivingGoalSetsArchivedFlagWithoutChangingStatusAndCascadesToChildren() {
        Goal goal = goal(10L, dream(1L, visionArea(1L)), WorkStatus.IN_PROGRESS, BigDecimal.valueOf(50), false);
        VisionStep step = step(20L, goal, WorkStatus.IN_PROGRESS, BigDecimal.valueOf(50), false, false);
        TaskItem task = task(30L, step, WorkStatus.IN_PROGRESS, BigDecimal.valueOf(50));

        when(goalRepository.findById(10L)).thenReturn(Optional.of(goal));
        when(visionStepRepository.findByGoal_IdAndUser_Id(10L, 1L)).thenReturn(List.of(step));
        when(taskItemRepository.findByStep_IdAndUser_Id(20L, 1L)).thenReturn(List.of(task));

        service.archiveGoal(10L);

        assertThat(goal.isArchived()).isTrue();
        assertThat(goal.getStatus()).isEqualTo(WorkStatus.IN_PROGRESS);
        assertThat(step.isArchived()).isTrue();
        assertThat(task.isArchived()).isTrue();
    }

    @Test
    void archivingStepCascadesToTasksAndRecalculatesParentGoal() {
        Goal goal = goal(10L, dream(1L, visionArea(1L)), WorkStatus.IN_PROGRESS, BigDecimal.valueOf(50), false);
        VisionStep step = step(20L, goal, WorkStatus.IN_PROGRESS, BigDecimal.valueOf(50), false, false);
        VisionStep otherStep = step(21L, goal, WorkStatus.IN_PROGRESS, BigDecimal.valueOf(20), false, false);
        TaskItem task = task(30L, step, WorkStatus.IN_PROGRESS, BigDecimal.valueOf(50));

        when(visionStepRepository.findById(20L)).thenReturn(Optional.of(step));
        when(taskItemRepository.findByStep_IdAndUser_Id(20L, 1L)).thenReturn(List.of(task));
        when(visionStepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(10L, 1L)).thenReturn(List.of(otherStep));

        service.archiveStep(20L);

        assertThat(step.isArchived()).isTrue();
        assertThat(task.isArchived()).isTrue();
        assertThat(goal.getProgressPercent()).isEqualByComparingTo("20.00");
    }

    @Test
    void archivingTaskExcludesItFromStepAndGoalProgressRecalculation() {
        Goal goal = goal(10L, dream(1L, visionArea(1L)), WorkStatus.IN_PROGRESS, BigDecimal.ZERO, false);
        VisionStep step = step(20L, goal, WorkStatus.IN_PROGRESS, BigDecimal.ZERO, false, false);
        TaskItem task1 = task(30L, step, WorkStatus.IN_PROGRESS, BigDecimal.valueOf(40));
        TaskItem task2 = task(31L, step, WorkStatus.IN_PROGRESS, BigDecimal.valueOf(80));

        when(taskItemRepository.findById(30L)).thenReturn(Optional.of(task1));
        when(taskItemRepository.findByStep_IdAndUser_IdAndArchivedFalse(20L, 1L)).thenReturn(List.of(task2));
        when(visionStepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(10L, 1L)).thenReturn(List.of(step));

        service.archiveTask(30L);

        assertThat(task1.isArchived()).isTrue();
        assertThat(step.getProgressPercent()).isEqualByComparingTo("80.00");
        assertThat(goal.getProgressPercent()).isEqualByComparingTo("80.00");
    }

    @Test
    void archivingPartnerSetsArchivedFlagWithoutOverwritingStatus() {
        Partner partner = Partner.builder().id(40L).user(testUser).code("P-001").name("Mentor")
                .supportType(PartnerSupportType.MENTOR).status(PartnerStatus.ACTIVE).build();
        when(partnerRepository.findById(40L)).thenReturn(Optional.of(partner));

        service.archivePartner(40L);

        assertThat(partner.isArchived()).isTrue();
        assertThat(partner.getStatus()).isEqualTo(PartnerStatus.ACTIVE);
    }

    // --- Dashboard characterization (pins behavior before the Clean Code split) --

    @Test
    void dashboardAveragesGoalProgressPerVisionAreaSortedAscending() {
        VisionArea career = visionArea(1L);
        VisionArea health = visionArea(2L);
        health.setName("Health");
        Dream careerDream = dream(1L, career);
        Dream healthDream = dream(2L, health);
        Goal careerGoalA = goal(10L, careerDream, WorkStatus.IN_PROGRESS, BigDecimal.valueOf(80), false);
        Goal careerGoalB = goal(11L, careerDream, WorkStatus.IN_PROGRESS, BigDecimal.valueOf(40), false);
        Goal healthGoal = goal(12L, healthDream, WorkStatus.IN_PROGRESS, BigDecimal.valueOf(20), false);

        when(visionAreaRepository.findByUser_IdAndArchivedFalse(1L)).thenReturn(List.of(career, health));
        when(dreamRepository.findByUser_IdAndArchivedFalse(1L)).thenReturn(List.of(careerDream, healthDream));
        when(goalRepository.findByUser_IdAndArchivedFalse(1L)).thenReturn(List.of(careerGoalA, careerGoalB, healthGoal));
        when(taskItemRepository.findByUser_IdAndArchivedFalse(1L)).thenReturn(List.of());

        DashboardSummaryResponse summary = service.buildDashboardSummary();

        // Health (20) sorts before Career (avg of 80 and 40 = 60), rounded to whole percent.
        assertThat(summary.visionAreaProgress()).hasSize(2);
        assertThat(summary.visionAreaProgress().get(0).name()).isEqualTo("Health");
        assertThat(summary.visionAreaProgress().get(0).progress()).isEqualByComparingTo("20");
        assertThat(summary.visionAreaProgress().get(1).name()).isEqualTo("Career");
        assertThat(summary.visionAreaProgress().get(1).progress()).isEqualByComparingTo("60");
        assertThat(summary.dreamsByVisionArea()).containsEntry("Career", 1L).containsEntry("Health", 1L);
    }

    @Test
    void dashboardCountsMoonshotGoalsAndBlockedTasks() {
        Dream dream = dream(1L, visionArea(1L));
        Goal moonshot = goal(10L, dream, WorkStatus.IN_PROGRESS, BigDecimal.valueOf(10), false);
        moonshot.setMoonshot(true);
        Goal standard = goal(11L, dream, WorkStatus.IN_PROGRESS, BigDecimal.valueOf(10), false);
        VisionStep step = step(20L, moonshot, WorkStatus.NOT_STARTED, BigDecimal.ZERO, false, false);
        TaskItem blocked = task(30L, step, WorkStatus.BLOCKED, BigDecimal.ZERO);

        when(goalRepository.findByUser_IdAndArchivedFalse(1L)).thenReturn(List.of(moonshot, standard));
        when(taskItemRepository.findByUser_IdAndArchivedFalse(1L)).thenReturn(List.of(blocked));

        DashboardSummaryResponse summary = service.buildDashboardSummary();

        assertThat(summary.moonshotGoals()).isEqualTo(1);
        assertThat(summary.blockedTasks()).isEqualTo(1);
    }

    @Test
    void dashboardTopPriorityTasksExcludeCompletedAndCapAtFive() {
        VisionStep step = step(20L, goal(10L, dream(1L, visionArea(1L)), WorkStatus.NOT_STARTED, BigDecimal.ZERO, false),
                WorkStatus.NOT_STARTED, BigDecimal.ZERO, false, false);
        List<TaskItem> tasks = new java.util.ArrayList<>();
        for (long i = 0; i < 6; i++) {
            tasks.add(task(30L + i, step, WorkStatus.IN_PROGRESS, BigDecimal.ZERO));
        }
        TaskItem completed = task(40L, step, WorkStatus.COMPLETED, BigDecimal.valueOf(100));
        completed.setPriority(Priority.CRITICAL);
        tasks.add(completed);

        when(taskItemRepository.findByUser_IdAndArchivedFalse(1L)).thenReturn(tasks);

        DashboardSummaryResponse summary = service.buildDashboardSummary();

        assertThat(summary.priorityTasks()).hasSize(5);
        assertThat(summary.priorityTasks()).noneMatch(task -> task.status() == WorkStatus.COMPLETED);
    }

    @Test
    void dashboardProgressTrendCarriesLatestLoggedAverageToTheFinalWeek() {
        VisionStep step = step(20L, goal(10L, dream(1L, visionArea(1L)), WorkStatus.IN_PROGRESS, BigDecimal.valueOf(40), false),
                WorkStatus.IN_PROGRESS, BigDecimal.valueOf(40), false, false);
        TaskItem task = task(30L, step, WorkStatus.IN_PROGRESS, BigDecimal.valueOf(40));
        ProgressLog log = ProgressLog.builder()
                .id(1L).user(testUser).relatedTask(task)
                .progressPercentBefore(BigDecimal.ZERO).progressPercentAfter(BigDecimal.valueOf(40))
                .loggedAt(java.time.Instant.now().minus(java.time.Duration.ofDays(2)))
                .build();

        when(progressLogRepository.findByUser_IdAndArchivedFalse(1L)).thenReturn(List.of(log));

        DashboardSummaryResponse summary = service.buildDashboardSummary();

        assertThat(summary.progressTrend()).isNotEmpty();
        assertThat(summary.progressTrend().get(summary.progressTrend().size() - 1).progress())
                .isEqualByComparingTo("40.00");
    }
}
