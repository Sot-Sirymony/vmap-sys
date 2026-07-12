package com.visionmapping.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

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
import com.visionmapping.service.support.ArchiveCascade;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.service.support.PermanentDeleteCascade;
import com.visionmapping.util.UserScope;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Goal completion rule (all steps done unless overridden) and archive cascade
 * for {@link GoalService}.
 */
@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

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

    private GoalService service;
    private AppUser testUser;

    @BeforeEach
    void setUp() {
        EntityLookup lookup = new EntityLookup(userScope, visionAreaRepository, dreamRepository, goalRepository,
                visionStepRepository, taskItemRepository, partnerRepository, communicationMessageRepository,
                reviewRepository, obstacleRepository, progressLogRepository);
        ArchiveCascade archiveCascade = new ArchiveCascade(lookup, dreamRepository, goalRepository,
                visionStepRepository, taskItemRepository);
        PermanentDeleteCascade permanentDeleteCascade = new PermanentDeleteCascade(lookup, visionAreaRepository,
                dreamRepository, goalRepository, visionStepRepository, taskItemRepository, partnerRepository,
                communicationMessageRepository, reviewRepository, obstacleRepository, progressLogRepository);
        service = new GoalService(lookup, archiveCascade, permanentDeleteCascade,
                new VisionMappingMapper(), goalRepository, visionStepRepository);
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
                .owner("Owner").priority(Priority.HIGH).dueDate(java.time.LocalDate.now().plusDays(5))
                .status(status).progressPercent(progress).build();
    }

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
}
