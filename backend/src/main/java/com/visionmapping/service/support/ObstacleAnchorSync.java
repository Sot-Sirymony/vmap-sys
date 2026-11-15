package com.visionmapping.service.support;

import com.visionmapping.entity.Goal;
import com.visionmapping.entity.Obstacle;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.repository.ObstacleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * V17 BL-FR-08: an anchored obstacle's step, goal and dream are derived from
 * its task or step (BL-FR-02). When a task, step or goal moves to another
 * parent, this re-derives them in the same transaction, so an obstacle never
 * names a dream its step no longer belongs to. Unplaced obstacles are never
 * touched (BL-FR-06).
 */
@Component
@RequiredArgsConstructor
public class ObstacleAnchorSync {

    private final ObstacleRepository obstacleRepository;

    public void afterTaskMoved(TaskItem task) {
        obstacleRepository.findByRelatedTask_Id(task.getId()).forEach(Obstacle::syncParentsFromAnchor);
    }

    public void afterStepMoved(VisionStep step) {
        obstacleRepository.findByRelatedStep_Id(step.getId()).forEach(Obstacle::syncParentsFromAnchor);
    }

    public void afterGoalMoved(Goal goal) {
        obstacleRepository.findAnchoredUnderGoal(goal.getId()).forEach(Obstacle::syncParentsFromAnchor);
    }
}
