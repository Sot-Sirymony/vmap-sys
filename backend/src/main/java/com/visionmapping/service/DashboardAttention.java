package com.visionmapping.service;

import com.visionmapping.dto.response.DashboardSummaryResponse;
import com.visionmapping.dto.response.DreamResponse;
import com.visionmapping.dto.response.GoalResponse;
import com.visionmapping.dto.response.TaskItemResponse;
import com.visionmapping.dto.response.VisionAreaResponse;
import com.visionmapping.dto.response.VisionStepResponse;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.Partner;
import com.visionmapping.entity.PlanLinked;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.DreamStatus;
import com.visionmapping.entity.enums.LifecycleStatus;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.mapper.VisionMappingMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The "needs attention" part of the dashboard: the method's own rules checked
 * against the participant's records (blocked tasks with no partner, complex
 * steps with no tasks, dreams with no goals, goals with no steps, stale
 * moonshots, starved areas). Read-only and derived from data already loaded,
 * so it is plain static code. Split out of DashboardService in BRD 13 EJ-FR-29.
 */
final class DashboardAttention {

    // FR-37.1: an area with an active goal but no progress in this many days —
    // while another area did move — is flagged as being starved of attention.
    private static final int STARVATION_WINDOW_DAYS = 14;

    private DashboardAttention() {
    }

    /**
     * The method's own rules, checked against the user's records. Everything here
     * is derived from lists already loaded above, so this costs one extra query
     * (steps) rather than four.
     */
    static DashboardSummaryResponse.Attention build(DashboardData data, VisionMappingMapper mapper, Instant now) {
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
                inactiveMoonshotDreams,
                starvedVisionAreas(data, mapper, now));
    }

    /**
     * FR-37.1 (BR-31): areas being starved of attention — an active goal but no
     * task progress logged in the last {@value #STARVATION_WINDOW_DAYS} days,
     * *while at least one other area did move* in that window. The "another area
     * moved" gate is what makes this an imbalance signal rather than a
     * holiday-lull false alarm. Purely computed and read-only: it never mutates
     * or persists anything, and it naturally yields nothing in an area-scoped
     * view (a cross-area comparison needs more than one area).
     */
    private static List<VisionAreaResponse> starvedVisionAreas(DashboardData data, VisionMappingMapper mapper, Instant now) {
        Instant cutoff = now.minus(STARVATION_WINDOW_DAYS, ChronoUnit.DAYS);
        Set<Long> areasWithRecentProgress = data.progressLogs().stream()
                .filter(log -> log.getLoggedAt() != null && !log.getLoggedAt().isBefore(cutoff))
                .map(PlanLinked::linkedVisionAreaId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // No area moved at all → a global lull, not an imbalance. Flag nothing.
        if (areasWithRecentProgress.isEmpty()) {
            return List.of();
        }
        Set<Long> areasWithActiveGoal = data.goals().stream()
                .filter(DashboardService::isOpenGoal)
                .map(Goal::visionAreaId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return data.areas().stream()
                .filter(area -> area.getStatus() != LifecycleStatus.ARCHIVED)
                .filter(area -> areasWithActiveGoal.contains(area.getId()))
                .filter(area -> !areasWithRecentProgress.contains(area.getId()))
                .map(mapper::toResponse)
                .toList();
    }
}
