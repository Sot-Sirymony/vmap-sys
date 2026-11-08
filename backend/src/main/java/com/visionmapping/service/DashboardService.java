package com.visionmapping.service;

import com.visionmapping.config.CacheConfig;
import com.visionmapping.dto.response.DashboardSummaryResponse;
import com.visionmapping.dto.response.GratitudeEntryResponse;
import com.visionmapping.dto.response.TaskItemResponse;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.Obstacle;
import com.visionmapping.entity.PlanLinked;
import com.visionmapping.entity.Review;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionArea;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.DreamStatus;
import com.visionmapping.entity.enums.EnergyDemand;
import com.visionmapping.entity.enums.LifecycleStatus;
import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.entity.enums.ReviewType;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.DreamRepository;
import com.visionmapping.repository.GoalRepository;
import com.visionmapping.repository.GratitudeEntryRepository;
import com.visionmapping.repository.ObstacleRepository;
import com.visionmapping.repository.PartnerRepository;
import com.visionmapping.repository.ProgressLogRepository;
import com.visionmapping.repository.ReviewRepository;
import com.visionmapping.repository.TaskItemRepository;
import com.visionmapping.repository.VisionAreaRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.service.support.ProgressCalculator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the single dashboard payload: the KPI counts, the per-status and
 * per-area breakdowns and the top-priority task list. Two package-private
 * collaborators build the larger parts: {@link DashboardTrend} (the
 * twelve-week progress trend) and {@link DashboardAttention} (the method's
 * rules checked against the records). Split in BRD 13 EJ-FR-29.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    static final int TREND_WEEKS = 12;
    static final int DAYS_PER_WEEK = 7;
    private static final int TOP_PRIORITY_TASK_LIMIT = 5;
    private static final int RECENT_GRATITUDE_ENTRY_LIMIT = 3;
    private static final int KPI_PROGRESS_SCALE = 2;
    private static final int AREA_PROGRESS_SCALE = 0;

    private final EntityLookup lookup;
    private final ProgressCalculator progress;
    private final VisionMappingMapper mapper;
    private final VisionAreaRepository visionAreaRepository;
    private final DreamRepository dreamRepository;
    private final GoalRepository goalRepository;
    private final VisionStepRepository visionStepRepository;
    private final TaskItemRepository taskItemRepository;
    private final PartnerRepository partnerRepository;
    private final ReviewRepository reviewRepository;
    private final ObstacleRepository obstacleRepository;
    private final ProgressLogRepository progressLogRepository;
    private final GratitudeEntryRepository gratitudeEntryRepository;
    private final Clock clock;


    public DashboardSummaryResponse buildDashboardSummary() {
        return buildDashboardSummary(null, null, null);
    }

    public DashboardSummaryResponse buildDashboardSummary(@Nullable Long visionAreaId) {
        return buildDashboardSummary(visionAreaId, null, null);
    }

    /**
     * @param visionAreaId scope every number to one area, or null for the whole
     *                     portfolio. Scoping happens here rather than in the
     *                     browser because what the client receives are already
     *                     sums — you cannot re-filter a total by area without the
     *                     rows behind it.
     * @param periodStart  start of the window (inclusive) for the two time-based
     *                     metrics — tasks due in the period and completed in the
     *                     period. Null defaults to the first of the current month.
     * @param periodEnd    end of that window (inclusive). Null defaults to the
     *                     last of the current month.
     */
    @Cacheable(CacheConfig.DASHBOARD_CACHE)
    public DashboardSummaryResponse buildDashboardSummary(@Nullable Long visionAreaId, @Nullable LocalDate periodStart, @Nullable LocalDate periodEnd) {
        LocalDate today = LocalDate.now(clock);
        DashboardData data = loadDashboardData(visionAreaId, today);
        LocalDate weekEnd = today.plusDays(DAYS_PER_WEEK);
        // The selectable window for the two time-based tiles. Defaults to the
        // current month when the caller passes nothing.
        LocalDate periodFrom = periodStart != null ? periodStart : today.withDayOfMonth(1);
        LocalDate periodTo = periodEnd != null ? periodEnd : today.withDayOfMonth(today.lengthOfMonth());

        return DashboardSummaryResponse.builder()
                .totalVisionAreas(data.areas().size())
                .activeDreams(data.dreams().stream().filter(dream -> dream.getStatus() == DreamStatus.ACTIVE).count())
                .activeGoals(data.goals().stream().filter(DashboardService::isOpenGoal).count())
                .activeTasks(data.tasks().stream().filter(task -> task.getStatus() != WorkStatus.COMPLETED).count())
                .completedTasks(data.tasks().stream().filter(task -> task.getStatus() == WorkStatus.COMPLETED).count())
                .overdueTasks(data.tasks().stream().filter(task -> progress.isOverdue(task, today)).count())
                .blockedTasks(data.tasks().stream().filter(task -> task.getStatus() == WorkStatus.BLOCKED).count())
                .averageProgress(averageGoalProgress(data.goals(), KPI_PROGRESS_SCALE))
                .tasksDueThisWeek(countTasksDueBetween(data.tasks(), today, weekEnd))
                .tasksDueInPeriod(countTasksDueBetween(data.tasks(), periodFrom, periodTo))
                .completedTasksInPeriod(countTasksCompletedBetween(data.tasks(), periodFrom, periodTo))
                .goalsByStatus(countBy(data.goals(), goal -> goal.getStatus().name()))
                .dreamsByVisionArea(countBy(data.dreams(), dream -> dream.getVisionArea().getName()))
                .tasksByStatus(countBy(data.tasks(), task -> task.getStatus().name()))
                .tasksByPriority(countBy(data.tasks(), task -> task.getPriority().name()))
                .activeObstaclesByType(activeObstaclesByType(data.obstacles()))
                .partnersByStatus(countBy(data.partners(), partner -> partner.getStatus().name()))
                .reviewCadence(reviewCadence(data.reviews()))
                .progressTrend(DashboardTrend.build(data.progressLogs(), today, clock.getZone()))
                .visionAreaProgress(buildAreaProgress(data.areas(), data.goals()))
                .priorityTasks(topPriorityTasks(data.tasks()))
                .weeksWithDiligence(countWeeksWithDiligence(data.reviews(), today))
                .moonshotGoals(data.goals().stream().filter(Goal::isMoonshot).count())
                .moonshotDreams(data.dreams().stream().filter(Dream::isMoonshot).count())
                .attention(DashboardAttention.build(data, mapper, clock.instant()))
                .energyBudget(buildEnergyBudget(data.tasks(), today))
                .gratitude(buildGratitude(today))
                .build();
    }

    /**
     * FR-59.2: not scoped to `visionAreaId` — gratitude entries aren't a
     * vision-area concept, so the dashboard card always reflects the whole
     * account, the same way `attention` and `energyBudget` are user-scoped
     * data computed independently of the area filter.
     */
    private DashboardSummaryResponse.Gratitude buildGratitude(LocalDate today) {
        Instant since = today.minusDays(DAYS_PER_WEEK).atStartOfDay(clock.getZone()).toInstant();
        long countThisWeek = gratitudeEntryRepository
                .countByUser_IdAndArchivedFalseAndCreatedAtAfter(lookup.userId(), since);
        List<GratitudeEntryResponse> recent = gratitudeEntryRepository
                .findByUser_IdAndArchivedFalseOrderByCreatedAtDesc(
                        lookup.userId(), PageRequest.of(0, RECENT_GRATITUDE_ENTRY_LIMIT)).stream()
                .map(mapper::toResponse)
                .toList();
        return new DashboardSummaryResponse.Gratitude(countThisWeek, recent);
    }

    /**
     * FR-34.2 / BR-28: this calendar week's tasks (Monday–Sunday, by due date),
     * split by energy demand with CHARGE and DRAIN netted. Null energy reads as
     * NEUTRAL (BR-27). Computed on read from the already-loaded, area-scoped,
     * unarchived task list — never stored, so it can't drift from the tasks.
     */
    private static DashboardSummaryResponse.EnergyBudget buildEnergyBudget(List<TaskItem> tasks, LocalDate today) {
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(DAYS_PER_WEEK - 1L);
        long charge = 0;
        long neutral = 0;
        long drain = 0;
        for (TaskItem task : tasks) {
            LocalDate due = task.getDueDate();
            if (due == null || due.isBefore(weekStart) || due.isAfter(weekEnd)) {
                continue;
            }
            EnergyDemand demand = task.getEnergyDemand() == null ? EnergyDemand.NEUTRAL : task.getEnergyDemand();
            switch (demand) {
                case CHARGE -> charge++;
                case DRAIN -> drain++;
                default -> neutral++;
            }
        }
        return new DashboardSummaryResponse.EnergyBudget(charge, neutral, drain, charge - drain);
    }

    private DashboardData loadDashboardData(Long visionAreaId, LocalDate today) {
        Long userId = lookup.userId();
        return new DashboardData(
                scoped(visionAreaRepository.findByUser_IdAndArchivedFalse(userId), VisionArea::getId, visionAreaId),
                scoped(dreamRepository.findByUser_IdAndArchivedFalse(userId), Dream::visionAreaId, visionAreaId),
                scoped(goalRepository.findByUser_IdAndArchivedFalse(userId), Goal::visionAreaId, visionAreaId),
                scoped(visionStepRepository.findByUser_IdAndArchivedFalse(userId), VisionStep::visionAreaId, visionAreaId),
                scoped(taskItemRepository.findByUser_IdAndArchivedFalse(userId), TaskItem::visionAreaId, visionAreaId),
                scoped(obstacleRepository.findByUser_IdAndArchivedFalse(userId), PlanLinked::linkedVisionAreaId, visionAreaId),
                scoped(partnerRepository.findByUser_IdAndArchivedFalse(userId), PlanLinked::linkedVisionAreaId, visionAreaId),
                scoped(reviewRepository.findByUser_IdAndArchivedFalse(userId), PlanLinked::linkedVisionAreaId, visionAreaId),
                // Only the trend window (plus each task's last pre-window value) —
                // the log table is the one that grows without bound.
                scoped(progressLogRepository.findTrendWindow(
                        userId, DashboardTrend.start(today).atStartOfDay(clock.getZone()).toInstant()),
                        PlanLinked::linkedVisionAreaId, visionAreaId));
    }

    /** Keeps only the records belonging to the chosen area; null means keep everything. */
    private static <T> List<T> scoped(List<T> records, Function<T, Long> areaIdOf, @Nullable Long visionAreaId) {
        if (visionAreaId == null) {
            return records;
        }
        return records.stream()
                .filter(item -> visionAreaId.equals(areaIdOf.apply(item)))
                .toList();
    }

    static boolean isOpenGoal(Goal goal) {
        return goal.getStatus() == WorkStatus.IN_PROGRESS || goal.getStatus() == WorkStatus.NOT_STARTED;
    }

    private static long countTasksDueBetween(List<TaskItem> tasks, LocalDate from, LocalDate to) {
        return tasks.stream()
                .filter(task -> task.getDueDate() != null
                        && !task.getDueDate().isBefore(from) && !task.getDueDate().isAfter(to))
                .count();
    }

    private long countTasksCompletedBetween(List<TaskItem> tasks, LocalDate from, LocalDate to) {
        return tasks.stream()
                .filter(task -> task.getCompletedAt() != null)
                .filter(task -> {
                    LocalDate completed = LocalDate.ofInstant(task.getCompletedAt(), clock.getZone());
                    return !completed.isBefore(from) && !completed.isAfter(to);
                })
                .count();
    }

    private static BigDecimal averageGoalProgress(List<Goal> goals, int scale) {
        if (goals.isEmpty()) {
            return ZERO;
        }
        return goals.stream()
                .map(Goal::getProgressPercent)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(goals.size()), scale, RoundingMode.HALF_UP);
    }

    private static <T> Map<String, Long> countBy(List<T> records, Function<T, String> classifier) {
        return records.stream().collect(Collectors.groupingBy(classifier, Collectors.counting()));
    }

    private static Map<String, Long> activeObstaclesByType(List<Obstacle> obstacles) {
        return countBy(obstacles.stream()
                        .filter(obstacle -> obstacle.getStatus() == ObstacleStatus.OPEN
                                || obstacle.getStatus() == ObstacleStatus.IN_PROGRESS)
                        .toList(),
                obstacle -> obstacle.getObstacleType().name());
    }

    private static Map<String, Long> reviewCadence(List<Review> reviews) {
        return countBy(reviews.stream()
                        .filter(review -> review.getReviewType() == ReviewType.DAILY
                                || review.getReviewType() == ReviewType.WEEKLY)
                        .toList(),
                review -> review.getReviewDate().toString());
    }

    /**
     * FR-16.4: weeks in the heatmap window whose weekly review carries an
     * answered diligence checklist (all-or-none, so one non-null answer
     * means the whole checklist was answered).
     */
    private static long countWeeksWithDiligence(List<Review> reviews, LocalDate today) {
        WeekFields weekFields = WeekFields.ISO;
        LocalDate windowStart = today.minusDays((long) TREND_WEEKS * DAYS_PER_WEEK - 1);
        return reviews.stream()
                .filter(review -> review.getReviewType() == ReviewType.WEEKLY)
                .filter(review -> review.getDiligenceClearVision() != null)
                .filter(review -> !review.getReviewDate().toLocalDate().isBefore(windowStart))
                .map(review -> review.getReviewDate().get(weekFields.weekBasedYear()) * 100
                        + review.getReviewDate().get(weekFields.weekOfWeekBasedYear()))
                .distinct()
                .count();
    }

    /** Per-area average goal progress, weakest area first, so the UI can spotlight what lags. */
    private static List<DashboardSummaryResponse.AreaProgress> buildAreaProgress(List<VisionArea> areas, List<Goal> goals) {
        return areas.stream()
                .filter(area -> area.getStatus() != LifecycleStatus.ARCHIVED)
                .map(area -> {
                    List<Goal> areaGoals = goals.stream()
                            .filter(goal -> goal.getDream().getVisionArea().getId().equals(area.getId()))
                            .toList();
                    return new DashboardSummaryResponse.AreaProgress(
                            area.getName(), averageGoalProgress(areaGoals, AREA_PROGRESS_SCALE));
                })
                .sorted(Comparator.comparing(DashboardSummaryResponse.AreaProgress::progress))
                .toList();
    }

    /** Highest priority first; among equal priorities the earliest due date wins, undated tasks last. */
    private List<TaskItemResponse> topPriorityTasks(List<TaskItem> tasks) {
        return tasks.stream()
                .filter(task -> task.getStatus() != WorkStatus.COMPLETED)
                .sorted(Comparator.comparingInt((TaskItem task) -> task.getPriority().ordinal()).reversed()
                        .thenComparing(TaskItem::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(TOP_PRIORITY_TASK_LIMIT)
                .map(mapper::toResponse)
                .toList();
    }

}
