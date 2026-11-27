package com.visionmapping.service;

import static com.visionmapping.service.support.ServiceSupport.findAllForUser;
import static com.visionmapping.service.support.ServiceSupport.findPageForUser;
import static com.visionmapping.service.support.ServiceSupport.isBlank;
import static com.visionmapping.service.support.ServiceSupport.requireArchived;

import com.visionmapping.config.CacheConfig;
import com.visionmapping.dto.request.ObstacleRequest;
import com.visionmapping.dto.response.ObstacleResponse;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.Obstacle;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.ObstaclePlacement;
import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.ObstacleRepository;
import com.visionmapping.service.support.ArchiveFilter;
import com.visionmapping.service.support.EntityLookup;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.visionmapping.dto.response.PagedResponse;
import com.visionmapping.util.Pageables;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import java.util.function.Function;

/**
 * Obstacles blocking a step or task, optionally linked to the partner whose
 * help would clear them. FR-32 "creative persistence": closing one out
 * requires either a root cause (Resolved) or at least three brainstormed
 * alternatives (Accepted) — see BR-25/BR-26.
 *
 * <p>V17 placement (D-17-1): a new plan obstacle is anchored to a step or a
 * task, and its goal and dream are derived from that anchor (BL-FR-01,
 * BL-FR-02). Partner conflicts may stay unplaced (BL-FR-04). Obstacles from
 * before V17 that sit only on a dream or goal are kept as they are until the
 * participant moves them (BL-FR-06).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ObstacleService {

    private static final int MIN_CREATIVE_ALTERNATIVES = 3;
    static final String ANCHOR_REQUIRED = "Put this blocker on a step or a task.";
    static final String TASK_NOT_IN_STEP = "That task is not part of the chosen step.";

    /** Whether an obstacle without a step or task may be saved. */
    private enum AnchorPolicy { REQUIRED, OPTIONAL }

    private final EntityLookup lookup;
    private final VisionMappingMapper mapper;
    private final ObstacleRepository obstacleRepository;
    private final Clock clock;

    @Cacheable(CacheConfig.OBSTACLE_LIST_CACHE)
    @Transactional(readOnly = true)
    public List<ObstacleResponse> listObstacles(ArchiveFilter archiveFilter) {
        return findAllForUser(obstacleRepository, lookup.userId(), archiveFilter).stream()
                .map(mapper::toResponse)
                .toList();
    }

    /** V33 (D-33-10): the properties the obstacle list can be sorted by. */
    static final Set<String> OBSTACLE_SORTS = Set.of("title", "obstacleType", "severity", "letterRank", "status", "resolvedAt",
            "createdAt", "updatedAt");

    /**
     * V33 (D-33-9): one page of the user's obstacles. {@code placement=UNPLACED}
     * pages the obstacles that still need a step (BL-FR-05), which are few, in
     * memory; any other value pages every obstacle in the database.
     */
    @Transactional(readOnly = true)
    public PagedResponse<ObstacleResponse> pageObstacles(ArchiveFilter archiveFilter, @Nullable String placement, Pageable pageable) {
        Pageable checked = Pageables.whitelist(pageable, OBSTACLE_SORTS);
        if ("UNPLACED".equalsIgnoreCase(placement)) {
            return PagedResponse.from(Pageables.slice(listUnplacedObstacles(), checked), Function.identity());
        }
        return PagedResponse.from(findPageForUser(obstacleRepository, lookup.userId(), archiveFilter, checked), mapper::toResponse);
    }

    /**
     * V17 BL-FR-05: active obstacles that still need a step: unplaced and not
     * exempt, or placed on a step or task that was archived (BL-FR-09).
     * Resolved and Accepted ones need no action, so they are left out.
     */
    @Transactional(readOnly = true)
    public List<ObstacleResponse> listUnplacedObstacles() {
        return obstacleRepository.findByUser_IdAndArchivedFalse(lookup.userId()).stream()
                .filter(ObstacleService::needsPlacing)
                .map(mapper::toResponse)
                .toList();
    }

    static boolean needsPlacing(Obstacle obstacle) {
        if (obstacle.isArchived() || obstacle.getStatus() == ObstacleStatus.RESOLVED
                || obstacle.getStatus() == ObstacleStatus.ACCEPTED) {
            return false;
        }
        if (obstacle.isAnchorArchived()) {
            return true;
        }
        return obstacle.placement() == ObstaclePlacement.NONE && !obstacle.isPlacementExempt();
    }

    /** V17 BL-FR-01: a new obstacle must sit on a step or task, unless it is an exempt partner conflict. */
    public ObstacleResponse createObstacle(ObstacleRequest request) {
        return create(request, AnchorPolicy.REQUIRED);
    }

    /**
     * Creates an obstacle that may sit only on a dream or goal. Used by the
     * Excel import (BL-FR-41: older workbooks hold dream-only rows, imported as
     * unplaced) and, until V17 phase 2 (BL-FR-37, BL-FR-38), by the AI Plan
     * Builder and plan assistant. Anything created here without a step shows
     * in the "Not on a step yet" list.
     */
    public ObstacleResponse createObstacleAllowingUnplaced(ObstacleRequest request) {
        return create(request, AnchorPolicy.OPTIONAL);
    }

    private ObstacleResponse create(ObstacleRequest request, AnchorPolicy policy) {
        Obstacle entity = Obstacle.builder()
                .user(lookup.currentUser())
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
        applyNewLinks(entity, request, policy);
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
        applyUpdatedLinks(entity, request);
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

    public ObstacleResponse updateObstacleStatus(Long id, ObstacleStatus status) {
        Obstacle entity = lookup.obstacle(id);
        entity.setStatus(status);
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

    /** BL-FR-01, BL-FR-02, BL-FR-04 for a new obstacle. */
    private void applyNewLinks(Obstacle entity, ObstacleRequest request, AnchorPolicy policy) {
        if (anchorRequested(request)) {
            anchor(entity, request);
            return;
        }
        if (policy == AnchorPolicy.REQUIRED && !entity.isPlacementExempt()) {
            throw new BusinessRuleException(ANCHOR_REQUIRED);
        }
        entity.leaveUnplaced(lookup.optionalDream(request.relatedDreamId()), lookup.optionalGoal(request.relatedGoalId()));
    }

    /**
     * BL-FR-06: an obstacle saved without a step or task keeps its place only
     * if it is exempt, or it was already unplaced and its dream and goal are
     * unchanged. Moving it or taking it off its step needs a step or task.
     */
    private void applyUpdatedLinks(Obstacle entity, ObstacleRequest request) {
        if (anchorRequested(request)) {
            anchor(entity, request);
            return;
        }
        if (entity.isPlacementExempt()) {
            entity.leaveUnplaced(lookup.optionalDream(request.relatedDreamId()), lookup.optionalGoal(request.relatedGoalId()));
            return;
        }
        boolean unchangedUnplaced = entity.placement() == ObstaclePlacement.NONE
                && Objects.equals(idOf(entity.getRelatedDream()), request.relatedDreamId())
                && Objects.equals(idOf(entity.getRelatedGoal()), request.relatedGoalId());
        if (!unchangedUnplaced) {
            throw new BusinessRuleException(ANCHOR_REQUIRED);
        }
    }

    private static boolean anchorRequested(ObstacleRequest request) {
        return request.relatedStepId() != null || request.relatedTaskId() != null;
    }

    /** BL-FR-02: the client's dream and goal are ignored once a step or task is given. */
    private void anchor(Obstacle entity, ObstacleRequest request) {
        VisionStep step = lookup.optionalStep(request.relatedStepId());
        TaskItem task = lookup.optionalTask(request.relatedTaskId());
        if (step != null && task != null && !task.getStep().getId().equals(step.getId())) {
            throw new BusinessRuleException(TASK_NOT_IN_STEP);
        }
        entity.anchorTo(step, task);
    }

    @Nullable
    private static Long idOf(@Nullable Dream dream) {
        return dream == null ? null : dream.getId();
    }

    @Nullable
    private static Long idOf(@Nullable Goal goal) {
        return goal == null ? null : goal.getId();
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
        // V19 RM-FR-08: create, update and status changes all pass here.
        entity.recordResolution(Instant.now(clock));
    }

    private static boolean conflictChecklistComplete(Obstacle entity) {
        return entity.getConflictNoCharacterAttacks() != null
                && entity.getConflictStayedOnIncident() != null
                && entity.getConflictNoThreatsOrSarcasm() != null
                && entity.getConflictDefinedWinWin() != null;
    }

    /** One alternative per line; blank lines don't count toward the minimum. */
    private static long countAlternatives(@Nullable String creativeAlternatives) {
        if (isBlank(creativeAlternatives)) {
            return 0;
        }
        return creativeAlternatives.lines().filter(line -> !line.isBlank()).count();
    }
}
