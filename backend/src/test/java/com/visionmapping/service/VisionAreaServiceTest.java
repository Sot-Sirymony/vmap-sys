package com.visionmapping.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Archive-impact counting for {@link VisionAreaService}: only descendants that
 * are not already archived are counted, so the confirmation never over-reports.
 */
@ExtendWith(MockitoExtension.class)
class VisionAreaServiceTest {

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

    private VisionAreaService service;
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
        service = new VisionAreaService(lookup, archiveCascade, permanentDeleteCascade,
                new VisionMappingMapper(), visionAreaRepository);
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

    private Goal goal(Long id, Dream dream) {
        return Goal.builder().id(id).user(testUser).dream(dream).code("G-001").title("Goal")
                .priority(Priority.HIGH).status(WorkStatus.NOT_STARTED).progressPercent(BigDecimal.ZERO)
                .manualProgressOverride(false).build();
    }

    private VisionStep step(Long id, Goal goal) {
        return VisionStep.builder().id(id).user(testUser).goal(goal).code("S-001").title("Step")
                .sequenceNumber(1).complex(false).priority(Priority.HIGH).status(WorkStatus.NOT_STARTED)
                .progressPercent(BigDecimal.ZERO).manualProgressOverride(false).build();
    }

    private TaskItem task(Long id, VisionStep step) {
        return TaskItem.builder().id(id).user(testUser).step(step).code("T-001").title("Task")
                .owner("Owner").priority(Priority.HIGH).dueDate(LocalDate.now().plusDays(5))
                .status(WorkStatus.NOT_STARTED).progressPercent(BigDecimal.ZERO).build();
    }

    @Test
    void archiveImpactCountsOnlyUnarchivedDescendants() {
        VisionArea area = visionArea(1L);
        Dream dream = dream(1L, area);
        Goal goal = goal(10L, dream);
        VisionStep activeStep = step(20L, goal);
        VisionStep archivedStep = step(21L, goal);
        archivedStep.setArchived(true);
        TaskItem activeTask = task(30L, activeStep);
        TaskItem archivedTask = task(31L, activeStep);
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
}
