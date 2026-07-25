package com.visionmapping.service;

import com.visionmapping.dto.request.GoalSynergyLinkRequest;
import com.visionmapping.dto.response.GoalSynergyLinkResponse;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.GoalSynergyLink;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.repository.GoalSynergyLinkRepository;
import com.visionmapping.service.support.EntityLookup;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-35: goal synergy links. Create, list, and remove links between two of the
 * user's own goals (BR-29: distinct goals, no duplicate pair, user-scoped).
 * Pairs are stored normalised — lower goal id in {@code goal}, higher in
 * {@code relatedGoal} — so (A,B) and (B,A) are one row, read from either side.
 * Informational only: nothing here touches progress, status, or archival.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class GoalSynergyLinkService {

    private final EntityLookup lookup;
    private final GoalSynergyLinkRepository repository;

    @Transactional(readOnly = true)
    public List<GoalSynergyLinkResponse> listLinks(Long goalId) {
        Goal goal = lookup.goal(goalId); // ownership check (BR-29 user scope)
        return repository.findForGoal(lookup.userId(), goalId).stream()
                .map(link -> toResponse(goal, link))
                .filter(Objects::nonNull)
                .toList();
    }

    public GoalSynergyLinkResponse createLink(Long goalId, GoalSynergyLinkRequest request) {
        Goal goal = lookup.goal(goalId);
        if (goalId.equals(request.relatedGoalId())) {
            throw new BusinessRuleException("A goal cannot be linked to itself.");
        }
        Goal related = lookup.goal(request.relatedGoalId()); // ownership check (BR-29 user scope)
        // Normalise the pair so ordering can never create a duplicate row.
        Goal lower = goal.getId() < related.getId() ? goal : related;
        Goal higher = goal.getId() < related.getId() ? related : goal;
        if (repository.existsByUser_IdAndGoal_IdAndRelatedGoal_Id(lookup.userId(), lower.getId(), higher.getId())) {
            throw new BusinessRuleException("These goals are already linked.");
        }
        GoalSynergyLink link = GoalSynergyLink.builder()
                .user(lookup.currentUser())
                .goal(lower)
                .relatedGoal(higher)
                .note(request.note())
                .build();
        return toResponse(goal, repository.save(link));
    }

    public void deleteLink(Long linkId) {
        GoalSynergyLink link = repository.findById(linkId)
                .filter(entity -> entity.getUser().getId().equals(lookup.userId()))
                .orElseThrow(() -> new ResourceNotFoundException("Synergy link not found: " + linkId));
        repository.delete(link);
    }

    /**
     * Perspective-aware: the "related" goal is whichever end isn't the goal being
     * viewed. Returns null when that other goal is archived, so an archived goal's
     * links drop out of the listing (no dangling links) without the row itself
     * being destroyed — it comes back when the goal is restored.
     */
    private GoalSynergyLinkResponse toResponse(Goal viewed, GoalSynergyLink link) {
        Goal other = link.getGoal().getId().equals(viewed.getId()) ? link.getRelatedGoal() : link.getGoal();
        if (other.isArchived()) {
            return null;
        }
        boolean crossArea = !areaId(viewed).equals(areaId(other));
        return new GoalSynergyLinkResponse(
                link.getId(), viewed.getId(), other.getId(), other.getCode(), other.getTitle(),
                other.getDream().getVisionArea().getName(), crossArea, link.getNote(), link.getCreatedAt());
    }

    private static Long areaId(Goal goal) {
        return goal.getDream().getVisionArea().getId();
    }
}
