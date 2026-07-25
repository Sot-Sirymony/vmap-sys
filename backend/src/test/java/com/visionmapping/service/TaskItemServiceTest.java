package com.visionmapping.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.visionmapping.dto.request.TaskItemRequest;
import com.visionmapping.dto.response.TaskItemResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionArea;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.DreamStatus;
import com.visionmapping.entity.enums.DreamType;
import com.visionmapping.entity.enums.LifecycleStatus;
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
import com.visionmapping.repository.IdealPartnerProfileRepository;
import com.visionmapping.repository.ObstacleRepository;
import com.visionmapping.repository.PartnerRepository;
import com.visionmapping.repository.ProgressLogRepository;
import com.visionmapping.repository.ReviewRepository;
import com.visionmapping.repository.TaskItemRepository;
import com.visionmapping.repository.VisionAreaRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.service.support.ArchiveCascade;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.service.support.PermanentDeleteCascade;
import com.visionmapping.service.support.ProgressCalculator;
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
 * Progress roll-up, blocked/overdue rules, and archive/restore behavior for
 * {@link TaskItemService}, driven through mocked repositories over the real
 * ProgressCalculator and cascade helpers.
 */
@ExtendWith(MockitoExtension.class)
class TaskItemServiceTest {

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
    @Mock private IdealPartnerProfileRepository idealPartnerProfileRepository;

    private TaskItemService service;
    private AppUser testUser;

    @BeforeEach
    void setUp() {
        EntityLookup lookup = new EntityLookup(userScope, visionAreaRepository, dreamRepository, goalRepository,
                visionStepRepository, taskItemRepository, partnerRepository, communicationMessageRepository,
                reviewRepository, obstacleRepository, progressLogRepository);
        ProgressCalculator progress = new ProgressCalculator(visionStepRepository, taskItemRepository);
        ArchiveCascade archiveCascade = new ArchiveCascade(lookup, dreamRepository, goalRepository,
                visionStepRepository, taskItemRepository, idealPartnerProfileRepository);
        PermanentDeleteCascade permanentDeleteCascade = new PermanentDeleteCascade(lookup, visionAreaRepository,
                dreamRepository, goalRepository, visionStepRepository, taskItemRepository, partnerRepository,
                communicationMessageRepository, reviewRepository, obstacleRepository, progressLogRepository, idealPartnerProfileRepository);
        service = new TaskItemService(lookup, progress, archiveCascade, permanentDeleteCascade,
                new VisionMappingMapper(), taskItemRepository, progressLogRepository, Clock.systemDefaultZone());
        testUser = AppUser.builder().id(1L).fullName("Test User").email("test@example.com")
                .passwordHash("hash").role(UserRole.USER).status(UserStatus.ACTIVE).build();
        lenient().when(userScope.currentUser()).thenReturn(testUser);
    }

    private VisionArea visionArea(Long id) {
        return VisionArea.builder().id(id).user(testUser).code("VA-001").name("Career")
                .priority(Priority.HIGH).status(LifecycleStatus.ACTIVE).build();
    }

    private Dream dream(Long id, VisionArea area) {
        return Dream.builder().id(id).user(testUser).visionArea(area).code("D-001").title("Dream")
                .dreamType(DreamType.LONG_TERM).priority(Priority.HIGH).status(DreamStatus.ACTIVE).build();
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

    @Test
    void creatingBlockedTaskWithoutReasonThrows() {
        VisionStep step = step(20L, goal(10L, dream(1L, visionArea(1L)), WorkStatus.NOT_STARTED, BigDecimal.ZERO, false),
                WorkStatus.NOT_STARTED, BigDecimal.ZERO, false, false);
        when(visionStepRepository.findById(20L)).thenReturn(Optional.of(step));
        when(taskItemRepository.findByUser_Id(1L)).thenReturn(List.of());

        TaskItemRequest request = new TaskItemRequest(20L, "Blocked task", null, "Owner", Priority.HIGH, null,
                LocalDate.now().plusDays(5), WorkStatus.BLOCKED, BigDecimal.TEN, null, null, "   ", null, null);

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
                LocalDate.now().plusDays(5), WorkStatus.BLOCKED, BigDecimal.TEN, null, null, "Waiting on mentor", null, null);

        TaskItemResponse response = service.createTask(request);

        assertThat(response.status()).isEqualTo(WorkStatus.BLOCKED);
        assertThat(response.blockerReason()).isEqualTo("Waiting on mentor");
    }

    @Test
    void progressPercentBelowZeroThrows() {
        VisionStep step = step(20L, goal(10L, dream(1L, visionArea(1L)), WorkStatus.NOT_STARTED, BigDecimal.ZERO, false),
                WorkStatus.NOT_STARTED, BigDecimal.ZERO, false, false);
        when(visionStepRepository.findById(20L)).thenReturn(Optional.of(step));
        lenient().when(taskItemRepository.findByUser_Id(1L)).thenReturn(List.of());

        TaskItemRequest request = new TaskItemRequest(20L, "Task", null, "Owner", Priority.HIGH, null,
                LocalDate.now().plusDays(5), WorkStatus.NOT_STARTED, BigDecimal.valueOf(-5), null, null, null, null, null);

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
                LocalDate.now().plusDays(5), WorkStatus.NOT_STARTED, BigDecimal.valueOf(150), null, null, null, null, null);

        assertThatThrownBy(() -> service.createTask(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Progress percent must be between 0 and 100.");
    }

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
                LocalDate.now().plusDays(5), WorkStatus.COMPLETED, BigDecimal.valueOf(40), null, null, null, null, null);

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
}
