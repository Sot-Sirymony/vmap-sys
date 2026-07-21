package com.visionmapping.service;

import com.visionmapping.config.CacheConfig;
import com.visionmapping.dto.response.DashboardSummaryResponse;
import com.visionmapping.dto.response.DreamResponse;
import com.visionmapping.dto.response.GoalResponse;
import com.visionmapping.dto.response.TaskItemResponse;
import com.visionmapping.dto.response.VisionStepResponse;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.Obstacle;
import com.visionmapping.entity.Partner;
import com.visionmapping.entity.ProgressLog;
import com.visionmapping.entity.Review;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionArea;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.DreamStatus;
import com.visionmapping.entity.enums.LifecycleStatus;
import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.entity.enums.ReviewType;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.mapper.VisionMappingMapper;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the single dashboard payload: the KPI counts, the per-status and
 * per-area breakdowns, the top-priority task list, and the twelve-week progress
 * trend reconstructed from the progress-log history.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final int TREND_WEEKS = 12;
    private static final int DAYS_PER_WEEK = 7;
    private static final int TOP_PRIORITY_TASK_LIMIT = 5;
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
    private final Clock clock;

    /** Every unarchived record of the user, already narrowed to one area when the caller asked for it. */
    private record ScopedData(
            List<VisionArea> areas,
            List<Dream> dreams,
            List<Goal> goals,
            List<VisionStep> steps,
            List<TaskItem> tasks,
            List<Obstacle> obstacles,
            List<Partner> partners,
            List<Review> reviews,
            List<ProgressLog> progressLogs) {
    }

    public DashboardSummaryResponse buildDashboardSummary() {
        return buildDashboardSummary(null, null, null);
    }

    public DashboardSummaryResponse buildDashboardSummary(Long visionAreaId) {
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
    public DashboardSummaryResponse buildDashboardSummary(Long visionAreaId, LocalDate periodStart, LocalDate periodEnd) {
        ScopedData data = loadScopedData(visionAreaId);
        LocalDate today = LocalDate.now(clock);
        LocalDate weekEnd = today.plusDays(DAYS_PER_WEEK);
        // The selectable window for the two time-based tiles. Defaults to the
        // current month when the caller passes nothing.
        LocalDate periodFrom = periodStart != null ? periodStart : today.withDayOfMonth(1);
        LocalDate periodTo = periodEnd != null ? periodEnd : today.withDayOfMonth(today.lengthOfMonth());

        return new DashboardSummaryResponse(
                data.areas().size(),
                data.dreams().stream().filter(dream -> dream.getStatus() == DreamStatus.ACTIVE).count(),
                data.goals().stream().filter(DashboardService::isOpenGoal).count(),
                data.tasks().stream().filter(task -> task.getStatus() != WorkStatus.COMPLETED).count(),
                data.tasks().stream().filter(task -> task.getStatus() == WorkStatus.COMPLETED).count(),
                data.tasks().stream().filter(task -> progress.isOverdue(task, today)).count(),
                data.tasks().stream().filter(task -> task.getStatus() == WorkStatus.BLOCKED).count(),
                averageGoalProgress(data.goals(), KPI_PROGRESS_SCALE),
                countTasksDueBetween(data.tasks(), today, weekEnd),
                countTasksDueBetween(data.tasks(), periodFrom, periodTo),
                countTasksCompletedBetween(data.tasks(), periodFrom, periodTo),
                countBy(data.goals(), goal -> goal.getStatus().name()),
                countBy(data.dreams(), dream -> dream.getVisionArea().getName()),
                countBy(data.tasks(), task -> task.getStatus().name()),
                countBy(data.tasks(), task -> task.getPriority().name()),
                activeObstaclesByType(data.obstacles()),
                countBy(data.partners(), partner -> partner.getStatus().name()),
                reviewCadence(data.reviews()),
                buildProgressTrend(data.progressLogs(), today),
                buildAreaProgress(data.areas(), data.goals()),
                topPriorityTasks(data.tasks()),
                countWeeksWithDiligence(data.reviews(), today),
                data.goals().stream().filter(Goal::isMoonshot).count(),
                data.dreams().stream().filter(Dream::isMoonshot).count(),
                buildAttention(data)
        );
    }

    private ScopedData loadScopedData(Long visionAreaId) {
        Long userId = lookup.userId();
        return new ScopedData(
                scoped(visionAreaRepository.findByUser_IdAndArchivedFalse(userId), VisionArea::getId, visionAreaId),
                scoped(dreamRepository.findByUser_IdAndArchivedFalse(userId), DashboardService::areaIdOf, visionAreaId),
                scoped(goalRepository.findByUser_IdAndArchivedFalse(userId), DashboardService::areaIdOf, visionAreaId),
                scoped(visionStepRepository.findByUser_IdAndArchivedFalse(userId), DashboardService::areaIdOf, visionAreaId),
                scoped(taskItemRepository.findByUser_IdAndArchivedFalse(userId), DashboardService::areaIdOf, visionAreaId),
                scoped(obstacleRepository.findByUser_IdAndArchivedFalse(userId), DashboardService::areaIdOf, visionAreaId),
                scoped(partnerRepository.findByUser_IdAndArchivedFalse(userId), DashboardService::areaIdOf, visionAreaId),
                scoped(reviewRepository.findByUser_IdAndArchivedFalse(userId), DashboardService::areaIdOf, visionAreaId),
                scoped(progressLogRepository.findByUser_IdAndArchivedFalse(userId), DashboardService::areaIdOf, visionAreaId));
    }

    /** Keeps only the records belonging to the chosen area; null means keep everything. */
    private static <T> List<T> scoped(List<T> records, Function<T, Long> areaIdOf, Long visionAreaId) {
        if (visionAreaId == null) {
            return records;
        }
        return records.stream()
                .filter(item -> visionAreaId.equals(areaIdOf.apply(item)))
                .toList();
    }

    private static boolean isOpenGoal(Goal goal) {
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
                .filter(review -> !review.getReviewDate().isBefore(windowStart))
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

    // Which area a record belongs to, walking up the hierarchy. A record that
    // links to nothing has no area, so it drops out of an area-scoped view —
    // it genuinely isn't part of that area.
    private static Long areaIdOf(Dream dream) {
        return dream.getVisionArea() == null ? null : dream.getVisionArea().getId();
    }

    private static Long areaIdOf(Goal goal) {
        return goal.getDream() == null ? null : areaIdOf(goal.getDream());
    }

    private static Long areaIdOf(VisionStep step) {
        return step.getGoal() == null ? null : areaIdOf(step.getGoal());
    }

    private static Long areaIdOf(TaskItem task) {
        return task.getStep() == null ? null : areaIdOf(task.getStep());
    }

    private static Long areaIdOf(Obstacle obstacle) {
        if (obstacle.getRelatedTask() != null) {
            return areaIdOf(obstacle.getRelatedTask());
        }
        if (obstacle.getRelatedStep() != null) {
            return areaIdOf(obstacle.getRelatedStep());
        }
        if (obstacle.getRelatedGoal() != null) {
            return areaIdOf(obstacle.getRelatedGoal());
        }
        if (obstacle.getRelatedDream() != null) {
            return areaIdOf(obstacle.getRelatedDream());
        }
        return null;
    }

    private static Long areaIdOf(Partner partner) {
        if (partner.getRelatedVisionArea() != null) {
            return partner.getRelatedVisionArea().getId();
        }
        if (partner.getRelatedTask() != null) {
            return areaIdOf(partner.getRelatedTask());
        }
        if (partner.getRelatedStep() != null) {
            return areaIdOf(partner.getRelatedStep());
        }
        if (partner.getRelatedGoal() != null) {
            return areaIdOf(partner.getRelatedGoal());
        }
        if (partner.getRelatedDream() != null) {
            return areaIdOf(partner.getRelatedDream());
        }
        return null;
    }

    private static Long areaIdOf(Review review) {
        if (review.getRelatedVisionArea() != null) {
            return review.getRelatedVisionArea().getId();
        }
        if (review.getRelatedDream() != null) {
            return areaIdOf(review.getRelatedDream());
        }
        return null;
    }

    private static Long areaIdOf(ProgressLog log) {
        return log.getRelatedTask() == null ? null : areaIdOf(log.getRelatedTask());
    }

    /**
     * The method's own rules, checked against the user's records. Everything here
     * is derived from lists already loaded above, so this costs one extra query
     * (steps) rather than four.
     */
    private DashboardSummaryResponse.Attention buildAttention(ScopedData data) {
        Set<Long> stepIdsWithTasks = data.tasks().stream()
                .map(task -> task.getStep().getId())
                .collect(Collectors.toSet());
        Set<Long> goalIdsWithSteps = data.steps().stream()
                .map(step -> step.getGoal().getId())
                .collect(Collectors.toSet());
        Set<Long> dreamIdsWithGoals = data.goals().stream()
                .map(goal -> goal.getDream().getId())
                .collect(Collectors.toSet());
        // A task counts as having a partner if any partner points at it.
        Set<Long> taskIdsWithPartner = data.partners().stream()
                .map(Partner::getRelatedTask)
                .filter(Objects::nonNull)
                .map(TaskItem::getId)
                .collect(Collectors.toSet());

        List<TaskItemResponse> blockedTasksWithoutPartner = data.tasks().stream()
                .filter(task -> task.getStatus() == WorkStatus.BLOCKED)
                .filter(task -> !taskIdsWithPartner.contains(task.getId()))
                .map(mapper::toResponse)
                .toList();

        // Business rule: a step marked complex should be broken into tasks.
        List<VisionStepResponse> complexStepsWithoutTasks = data.steps().stream()
                .filter(VisionStep::isComplex)
                .filter(step -> step.getStatus() != WorkStatus.COMPLETED)
                .filter(step -> !stepIdsWithTasks.contains(step.getId()))
                .map(mapper::toResponse)
                .toList();

        // A dream with no goals is still a wish — nothing can be executed on it.
        List<DreamResponse> dreamsWithoutGoals = data.dreams().stream()
                .filter(dream -> dream.getStatus() != DreamStatus.COMPLETED)
                .filter(dream -> !dreamIdsWithGoals.contains(dream.getId()))
                .map(mapper::toResponse)
                .toList();

        List<GoalResponse> goalsWithoutSteps = data.goals().stream()
                .filter(goal -> goal.getStatus() != WorkStatus.COMPLETED)
                .filter(goal -> !goalIdsWithSteps.contains(goal.getId()))
                .map(mapper::toResponse)
                .toList();

        // FR-25.2: a moonshot that never started is ambition going stale —
        // surfaced so the user either starts it or consciously revises it.
        List<GoalResponse> inactiveMoonshotGoals = data.goals().stream()
                .filter(Goal::isMoonshot)
                .filter(goal -> goal.getStatus() == WorkStatus.NOT_STARTED)
                .map(mapper::toResponse)
                .toList();

        // FR-31.4: same rule one level up — a moonshot dream still at the Idea
        // stage is ambition going stale, same as an unstarted moonshot goal.
        List<DreamResponse> inactiveMoonshotDreams = data.dreams().stream()
                .filter(Dream::isMoonshot)
                .filter(dream -> dream.getStatus() == DreamStatus.IDEA)
                .map(mapper::toResponse)
                .toList();

        return new DashboardSummaryResponse.Attention(
                blockedTasksWithoutPartner,
                complexStepsWithoutTasks,
                dreamsWithoutGoals,
                goalsWithoutSteps,
                inactiveMoonshotGoals,
                inactiveMoonshotDreams);
    }

    /**
     * Weekly samples of the running "average task progress" over the last
     * twelve weeks, reconstructed from the progress-log history: each task's
     * latest logged value as of a given day carries forward until its next
     * change, and each week reports the last day that had any data. Mirrors
     * the same Average Progress KPI, but as a trend instead of one number.
     */
    private List<DashboardSummaryResponse.TrendPoint> buildProgressTrend(List<ProgressLog> logs, LocalDate today) {
        int totalDays = TREND_WEEKS * DAYS_PER_WEEK;
        LocalDate start = today.minusDays(totalDays - 1L);
        List<BigDecimal> dailyAverages = dailyAverageProgress(groupLogsByTask(logs), start, totalDays);
        return trimLeadingEmptyWeeks(weeklySamples(dailyAverages, start));
    }

    /** Each task's log entries in the order they were written, so "latest as of a day" is a forward scan. */
    private static Map<Long, List<ProgressLog>> groupLogsByTask(List<ProgressLog> logs) {
        Map<Long, List<ProgressLog>> byTask = logs.stream()
                .collect(Collectors.groupingBy(log -> log.getRelatedTask().getId()));
        byTask.values().forEach(entries -> entries.sort(Comparator.comparing(ProgressLog::getLoggedAt)));
        return byTask;
    }

    /** Average of every task's carried-forward progress, one value per day; null on days with no data yet. */
    private List<BigDecimal> dailyAverageProgress(Map<Long, List<ProgressLog>> logsByTask, LocalDate start, int totalDays) {
        List<BigDecimal> dailyAverages = new ArrayList<>(totalDays);
        for (int i = 0; i < totalDays; i++) {
            LocalDate cursor = start.plusDays(i);
            List<BigDecimal> valuesAsOfDay = logsByTask.values().stream()
                    .map(entries -> latestProgressAsOf(entries, cursor))
                    .filter(Objects::nonNull)
                    .toList();
            dailyAverages.add(valuesAsOfDay.isEmpty()
                    ? null
                    : valuesAsOfDay.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(valuesAsOfDay.size()), 2, RoundingMode.HALF_UP));
        }
        return dailyAverages;
    }

    /** The last value logged on or before the cursor day, or null if the task had no log yet. */
    private BigDecimal latestProgressAsOf(List<ProgressLog> sortedEntries, LocalDate cursor) {
        ProgressLog latest = null;
        for (ProgressLog entry : sortedEntries) {
            LocalDate logDate = entry.getLoggedAt().atZone(clock.getZone()).toLocalDate();
            if (logDate.isAfter(cursor)) {
                break;
            }
            latest = entry;
        }
        return latest == null ? null : latest.getProgressPercentAfter();
    }

    /** One point per week, sampled from the last day of that week that had any data. */
    private static List<DashboardSummaryResponse.TrendPoint> weeklySamples(List<BigDecimal> dailyAverages, LocalDate start) {
        List<DashboardSummaryResponse.TrendPoint> weeklyPoints = new ArrayList<>(TREND_WEEKS);
        for (int week = 0; week < TREND_WEEKS; week++) {
            weeklyPoints.add(new DashboardSummaryResponse.TrendPoint(
                    start.plusDays(week * (long) DAYS_PER_WEEK + 6),
                    lastKnownAverageInWeek(dailyAverages, week)));
        }
        return weeklyPoints;
    }

    private static BigDecimal lastKnownAverageInWeek(List<BigDecimal> dailyAverages, int week) {
        for (int day = week * DAYS_PER_WEEK + 6; day >= week * DAYS_PER_WEEK; day--) {
            if (dailyAverages.get(day) != null) {
                return dailyAverages.get(day);
            }
        }
        return null;
    }

    /** Drops the weeks before the first logged data; gaps after that render as zero. */
    private static List<DashboardSummaryResponse.TrendPoint> trimLeadingEmptyWeeks(
            List<DashboardSummaryResponse.TrendPoint> weeklyPoints) {
        int firstDataIndex = 0;
        while (firstDataIndex < weeklyPoints.size() && weeklyPoints.get(firstDataIndex).progress() == null) {
            firstDataIndex++;
        }
        if (firstDataIndex == weeklyPoints.size()) {
            return List.of();
        }
        return weeklyPoints.subList(firstDataIndex, weeklyPoints.size()).stream()
                .map(point -> new DashboardSummaryResponse.TrendPoint(
                        point.weekEnd(), point.progress() == null ? ZERO : point.progress()))
                .toList();
    }
}
