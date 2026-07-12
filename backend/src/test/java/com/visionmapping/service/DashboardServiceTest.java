package com.visionmapping.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.visionmapping.dto.response.DashboardSummaryResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.ProgressLog;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionArea;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.DreamStatus;
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
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.service.support.ProgressCalculator;
import com.visionmapping.util.UserScope;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link DashboardService}'s aggregation: overdue/blocked/moonshot
 * counts, per-area progress ordering, top-priority selection, and the progress
 * trend. Repositories are mocked; Mockito returns empty lists for the ones a
 * given case does not stub.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

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

    private DashboardService service;
    private AppUser testUser;

    @BeforeEach
    void setUp() {
        EntityLookup lookup = new EntityLookup(userScope, visionAreaRepository, dreamRepository, goalRepository,
                visionStepRepository, taskItemRepository, partnerRepository, communicationMessageRepository,
                reviewRepository, obstacleRepository, progressLogRepository);
        ProgressCalculator progress = new ProgressCalculator(visionStepRepository, taskItemRepository);
        service = new DashboardService(lookup, progress, new VisionMappingMapper(), visionAreaRepository,
                dreamRepository, goalRepository, taskItemRepository, partnerRepository,
                reviewRepository, obstacleRepository, progressLogRepository, Clock.systemDefaultZone());

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
