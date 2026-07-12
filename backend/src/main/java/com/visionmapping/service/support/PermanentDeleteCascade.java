package com.visionmapping.service.support;

import com.visionmapping.entity.CommunicationMessage;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.Obstacle;
import com.visionmapping.entity.Partner;
import com.visionmapping.entity.Review;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionArea;
import com.visionmapping.entity.VisionStep;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Removes a hierarchy record and everything beneath it in one shot. It first
 * gathers the whole doomed subtree, then clears every surviving record's link
 * into it (partners, obstacles, messages, reviews) and drops the progress logs
 * that cannot outlive their task, and only then deletes the subtree bottom-up
 * so no foreign key is ever left dangling.
 */
@Component
@RequiredArgsConstructor
public class PermanentDeleteCascade {

    private final EntityLookup lookup;
    private final VisionAreaRepository visionAreaRepository;
    private final DreamRepository dreamRepository;
    private final GoalRepository goalRepository;
    private final VisionStepRepository visionStepRepository;
    private final TaskItemRepository taskItemRepository;
    private final PartnerRepository partnerRepository;
    private final CommunicationMessageRepository communicationMessageRepository;
    private final ReviewRepository reviewRepository;
    private final ObstacleRepository obstacleRepository;
    private final ProgressLogRepository progressLogRepository;

    public void deleteVisionArea(VisionArea area) {
        Subtree subtree = new Subtree();
        subtree.visionAreas.add(area);
        for (Dream dream : dreamRepository.findByVisionArea_IdAndUser_Id(area.getId(), lookup.userId())) {
            collectDreamSubtree(dream, subtree);
        }
        unlinkAndDelete(subtree);
    }

    public void deleteDream(Dream dream) {
        Subtree subtree = new Subtree();
        collectDreamSubtree(dream, subtree);
        unlinkAndDelete(subtree);
    }

    public void deleteGoal(Goal goal) {
        Subtree subtree = new Subtree();
        collectGoalSubtree(goal, subtree);
        unlinkAndDelete(subtree);
    }

    public void deleteStep(VisionStep step) {
        Subtree subtree = new Subtree();
        collectStepSubtree(step, subtree);
        unlinkAndDelete(subtree);
    }

    public void deleteTask(TaskItem task) {
        Subtree subtree = new Subtree();
        subtree.tasks.add(task);
        unlinkAndDelete(subtree);
    }

    private void collectDreamSubtree(Dream dream, Subtree subtree) {
        subtree.dreams.add(dream);
        for (Goal goal : goalRepository.findByDream_IdAndUser_Id(dream.getId(), lookup.userId())) {
            collectGoalSubtree(goal, subtree);
        }
    }

    private void collectGoalSubtree(Goal goal, Subtree subtree) {
        subtree.goals.add(goal);
        for (VisionStep step : visionStepRepository.findByGoal_IdAndUser_Id(goal.getId(), lookup.userId())) {
            collectStepSubtree(step, subtree);
        }
    }

    private void collectStepSubtree(VisionStep step, Subtree subtree) {
        subtree.steps.add(step);
        subtree.tasks.addAll(taskItemRepository.findByStep_IdAndUser_Id(step.getId(), lookup.userId()));
    }

    private void unlinkAndDelete(Subtree subtree) {
        DeletedIds deleted = new DeletedIds(subtree);
        Long userId = lookup.userId();
        for (TaskItem task : subtree.tasks) {
            progressLogRepository.deleteAll(progressLogRepository.findByRelatedTask_IdAndUser_Id(task.getId(), userId));
        }
        partnerRepository.findByUser_Id(userId).forEach(partner -> unlinkPartner(partner, deleted));
        obstacleRepository.findByUser_Id(userId).forEach(obstacle -> unlinkObstacle(obstacle, deleted));
        communicationMessageRepository.findByUser_Id(userId).forEach(message -> unlinkMessage(message, deleted));
        reviewRepository.findByUser_Id(userId).forEach(review -> unlinkReview(review, deleted));

        taskItemRepository.deleteAll(subtree.tasks);
        visionStepRepository.deleteAll(subtree.steps);
        goalRepository.deleteAll(subtree.goals);
        dreamRepository.deleteAll(subtree.dreams);
        visionAreaRepository.deleteAll(subtree.visionAreas);
    }

    private void unlinkPartner(Partner partner, DeletedIds deleted) {
        if (references(partner.getRelatedVisionArea(), VisionArea::getId, deleted.visionAreas)) {
            partner.setRelatedVisionArea(null);
        }
        if (references(partner.getRelatedDream(), Dream::getId, deleted.dreams)) {
            partner.setRelatedDream(null);
        }
        if (references(partner.getRelatedGoal(), Goal::getId, deleted.goals)) {
            partner.setRelatedGoal(null);
        }
        if (references(partner.getRelatedStep(), VisionStep::getId, deleted.steps)) {
            partner.setRelatedStep(null);
        }
        if (references(partner.getRelatedTask(), TaskItem::getId, deleted.tasks)) {
            partner.setRelatedTask(null);
        }
    }

    private void unlinkObstacle(Obstacle obstacle, DeletedIds deleted) {
        if (references(obstacle.getRelatedDream(), Dream::getId, deleted.dreams)) {
            obstacle.setRelatedDream(null);
        }
        if (references(obstacle.getRelatedGoal(), Goal::getId, deleted.goals)) {
            obstacle.setRelatedGoal(null);
        }
        if (references(obstacle.getRelatedStep(), VisionStep::getId, deleted.steps)) {
            obstacle.setRelatedStep(null);
        }
        if (references(obstacle.getRelatedTask(), TaskItem::getId, deleted.tasks)) {
            obstacle.setRelatedTask(null);
        }
    }

    private void unlinkMessage(CommunicationMessage message, DeletedIds deleted) {
        if (references(message.getRelatedDream(), Dream::getId, deleted.dreams)) {
            message.setRelatedDream(null);
        }
        if (references(message.getRelatedGoal(), Goal::getId, deleted.goals)) {
            message.setRelatedGoal(null);
        }
        if (references(message.getRelatedTask(), TaskItem::getId, deleted.tasks)) {
            message.setRelatedTask(null);
        }
    }

    private void unlinkReview(Review review, DeletedIds deleted) {
        if (references(review.getRelatedVisionArea(), VisionArea::getId, deleted.visionAreas)) {
            review.setRelatedVisionArea(null);
        }
        if (references(review.getRelatedDream(), Dream::getId, deleted.dreams)) {
            review.setRelatedDream(null);
        }
    }

    private <T> boolean references(T related, Function<T, Long> idOf, Set<Long> deletedIds) {
        return related != null && deletedIds.contains(idOf.apply(related));
    }

    /** The database ids in a subtree, grouped by level, for fast link-membership checks. */
    private static final class DeletedIds {
        private final Set<Long> visionAreas;
        private final Set<Long> dreams;
        private final Set<Long> goals;
        private final Set<Long> steps;
        private final Set<Long> tasks;

        private DeletedIds(Subtree subtree) {
            visionAreas = ids(subtree.visionAreas, VisionArea::getId);
            dreams = ids(subtree.dreams, Dream::getId);
            goals = ids(subtree.goals, Goal::getId);
            steps = ids(subtree.steps, VisionStep::getId);
            tasks = ids(subtree.tasks, TaskItem::getId);
        }

        private static <T> Set<Long> ids(List<T> entities, Function<T, Long> idOf) {
            return entities.stream().map(idOf).collect(Collectors.toSet());
        }
    }

    /** Every hierarchy record a single permanent-delete will remove, gathered before the delete runs. */
    private static final class Subtree {
        private final List<VisionArea> visionAreas = new ArrayList<>();
        private final List<Dream> dreams = new ArrayList<>();
        private final List<Goal> goals = new ArrayList<>();
        private final List<VisionStep> steps = new ArrayList<>();
        private final List<TaskItem> tasks = new ArrayList<>();
    }
}
