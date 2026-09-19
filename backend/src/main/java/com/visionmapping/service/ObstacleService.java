package com.visionmapping.service;

import static com.visionmapping.service.support.ServiceSupport.findAllForUser;
import static com.visionmapping.service.support.ServiceSupport.isBlank;
import static com.visionmapping.service.support.ServiceSupport.parseEnum;
import static com.visionmapping.service.support.ServiceSupport.requireArchived;

import com.visionmapping.config.CacheConfig;
import com.visionmapping.dto.request.ObstacleRequest;
import com.visionmapping.dto.response.ObstacleResponse;
import com.visionmapping.entity.Obstacle;
import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.ObstacleRepository;
import com.visionmapping.service.support.EntityLookup;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
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
    private final Clock clock;

    @Cacheable(CacheConfig.OBSTACLE_LIST_CACHE)
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
                .letterRank(request.letterRank())
                .solution(request.solution())
                .rootCause(request.rootCause())
                .creativeAlternatives(request.creativeAlternatives())
                .conflictIncident(request.conflictIncident())
                .conflictCost(request.conflictCost())
                .conflictOtherPerspective(request.conflictOtherPerspective())
                .conflictLesson(request.conflictLesson())
                .conflictPrivateNote(request.conflictPrivateNote())
                .conflictNextAction(request.conflictNextAction())
                .conflictExpectation(request.conflictExpectation())
                .conflictExpectationAgreed(request.conflictExpectationAgreed())
                .conflictNoCharacterAttacks(request.conflictNoCharacterAttacks())
                .conflictStayedOnIncident(request.conflictStayedOnIncident())
                .conflictNoThreatsOrSarcasm(request.conflictNoThreatsOrSarcasm())
                .conflictDefinedWinWin(request.conflictDefinedWinWin())
                .criticismOverstated(request.criticismOverstated())
                .criticismDelivery(request.criticismDelivery())
                .criticismSubstance(request.criticismSubstance())
                .requiredPartner(lookup.optionalPartner(request.requiredPartnerId()))
                .status(request.status())
                .build();
        prepareObstacle(entity);
        return mapper.toResponse(obstacleRepository.save(entity));
    }

    @Cacheable(CacheConfig.OBSTACLE_CACHE)
    @Transactional(readOnly = true)
    public ObstacleResponse getObstacle(Long id) {
        return mapper.toResponse(lookup.obstacle(id));
    }

    /**
     * FR-36.2: contextual resurfacing — the user's own already-resolved
     * obstacles of the same type, so a prior root cause and its alternatives
     * are one click away when facing a similar obstacle. Read-only and
     * user-scoped (BR-30); excludes the obstacle being viewed and archived rows.
     */
    @Cacheable(CacheConfig.OBSTACLE_LIST_CACHE)
    @Transactional(readOnly = true)
    public List<ObstacleResponse> relatedObstacles(Long id) {
        Obstacle target = lookup.obstacle(id);
        ObstacleType type = target.getObstacleType();
        return obstacleRepository.findByUser_IdAndArchivedFalse(lookup.userId()).stream()
                .filter(obstacle -> !obstacle.getId().equals(id))
                .filter(obstacle -> obstacle.getObstacleType() == type)
                .filter(obstacle -> obstacle.getStatus() == ObstacleStatus.RESOLVED)
                .map(mapper::toResponse)
                .toList();
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
        entity.setLetterRank(request.letterRank());
        entity.setSolution(request.solution());
        entity.setRootCause(request.rootCause());
        entity.setCreativeAlternatives(request.creativeAlternatives());
        entity.setConflictIncident(request.conflictIncident());
        entity.setConflictCost(request.conflictCost());
        entity.setConflictOtherPerspective(request.conflictOtherPerspective());
        entity.setConflictLesson(request.conflictLesson());
        entity.setConflictPrivateNote(request.conflictPrivateNote());
        entity.setConflictNextAction(request.conflictNextAction());
        entity.setConflictExpectation(request.conflictExpectation());
        entity.setConflictExpectationAgreed(request.conflictExpectationAgreed());
        entity.setConflictNoCharacterAttacks(request.conflictNoCharacterAttacks());
        entity.setConflictStayedOnIncident(request.conflictStayedOnIncident());
        entity.setConflictNoThreatsOrSarcasm(request.conflictNoThreatsOrSarcasm());
        entity.setConflictDefinedWinWin(request.conflictDefinedWinWin());
        entity.setCriticismOverstated(request.criticismOverstated());
        entity.setCriticismDelivery(request.criticismDelivery());
        entity.setCriticismSubstance(request.criticismSubstance());
        entity.setRequiredPartner(lookup.optionalPartner(request.requiredPartnerId()));
        entity.setStatus(request.status());
        prepareObstacle(entity);
        return mapper.toResponse(entity);
    }

    /**
     * FR-61.2: a one-time personal marker, not a status change. Idempotent —
     * calling this again after it's already set changes nothing (BR-50).
     */
    public ObstacleResponse releaseExpectation(Long id) {
        Obstacle entity = lookup.obstacle(id);
        if (entity.getExpectationReleasedAt() == null) {
            entity.setExpectationReleasedAt(Instant.now(clock));
        }
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
        // FR-62.2 / BR-51: completeness, not content — a "No" answer never
        // blocks; only a blank one does.
        if (entity.getStatus() == ObstacleStatus.RESOLVED
                && entity.getObstacleType() == ObstacleType.PARTNER
                && !conflictChecklistComplete(entity)) {
            throw new BusinessRuleException(
                    "Resolved PARTNER obstacles must also complete all four conflict engagement checklist items.");
        }
    }

    private static boolean conflictChecklistComplete(Obstacle entity) {
        return entity.getConflictNoCharacterAttacks() != null
                && entity.getConflictStayedOnIncident() != null
                && entity.getConflictNoThreatsOrSarcasm() != null
                && entity.getConflictDefinedWinWin() != null;
    }

    /** One alternative per line; blank lines don't count toward the minimum. */
    private static long countAlternatives(String creativeAlternatives) {
        if (isBlank(creativeAlternatives)) {
            return 0;
        }
        return creativeAlternatives.lines().filter(line -> !line.isBlank()).count();
    }
}
