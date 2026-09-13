package com.visionmapping.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.VisionArea;
import com.visionmapping.entity.enums.DreamStatus;
import com.visionmapping.entity.enums.DreamType;
import com.visionmapping.entity.enums.LifecycleStatus;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.ScheduleMode;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * BR-40 (FR-51): a dream's target date must not precede its latest active
 * goal's target date, unless the dream is set to a fixed top-down deadline.
 */
@ExtendWith(MockitoExtension.class)
class DreamServiceTest {

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
    @Mock private com.visionmapping.repository.IdealPartnerProfileRepository idealPartnerProfileRepository;

    private DreamService service;
    private AppUser testUser;

    @BeforeEach
    void setUp() {
        EntityLookup lookup = new EntityLookup(userScope, visionAreaRepository, dreamRepository, goalRepository,
                visionStepRepository, taskItemRepository, partnerRepository, communicationMessageRepository,
                reviewRepository, obstacleRepository, progressLogRepository);
        ArchiveCascade archiveCascade = new ArchiveCascade(lookup, dreamRepository, goalRepository,
                visionStepRepository, taskItemRepository, idealPartnerProfileRepository);
        PermanentDeleteCascade permanentDeleteCascade = new PermanentDeleteCascade(lookup, visionAreaRepository,
                dreamRepository, goalRepository, visionStepRepository, taskItemRepository, partnerRepository,
                communicationMessageRepository, reviewRepository, obstacleRepository, progressLogRepository, idealPartnerProfileRepository);
        service = new DreamService(lookup, archiveCascade, permanentDeleteCascade,
                new VisionMappingMapper(), dreamRepository, goalRepository);
        testUser = AppUser.builder().id(1L).fullName("Test User").email("test@example.com")
                .passwordHash("hash").role(UserRole.USER).status(UserStatus.ACTIVE).build();
        lenient().when(userScope.currentUser()).thenReturn(testUser);
    }

    private VisionArea visionArea(Long id) {
        return VisionArea.builder().id(id).user(testUser).code("VA-001").name("Career")
                .priority(Priority.HIGH).status(LifecycleStatus.ACTIVE).build();
    }

    private Dream dream(Long id, LocalDate targetDate, ScheduleMode scheduleMode) {
        return Dream.builder().id(id).user(testUser).visionArea(visionArea(1L)).code("D-001").title("Dream")
                .dreamType(DreamType.LONG_TERM).priority(Priority.HIGH).status(DreamStatus.ACTIVE)
                .targetDate(targetDate).scheduleMode(scheduleMode).build();
    }

    private Goal goal(Long id, Dream dream, LocalDate targetDate) {
        return Goal.builder().id(id).user(testUser).dream(dream).code("G-001").title("Goal")
                .priority(Priority.HIGH).status(WorkStatus.IN_PROGRESS).progressPercent(BigDecimal.ZERO)
                .targetDate(targetDate).build();
    }

    private com.visionmapping.dto.request.DreamRequest requestFrom(Dream dream) {
        return new com.visionmapping.dto.request.DreamRequest(dream.getVisionArea().getId(), dream.getTitle(),
                dream.getDescription(), dream.getWhyImportant(), dream.getSuccessDefinition(), dream.getDreamType(),
                dream.getPriority(), dream.getTargetDate(), dream.getStatus(), dream.isMoonshot(),
                dream.getMoonshotVision(), dream.getScheduleMode());
    }

    @Test
    void bottomUpDreamEarlierThanGoalTargetDateThrows() {
        Dream dream = dream(1L, LocalDate.of(2026, 1, 1), ScheduleMode.BOTTOM_UP);
        Goal goal = goal(10L, dream, LocalDate.of(2026, 6, 1));
        when(dreamRepository.findById(1L)).thenReturn(Optional.of(dream));
        when(visionAreaRepository.findById(dream.getVisionArea().getId())).thenReturn(Optional.of(dream.getVisionArea()));
        when(goalRepository.findByDream_IdAndUser_IdAndArchivedFalse(1L, 1L)).thenReturn(List.of(goal));

        assertThatThrownBy(() -> service.updateDream(1L, requestFrom(dream)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("earlier than one of its goals' target date");
    }

    @Test
    void bottomUpDreamOnOrAfterGoalTargetDateSucceeds() {
        Dream dream = dream(1L, LocalDate.of(2026, 6, 1), ScheduleMode.BOTTOM_UP);
        Goal goal = goal(10L, dream, LocalDate.of(2026, 6, 1));
        when(dreamRepository.findById(1L)).thenReturn(Optional.of(dream));
        when(visionAreaRepository.findById(dream.getVisionArea().getId())).thenReturn(Optional.of(dream.getVisionArea()));
        lenient().when(goalRepository.findByDream_IdAndUser_IdAndArchivedFalse(1L, 1L)).thenReturn(List.of(goal));

        var response = service.updateDream(1L, requestFrom(dream));

        assertThat(response.scheduleOverrun()).isFalse();
    }

    @Test
    void topDownFixedDreamEarlierThanGoalTargetDateSavesWithOverrunFlagged() {
        Dream dream = dream(1L, LocalDate.of(2026, 1, 1), ScheduleMode.TOP_DOWN_FIXED);
        Goal goal = goal(10L, dream, LocalDate.of(2026, 6, 1));
        when(dreamRepository.findById(1L)).thenReturn(Optional.of(dream));
        when(visionAreaRepository.findById(dream.getVisionArea().getId())).thenReturn(Optional.of(dream.getVisionArea()));
        lenient().when(goalRepository.findByDream_IdAndUser_IdAndArchivedFalse(1L, 1L)).thenReturn(List.of(goal));

        var response = service.updateDream(1L, requestFrom(dream));

        assertThat(response.scheduleOverrun()).isTrue();
        assertThat(response.scheduleOverrunDetail()).contains("2026-06-01").contains("2026-01-01");
    }

    @Test
    void goalWithNoTargetDateNeverBlocksDream() {
        Dream dream = dream(1L, LocalDate.of(2026, 1, 1), ScheduleMode.BOTTOM_UP);
        Goal goalWithNoDate = goal(10L, dream, null);
        when(dreamRepository.findById(1L)).thenReturn(Optional.of(dream));
        when(visionAreaRepository.findById(dream.getVisionArea().getId())).thenReturn(Optional.of(dream.getVisionArea()));
        lenient().when(goalRepository.findByDream_IdAndUser_IdAndArchivedFalse(1L, 1L)).thenReturn(List.of(goalWithNoDate));

        var response = service.updateDream(1L, requestFrom(dream));

        assertThat(response.scheduleOverrun()).isFalse();
    }
}
