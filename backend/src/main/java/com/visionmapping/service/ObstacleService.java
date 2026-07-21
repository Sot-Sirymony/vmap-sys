package com.visionmapping.service;

import static com.visionmapping.service.support.ServiceSupport.findAllForUser;
import static com.visionmapping.service.support.ServiceSupport.isBlank;
import static com.visionmapping.service.support.ServiceSupport.parseEnum;
import static com.visionmapping.service.support.ServiceSupport.requireArchived;

import com.visionmapping.dto.request.ObstacleRequest;
import com.visionmapping.dto.response.ObstacleResponse;
import com.visionmapping.entity.Obstacle;
import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.ObstacleRepository;
import com.visionmapping.service.support.EntityLookup;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Obstacles blocking a dream, goal, step, or task, optionally linked to the
 * partner whose help would clear them. FR-32 "creative persistence": closing
 * one out requires either a root cause (Resolved) or at least three
 * brainstormed alternatives (Accepted) — see BR-25/BR-26.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ObstacleService {

    private static final int MIN_CREATIVE_ALTERNATIVES = 3;

    private final EntityLookup lookup;
    private final VisionMappingMapper mapper;
    private final ObstacleRepository obstacleRepository;

    @Transactional(readOnly = true)
    public List<ObstacleResponse> listObstacles(boolean includeArchived) {
        return findAllForUser(obstacleRepository, lookup.userId(), includeArchived).stream()
                .map(mapper::toResponse)
                .toList();
    }

    public ObstacleResponse createObstacle(ObstacleRequest request) {
        Obstacle entity = Obstacle.builder()
                .user(lookup.currentUser())
                .relatedDream(lookup.optionalDream(request.relatedDreamId()))
                .relatedGoal(lookup.optionalGoal(request.relatedGoalId()))
                .relatedStep(lookup.optionalStep(request.relatedStepId()))
                .relatedTask(lookup.optionalTask(request.relatedTaskId()))
                .title(request.title())
                .description(request.description())
                .obstacleType(request.obstacleType())
                .severity(request.severity())
                .solution(request.solution())
                .rootCause(request.rootCause())
                .creativeAlternatives(request.creativeAlternatives())
                .requiredPartner(lookup.optionalPartner(request.requiredPartnerId()))
                .status(request.status())
                .build();
        prepareObstacle(entity);
        return mapper.toResponse(obstacleRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public ObstacleResponse getObstacle(Long id) {
        return mapper.toResponse(lookup.obstacle(id));
    }

    public ObstacleResponse updateObstacle(Long id, ObstacleRequest request) {
        Obstacle entity = lookup.obstacle(id);
        entity.setRelatedDream(lookup.optionalDream(request.relatedDreamId()));
        entity.setRelatedGoal(lookup.optionalGoal(request.relatedGoalId()));
        entity.setRelatedStep(lookup.optionalStep(request.relatedStepId()));
        entity.setRelatedTask(lookup.optionalTask(request.relatedTaskId()));
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setObstacleType(request.obstacleType());
        entity.setSeverity(request.severity());
        entity.setSolution(request.solution());
        entity.setRootCause(request.rootCause());
        entity.setCreativeAlternatives(request.creativeAlternatives());
        entity.setRequiredPartner(lookup.optionalPartner(request.requiredPartnerId()));
        entity.setStatus(request.status());
        prepareObstacle(entity);
        return mapper.toResponse(entity);
    }

    public ObstacleResponse updateObstacleStatus(Long id, String status) {
        Obstacle entity = lookup.obstacle(id);
        entity.setStatus(parseEnum(ObstacleStatus.class, status));
        prepareObstacle(entity);
        return mapper.toResponse(entity);
    }

    public void archiveObstacle(Long id) {
        lookup.obstacle(id).setArchived(true);
    }

    public void restoreObstacle(Long id) {
        lookup.obstacle(id).setArchived(false);
    }

    public void permanentlyDeleteObstacle(Long id) {
        Obstacle obstacle = lookup.obstacle(id);
        requireArchived(obstacle.isArchived(), "Obstacle");
        obstacleRepository.delete(obstacle);
    }

    /** BR-25/BR-26: the two closing statuses each require their own proof of diligence. */
    private void prepareObstacle(Obstacle entity) {
        if (entity.getStatus() == ObstacleStatus.RESOLVED && isBlank(entity.getRootCause())) {
            throw new BusinessRuleException("Resolved obstacles must include a root cause.");
        }
        if (entity.getStatus() == ObstacleStatus.ACCEPTED
                && countAlternatives(entity.getCreativeAlternatives()) < MIN_CREATIVE_ALTERNATIVES) {
            throw new BusinessRuleException(
                    "Accepted obstacles must include at least " + MIN_CREATIVE_ALTERNATIVES + " creative alternatives.");
        }
    }

    /** One alternative per line; blank lines don't count toward the minimum. */
    private static long countAlternatives(String creativeAlternatives) {
        if (isBlank(creativeAlternatives)) {
            return 0;
        }
        return creativeAlternatives.lines().filter(line -> !line.isBlank()).count();
    }
}
