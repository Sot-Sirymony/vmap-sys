package com.visionmapping.service;

import static com.visionmapping.service.support.ServiceSupport.isBlank;

import com.visionmapping.dto.request.GapStepRequest;
import com.visionmapping.dto.request.PartnerRequest;
import com.visionmapping.dto.request.VisionStepRequest;
import com.visionmapping.dto.response.GapStepResponse;
import com.visionmapping.dto.response.ObstacleResponse;
import com.visionmapping.dto.response.PartnerResponse;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.Obstacle;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.entity.enums.PartnerStatus;
import com.visionmapping.entity.enums.PartnerSupportType;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.service.support.EntityLookup;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V17 phase 3 (BL-FR-19 to BL-FR-21, BL-FR-23): a gap is a signal to find
 * help, so it becomes a step of its own. From a step's gap, the new step goes
 * just before that step in the same goal. From an obstacle, the obstacle is
 * anchored to the new step and moves to In Progress. Either way, a To Contact
 * partner can be created with it, and the new step remembers what it covers.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class GapStepService {

    static final String NO_GAP = "Write what is missing on this step first.";
    static final String CHOOSE_GOAL = "Choose the goal this step belongs to.";
    static final String EXEMPT = "A partner conflict stays off the steps. Work it through on the Obstacles page.";
    static final String CLOSED = "This blocker is already closed, so it needs no step.";
    private static final int MAX_PARTNER_NAME = 180;

    /** The same suggestions as the frontend's suggestPartnerFor (CLAUDE.md, Partner Management page). */
    private static final Map<ObstacleType, PartnerSupportType> SUPPORT_FOR_GAP = new EnumMap<>(Map.of(
            ObstacleType.KNOWLEDGE, PartnerSupportType.MENTOR,
            ObstacleType.SKILL, PartnerSupportType.TECHNICAL,
            ObstacleType.TIME, PartnerSupportType.COLLEAGUE,
            ObstacleType.MONEY, PartnerSupportType.FINANCIAL,
            ObstacleType.MOTIVATION, PartnerSupportType.EMOTIONAL,
            ObstacleType.DECISION, PartnerSupportType.ADVISOR));

    private final EntityLookup lookup;
    private final VisionMappingMapper mapper;
    private final VisionStepRepository visionStepRepository;
    private final VisionStepService visionStepService;
    private final PartnerService partnerService;

    /** BL-FR-19: turns the gap written on a step into a "find help" step placed just before it. */
    public GapStepResponse createFromStep(Long stepId, GapStepRequest request) {
        VisionStep source = lookup.step(stepId);
        if (source.isArchived()) {
            throw new BusinessRuleException("Restore this step before planning help for it.");
        }
        if (isBlank(source.getResources())) {
            throw new BusinessRuleException(NO_GAP);
        }
        ObstacleType gapKind = VisionStepService.gapKindOf(request.gapKind());
        VisionStep created = createStep(source.getGoal(), source, request, gapKind != null ? gapKind : source.getGapKind());
        created.setCoversStep(source);
        PartnerResponse partner = maybeCreatePartner(created, request, source.getResources());
        return new GapStepResponse(mapper.toResponse(created), partner, null);
    }

    /** BL-FR-20: turns an obstacle into a "find help" step and anchors the obstacle to it. */
    public GapStepResponse createFromObstacle(Long obstacleId, GapStepRequest request) {
        Obstacle obstacle = lookup.obstacle(obstacleId);
        if (obstacle.isPlacementExempt()) {
            throw new BusinessRuleException(EXEMPT);
        }
        if (obstacle.getStatus() == ObstacleStatus.RESOLVED || obstacle.getStatus() == ObstacleStatus.ACCEPTED) {
            throw new BusinessRuleException(CLOSED);
        }
        VisionStep anchor = obstacle.getRelatedStep();
        boolean onLiveStep = anchor != null && !anchor.isArchived();
        Goal goal;
        if (onLiveStep) {
            goal = anchor.getGoal();
        } else if (request.goalId() != null) {
            goal = lookup.goal(request.goalId());
        } else {
            throw new BusinessRuleException(CHOOSE_GOAL);
        }
        ObstacleType gapKind = VisionStepService.gapKindOf(request.gapKind());
        VisionStep created = createStep(goal, onLiveStep ? anchor : null, request,
                gapKind != null ? gapKind : obstacle.getObstacleType());
        created.setCoversObstacle(obstacle);
        if (onLiveStep) {
            created.setCoversStep(anchor);
        }
        obstacle.anchorTo(created, null);
        obstacle.setStatus(ObstacleStatus.IN_PROGRESS);
        PartnerResponse partner = maybeCreatePartner(created, request, obstacle.getTitle());
        ObstacleResponse obstacleResponse = mapper.toResponse(obstacle);
        return new GapStepResponse(mapper.toResponse(created), partner, obstacleResponse);
    }

    /**
     * BL-FR-23: the one way a gap becomes a step, shared with Strengths and
     * Gaps (FR-82). With {@code before}, the step takes that step's place and
     * the later steps move down one; without it, the step goes last.
     */
    private VisionStep createStep(Goal goal, @Nullable VisionStep before, GapStepRequest request, @Nullable ObstacleType gapKind) {
        int sequence = before != null ? makeRoomBefore(goal, before) : nextSequence(goal);
        Long id = visionStepService.createStep(new VisionStepRequest(goal.getId(), request.title().strip(),
                isBlank(request.description()) ? null : request.description().strip(), request.owner().strip(), sequence,
                false, request.priority(), request.targetDate(), WorkStatus.NOT_STARTED, null, null, null,
                gapKind == null ? null : gapKind.name())).id();
        return lookup.step(id);
    }

    /**
     * FR-82's learning step, made the same way as a gap step (BL-FR-23).
     * Public, so a call through the Spring proxy reaches the real bean.
     */
    public VisionStep createLearningStep(Goal goal, String title, @Nullable String description, String owner, LocalDate targetDate) {
        return createStep(goal, null, new GapStepRequest(goal.getId(), title, description, owner, Priority.MEDIUM, targetDate,
                null, false, null, null), null);
    }

    private int makeRoomBefore(Goal goal, VisionStep before) {
        int sequence = before.getSequenceNumber();
        visionStepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(goal.getId(), lookup.userId()).stream()
                .filter(step -> step.getSequenceNumber() >= sequence)
                .forEach(step -> step.setSequenceNumber(step.getSequenceNumber() + 1));
        return sequence;
    }

    private int nextSequence(Goal goal) {
        return visionStepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(goal.getId(), lookup.userId()).stream()
                .map(VisionStep::getSequenceNumber)
                .max(Comparator.naturalOrder())
                .orElse(0) + 1;
    }

    @Nullable
    private PartnerResponse maybeCreatePartner(VisionStep step, GapStepRequest request, String gap) {
        if (!request.createPartner()) {
            return null;
        }
        PartnerSupportType supportType = request.partnerSupportType() != null
                ? request.partnerSupportType()
                : supportFor(step.getGapKind());
        String name = isBlank(request.partnerName())
                ? truncate("To find: " + step.getTitle(), MAX_PARTNER_NAME)
                : request.partnerName().strip();
        PartnerResponse partner = partnerService.createPartner(new PartnerRequest(name, null, null, null, null, null,
                supportType, null, null, null, null, step.getId(), null, PartnerStatus.TO_CONTACT,
                truncate("Found to cover a gap: " + gap, 3000), null, null, null, null, null, null, null, null, null, null));
        step.setCreatedPartner(lookup.partner(partner.id()));
        return partner;
    }

    /** The suggested helper type for a kind of gap, or Other when there is no clear match. */
    static PartnerSupportType supportFor(@Nullable ObstacleType gapKind) {
        return gapKind == null ? PartnerSupportType.OTHER : SUPPORT_FOR_GAP.getOrDefault(gapKind, PartnerSupportType.OTHER);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1).strip() + "…";
    }
}
