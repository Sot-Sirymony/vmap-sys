package com.visionmapping.service;

import static com.visionmapping.service.support.ServiceSupport.parseEnum;
import static com.visionmapping.service.support.ServiceSupport.requireArchived;

import com.visionmapping.dto.request.ObstacleRequest;
import com.visionmapping.dto.response.ObstacleResponse;
import com.visionmapping.entity.Obstacle;
import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.ObstacleRepository;
import com.visionmapping.service.support.EntityLookup;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Obstacles blocking a dream, goal, step, or task, optionally linked to the
 * partner whose help would clear them.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ObstacleService {

    private final EntityLookup lookup;
    private final VisionMappingMapper mapper;
    private final ObstacleRepository obstacleRepository;

    @Transactional(readOnly = true)
    public List<ObstacleResponse> listObstacles(boolean includeArchived) {
        List<Obstacle> entities = includeArchived
                ? obstacleRepository.findByUser_Id(lookup.userId())
                : obstacleRepository.findByUser_IdAndArchivedFalse(lookup.userId());
        return entities.stream().map(mapper::toResponse).toList();
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
                .requiredPartner(lookup.optionalPartner(request.requiredPartnerId()))
                .status(request.status())
                .build();
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
        entity.setRequiredPartner(lookup.optionalPartner(request.requiredPartnerId()));
        entity.setStatus(request.status());
        return mapper.toResponse(entity);
    }

    public ObstacleResponse updateObstacleStatus(Long id, String status) {
        Obstacle entity = lookup.obstacle(id);
        entity.setStatus(parseEnum(ObstacleStatus.class, status));
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
}
