package com.visionmapping.service;

import com.visionmapping.dto.request.MarkBlockedRequest;
import com.visionmapping.dto.request.ObstacleRequest;
import com.visionmapping.dto.response.MarkBlockedResponse;
import com.visionmapping.entity.Obstacle;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.entity.enums.Severity;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.repository.ObstacleRepository;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.service.support.ProgressOverride;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V28 (BA-FR-11, BA-FR-12, D-28-9): marks one open task or step as blocked
 * from the Vision Map plan assistant. In one transaction it sets the Blocked
 * status through the normal task and step rules, gives a step without a gap
 * kind the chosen type, and records an open obstacle on the item (D-17-1),
 * reusing one that is already open there (BR-28-9). No AI.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class BlockMarkingService {

    /** The obstacle title column holds 220 characters; a longer reason also goes in full into the description. */
    private static final int TITLE_MAX = 220;

    private final EntityLookup lookup;
    private final TaskItemService taskService;
    private final VisionStepService stepService;
    private final ObstacleService obstacleService;
    private final ObstacleRepository obstacleRepository;

    public MarkBlockedResponse markBlocked(MarkBlockedRequest request) {
        String reason = request.reason().strip();
        Severity severity = request.severity() == null ? Severity.MEDIUM : request.severity();
        if ("TASK".equals(request.itemType())) {
            // Ownership first: another user's task answers 404.
            TaskItem task = lookup.task(request.itemId());
            VisionStep step = task.getStep();
            Optional<Obstacle> open = openObstacle(obstacleRepository.findByRelatedTask_Id(task.getId()));
            taskService.blockTask(task.getId(), reason);
            Long obstacleId = open.map(Obstacle::getId)
                    .orElseGet(() -> createObstacle(step.getId(), task.getId(), request, reason, severity));
            return new MarkBlockedResponse("TASK", task.getId(), obstacleId, open.isEmpty());
        }
        VisionStep step = lookup.step(request.itemId());
        if (step.isArchived() || step.getStatus() == WorkStatus.COMPLETED) {
            throw new BusinessRuleException("Only an open step can be marked as blocked.");
        }
        Optional<Obstacle> open = openObstacle(obstacleRepository.findByRelatedStep_Id(step.getId()).stream()
                .filter(obstacle -> obstacle.getRelatedTask() == null)
                .toList());
        if (step.getGapKind() == null) {
            step.setGapKind(request.obstacleType());
        }
        stepService.updateStepStatus(step.getId(), WorkStatus.BLOCKED, ProgressOverride.NONE);
        Long obstacleId = open.map(Obstacle::getId)
                .orElseGet(() -> createObstacle(step.getId(), null, request, reason, severity));
        return new MarkBlockedResponse("STEP", step.getId(), obstacleId, open.isEmpty());
    }

    private Long createObstacle(Long stepId, Long taskId, MarkBlockedRequest request, String reason, Severity severity) {
        String title = reason.length() <= TITLE_MAX ? reason : reason.substring(0, TITLE_MAX - 3) + "...";
        String description = reason.length() <= TITLE_MAX ? null : reason;
        return obstacleService.createObstacle(new ObstacleRequest(null, null, stepId, taskId, title, description,
                request.obstacleType(), severity, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, ObstacleStatus.OPEN)).id();
    }

    private static Optional<Obstacle> openObstacle(List<Obstacle> obstacles) {
        return obstacles.stream()
                .filter(obstacle -> !obstacle.isArchived()
                        && (obstacle.getStatus() == ObstacleStatus.OPEN || obstacle.getStatus() == ObstacleStatus.IN_PROGRESS))
                .findFirst();
    }
}
