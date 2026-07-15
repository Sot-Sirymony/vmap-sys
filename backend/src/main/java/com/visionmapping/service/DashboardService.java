package com.visionmapping.service;

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

    @Transactional(readOnly = true)
    public DashboardSummaryResponse buildDashboardSummary() {
        return buildDashboardSummary(null);
    }

    /**
     * @param visionAreaId scope every number to one area, or null for the whole
     *                     portfolio. Scoping happens here rather than in the
     *                     browser because what the client receives are already
     *                     sums — you cannot re-filter a total by area without the
     *                     rows behind it.
     */
    @Transactional(readOnly = true)
    public DashboardSummaryResponse buildDashboardSummary(Long visionAreaId) {
        Long userId = lookup.userId();
        List<VisionArea> areas = scoped(visionAreaRepository.findByUser_IdAndArchivedFalse(userId),
                VisionArea::getId, visionAreaId);
        List<Dream> dreams = scoped(dreamRepository.findByUser_IdAndArchivedFalse(userId),
                DashboardService::areaIdOf, visionAreaId);
        List<Goal> goals = scoped(goalRepository.findByUser_IdAndArchivedFalse(userId),
                DashboardService::areaIdOf, visionAreaId);
        List<VisionStep> steps = scoped(visionStepRepository.findByUser_IdAndArchivedFalse(userId),
                DashboardService::areaIdOf, visionAreaId);
        List<TaskItem> tasks = scoped(taskItemRepository.findByUser_IdAndArchivedFalse(userId),
                DashboardService::areaIdOf, visionAreaId);
        List<Obstacle> obstacles = scoped(obstacleRepository.findByUser_IdAndArchivedFalse(userId),
                DashboardService::areaIdOf, visionAreaId);
        List<Partner> partners = scoped(partnerRepository.findByUser_IdAndArchivedFalse(userId),
                DashboardService::areaIdOf, visionAreaId);
        List<Review> reviews = scoped(reviewRepository.findByUser_IdAndArchivedFalse(userId),
                DashboardService::areaIdOf, visionAreaId);
        List<ProgressLog> progressLogs = scoped(progressLogRepository.findByUser_IdAndArchivedFalse(userId),
                DashboardService::areaIdOf, visionAreaId);
        LocalDate today = LocalDate.now(clock);
        LocalDate weekEnd = today.plusDays(7);
        BigDecimal averageProgress = goals.isEmpty()
                ? ZERO
                : goals.stream()
                .map(Goal::getProgressPercent)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(goals.size()), 2, RoundingMode.HALF_UP);
        long moonshotGoals = goals.stream().filter(Goal::isMoonshot).count();
        Map<String, Long> goalsByStatus = goals.stream()
                .collect(Collectors.groupingBy(goal -> goal.getStatus().name(), Collectors.counting()));
        Map<String, Long> dreamsByArea = dreams.stream()
                .collect(Collectors.groupingBy(dream -> dream.getVisionArea().getName(), Collectors.counting()));
        Map<String, Long> tasksByStatus = tasks.stream()
                .collect(Collectors.groupingBy(task -> task.getStatus().name(), Collectors.counting()));
        Map<String, Long> tasksByPriority = tasks.stream()
                .collect(Collectors.groupingBy(task -> task.getPriority().name(), Collectors.counting()));
        Map<String, Long> activeObstaclesByType = obstacles.stream()
                .filter(obstacle -> obstacle.getStatus() == ObstacleStatus.OPEN || obstacle.getStatus() == ObstacleStatus.IN_PROGRESS)
                .collect(Collectors.groupingBy(obstacle -> obstacle.getObstacleType().name(), Collectors.counting()));
        Map<String, Long> partnersByStatus = partners.stream()
                .collect(Collectors.groupingBy(partner -> partner.getStatus().name(), Collectors.counting()));
        Map<String, Long> reviewCadence = reviews.stream()
                .filter(review -> review.getReviewType() == ReviewType.DAILY || review.getReviewType() == ReviewType.WEEKLY)
                .collect(Collectors.groupingBy(review -> review.getReviewDate().toString(), Collectors.counting()));
        // FR-16.4: weeks in the heatmap window whose weekly review carries an
        // answered diligence checklist (all-or-none, so one non-null answer
        // means the whole checklist was answered).
        WeekFields weekFields = WeekFields.ISO;
        long weeksWithDiligence = reviews.stream()
                .filter(review -> review.getReviewType() == ReviewType.WEEKLY)
                .filter(review -> review.getDiligenceClearVision() != null)
                .filter(review -> !review.getReviewDate().isBefore(today.minusDays(12L * 7 - 1)))
                .map(review -> review.getReviewDate().get(weekFields.weekBasedYear()) * 100
                        + review.getReviewDate().get(weekFields.weekOfWeekBasedYear()))
                .distinct()
                .count();
        List<DashboardSummaryResponse.AreaProgress> visionAreaProgress = areas.stream()
                .filter(area -> area.getStatus() != LifecycleStatus.ARCHIVED)
                .map(area -> {
                    List<Goal> areaGoals = goals.stream()
                            .filter(goal -> goal.getDream().getVisionArea().getId().equals(area.getId()))
                            .toList();
                    BigDecimal areaProgress = areaGoals.isEmpty()
                            ? ZERO
                            : areaGoals.stream()
                            .map(Goal::getProgressPercent)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(areaGoals.size()), 0, RoundingMode.HALF_UP);
                    return new DashboardSummaryResponse.AreaProgress(area.getName(), areaProgress);
                })
                .sorted(Comparator.comparing(DashboardSummaryResponse.AreaProgress::progress))
                .toList();
        List<TaskItemResponse> priorityTasks = tasks.stream()
                .filter(task -> task.getStatus() != WorkStatus.COMPLETED)
                .sorted(Comparator.comparingInt((TaskItem task) -> task.getPriority().ordinal()).reversed())
                .limit(5)
                .map(mapper::toResponse)
                .toList();

        return new DashboardSummaryResponse(
                areas.size(),
                dreams.stream().filter(dream -> dream.getStatus() == DreamStatus.ACTIVE).count(),
                goals.stream().filter(goal -> goal.getStatus() == WorkStatus.IN_PROGRESS || goal.getStatus() == WorkStatus.NOT_STARTED).count(),
                tasks.stream().filter(task -> task.getStatus() != WorkStatus.COMPLETED).count(),
                tasks.stream().filter(task -> task.getStatus() == WorkStatus.COMPLETED).count(),
                tasks.stream().filter(task -> progress.isOverdue(task, today)).count(),
                tasks.stream().filter(task -> task.getStatus() == WorkStatus.BLOCKED).count(),
                averageProgress,
                tasks.stream().filter(task -> !task.getDueDate().isBefore(today) && !task.getDueDate().isAfter(weekEnd)).count(),
                goalsByStatus,
                dreamsByArea,
                tasksByStatus,
                tasksByPriority,
                activeObstaclesByType,
                partnersByStatus,
                reviewCadence,
                buildProgressTrend(progressLogs, today),
                visionAreaProgress,
                priorityTasks,
                weeksWithDiligence,
                moonshotGoals,
                buildAttention(dreams, goals, steps, tasks, partners)
        );
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
    private DashboardSummaryResponse.Attention buildAttention(
            List<Dream> dreams,
            List<Goal> goals,
            List<VisionStep> steps,
            List<TaskItem> tasks,
            List<Partner> partners) {

        Set<Long> stepIdsWithTasks = tasks.stream()
                .map(task -> task.getStep().getId())
                .collect(Collectors.toSet());
        Set<Long> goalIdsWithSteps = steps.stream()
                .map(step -> step.getGoal().getId())
                .collect(Collectors.toSet());
        Set<Long> dreamIdsWithGoals = goals.stream()
                .map(goal -> goal.getDream().getId())
                .collect(Collectors.toSet());
        // A task counts as having a partner if any partner points at it.
        Set<Long> taskIdsWithPartner = partners.stream()
                .map(Partner::getRelatedTask)
                .filter(Objects::nonNull)
                .map(TaskItem::getId)
                .collect(Collectors.toSet());

        List<TaskItemResponse> blockedTasksWithoutPartner = tasks.stream()
                .filter(task -> task.getStatus() == WorkStatus.BLOCKED)
                .filter(task -> !taskIdsWithPartner.contains(task.getId()))
                .map(mapper::toResponse)
                .toList();

        // Business rule: a step marked complex should be broken into tasks.
        List<VisionStepResponse> complexStepsWithoutTasks = steps.stream()
                .filter(VisionStep::isComplex)
                .filter(step -> step.getStatus() != WorkStatus.COMPLETED)
                .filter(step -> !stepIdsWithTasks.contains(step.getId()))
                .map(mapper::toResponse)
                .toList();

        // A dream with no goals is still a wish — nothing can be executed on it.
        List<DreamResponse> dreamsWithoutGoals = dreams.stream()
                .filter(dream -> dream.getStatus() != DreamStatus.COMPLETED)
                .filter(dream -> !dreamIdsWithGoals.contains(dream.getId()))
                .map(mapper::toResponse)
                .toList();

        List<GoalResponse> goalsWithoutSteps = goals.stream()
                .filter(goal -> goal.getStatus() != WorkStatus.COMPLETED)
                .filter(goal -> !goalIdsWithSteps.contains(goal.getId()))
                .map(mapper::toResponse)
                .toList();

        return new DashboardSummaryResponse.Attention(
                blockedTasksWithoutPartner,
                complexStepsWithoutTasks,
                dreamsWithoutGoals,
                goalsWithoutSteps);
    }

    /**
     * Weekly samples of the running "average task progress" over the last
     * twelve weeks, reconstructed from the progress-log history: each task's
     * latest logged value as of a given day carries forward until its next
     * change, and each week reports the last day that had any data. Mirrors
     * the same Average Progress KPI, but as a trend instead of one number.
     */
    private List<DashboardSummaryResponse.TrendPoint> buildProgressTrend(List<ProgressLog> logs, LocalDate today) {
        int trendWeeks = 12;
        int totalDays = trendWeeks * 7;
        LocalDate start = today.minusDays(totalDays - 1L);
        Map<Long, List<ProgressLog>> byTask = logs.stream()
                .collect(Collectors.groupingBy(log -> log.getRelatedTask().getId()));
        byTask.values().forEach(entries -> entries.sort(Comparator.comparing(ProgressLog::getLoggedAt)));

        List<BigDecimal> dailyAverages = new ArrayList<>(totalDays);
        for (int i = 0; i < totalDays; i++) {
            LocalDate cursor = start.plusDays(i);
            List<BigDecimal> valuesAsOfDay = new ArrayList<>();
            for (List<ProgressLog> entries : byTask.values()) {
                ProgressLog latest = null;
                for (ProgressLog entry : entries) {
                    LocalDate logDate = entry.getLoggedAt().atZone(clock.getZone()).toLocalDate();
                    if (!logDate.isAfter(cursor)) {
                        latest = entry;
                    } else {
                        break;
                    }
                }
                if (latest != null) {
                    valuesAsOfDay.add(latest.getProgressPercentAfter());
                }
            }
            dailyAverages.add(valuesAsOfDay.isEmpty()
                    ? null
                    : valuesAsOfDay.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(valuesAsOfDay.size()), 2, RoundingMode.HALF_UP));
        }

        List<DashboardSummaryResponse.TrendPoint> weeklyPoints = new ArrayList<>(trendWeeks);
        for (int week = 0; week < trendWeeks; week++) {
            BigDecimal lastKnown = null;
            for (int day = week * 7 + 6; day >= week * 7; day--) {
                if (dailyAverages.get(day) != null) {
                    lastKnown = dailyAverages.get(day);
                    break;
                }
            }
            weeklyPoints.add(new DashboardSummaryResponse.TrendPoint(start.plusDays(week * 7L + 6), lastKnown));
        }

        int firstDataIndex = 0;
        while (firstDataIndex < weeklyPoints.size() && weeklyPoints.get(firstDataIndex).progress() == null) {
            firstDataIndex++;
        }
        if (firstDataIndex == weeklyPoints.size()) {
            return List.of();
        }
        return weeklyPoints.subList(firstDataIndex, weeklyPoints.size()).stream()
                .map(point -> new DashboardSummaryResponse.TrendPoint(point.weekEnd(), point.progress() == null ? ZERO : point.progress()))
                .toList();
    }

    // --- Archive impact (what a cascade would newly archive) -----------------

    // --- Restore (un-archive, pulling archived parents back with it) ---------

    // --- Permanent delete (irreversible; only for already-archived records) --

    /**
     * Clears every surviving record's link into the doomed subtree, removes the
     * progress logs that cannot outlive their task, then deletes the subtree
     * bottom-up so no foreign key is ever left dangling.
     */
    /** The database ids in a subtree, grouped by level, for fast link-membership checks. */
    /** Every hierarchy record a single permanent-delete will remove, gathered before the delete runs. */

}
