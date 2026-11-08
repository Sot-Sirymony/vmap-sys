package com.visionmapping.service.support;

import com.visionmapping.dto.response.ArchiveImpactResponse;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionArea;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.repository.DreamRepository;
import com.visionmapping.repository.GoalRepository;
import com.visionmapping.repository.IdealPartnerProfileRepository;
import com.visionmapping.repository.TaskItemRepository;
import com.visionmapping.repository.VisionStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Keeps the archive flag consistent down and up the hierarchy. Archiving a
 * record archives everything beneath it; restoring one pulls its archived
 * parents back with it (as Paused, since the pre-archive status is unknown).
 * The impact counts report only what a fresh archive would newly hide, so the
 * confirmation dialog never over-counts already-archived descendants.
 */
@Component
@RequiredArgsConstructor
public class ArchiveCascade {

    private final EntityLookup lookup;
    private final DreamRepository dreamRepository;
    private final GoalRepository goalRepository;
    private final VisionStepRepository visionStepRepository;
    private final TaskItemRepository taskItemRepository;
    private final IdealPartnerProfileRepository idealPartnerProfileRepository;

    // --- Down-cascade: archiving a parent archives its descendants -----------

    public void archiveDreamsUnder(Long visionAreaId) {
        for (Dream dream : dreamRepository.findByVisionArea_IdAndUser_Id(visionAreaId, lookup.userId())) {
            dream.archive();
            archiveGoalsUnder(dream.getId());
        }
    }

    public void archiveGoalsUnder(Long dreamId) {
        for (Goal goal : goalRepository.findByDream_IdAndUser_Id(dreamId, lookup.userId())) {
            goal.archive();
            archiveStepsUnder(goal.getId());
        }
    }

    public void archiveStepsUnder(Long goalId) {
        for (VisionStep step : visionStepRepository.findByGoal_IdAndUser_Id(goalId, lookup.userId())) {
            step.archive();
            archiveTasksUnder(step.getId());
        }
    }

    public void archiveTasksUnder(Long stepId) {
        for (TaskItem task : taskItemRepository.findByStep_IdAndUser_Id(stepId, lookup.userId())) {
            task.archive();
        }
        // BR-13: the step's ideal partner profile is archived with it.
        setProfileArchived(stepId, true);
    }

    /** BR-13: a step's ideal partner profile follows the step's archived flag. */
    public void setProfileArchived(Long stepId, boolean archived) {
        idealPartnerProfileRepository.findByStep_IdAndUser_Id(stepId, lookup.userId())
                .ifPresent(profile -> profile.setArchived(archived));
    }

    // --- Impact: how many not-yet-archived descendants a cascade would hide --

    public ArchiveImpactResponse impactOfVisionArea(VisionArea area) {
        long dreams = 0;
        long goals = 0;
        long steps = 0;
        long tasks = 0;
        for (Dream dream : dreamRepository.findByVisionArea_IdAndUser_Id(area.getId(), lookup.userId())) {
            if (!dream.isArchived()) {
                dreams++;
            }
            ArchiveImpactResponse below = impactOfDream(dream);
            goals += below.goals();
            steps += below.steps();
            tasks += below.tasks();
        }
        return new ArchiveImpactResponse(dreams, goals, steps, tasks);
    }

    public ArchiveImpactResponse impactOfDream(Dream dream) {
        long goals = 0;
        long steps = 0;
        long tasks = 0;
        for (Goal goal : goalRepository.findByDream_IdAndUser_Id(dream.getId(), lookup.userId())) {
            if (!goal.isArchived()) {
                goals++;
            }
            ArchiveImpactResponse below = impactOfGoal(goal);
            steps += below.steps();
            tasks += below.tasks();
        }
        return new ArchiveImpactResponse(0, goals, steps, tasks);
    }

    public ArchiveImpactResponse impactOfGoal(Goal goal) {
        long steps = 0;
        long tasks = 0;
        for (VisionStep step : visionStepRepository.findByGoal_IdAndUser_Id(goal.getId(), lookup.userId())) {
            if (!step.isArchived()) {
                steps++;
            }
            tasks += impactOfStep(step).tasks();
        }
        return new ArchiveImpactResponse(0, 0, steps, tasks);
    }

    public ArchiveImpactResponse impactOfStep(VisionStep step) {
        long tasks = taskItemRepository.findByStep_IdAndUser_Id(step.getId(), lookup.userId()).stream()
                .filter(task -> !task.isArchived())
                .count();
        return new ArchiveImpactResponse(0, 0, 0, tasks);
    }

    // --- Up-chain: restoring a child brings its archived parents back --------

    public void unarchiveVisionArea(VisionArea area) {
        area.restore();
    }

    public void unarchiveDreamChain(Dream dream) {
        unarchiveVisionArea(dream.getVisionArea());
        dream.restore();
    }

    public void unarchiveGoalChain(Goal goal) {
        unarchiveDreamChain(goal.getDream());
        goal.restore();
    }
}
