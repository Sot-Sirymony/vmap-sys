package com.visionmapping.service.ai;

import com.visionmapping.dto.request.AiPlanAddRequest;
import com.visionmapping.dto.request.AiPlanOutcomeRequest;
import com.visionmapping.dto.request.AiPlanUndoRequest;
import com.visionmapping.dto.request.AiRatingRequest;
import com.visionmapping.dto.response.AiPlanResponses.PlanAddResult;
import com.visionmapping.dto.response.AiPlanResponses.PlanFeatureMetrics;
import com.visionmapping.dto.response.AiPlanResponses.PlanMetrics;
import com.visionmapping.entity.AiUsageLog;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.repository.AiUsageLogRepository;
import com.visionmapping.security.Roles;
import com.visionmapping.service.GoalService;
import com.visionmapping.service.VisionStepService;
import com.visionmapping.service.support.EntityLookup;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V11 Phase 1: what happens to plan builder suggestions after they are shown.
 * The participant rates an answer (PB-FR-34); the counts of items added as
 * they were, added after editing, and (by difference) discarded are kept on
 * the answer's usage-log row without any text (PB-FR-35); kept goals and steps
 * are saved in one all-or-nothing request (PB-FR-31) that can be undone
 * (PB-FR-32); admins see the totals per feature (PB-FR-36).
 */
@Service
@RequiredArgsConstructor
public class AiPlanFeedbackService {

    /** The features whose answers are plan builder suggestions. */
    static final Set<String> PLAN_FEATURES = Set.of(
            "AREA_BUILDER", "DREAM_BUILDER", "STRETCH_DREAM", "GOAL_BUILDER", "STRETCH_GOAL", "PLAN_BUILDER", "PLAN_CHECK");
    private static final Set<String> GOAL_FEATURES = Set.of("GOAL_BUILDER", "STRETCH_GOAL");
    private static final Set<String> STEP_FEATURES = Set.of("PLAN_BUILDER");
    static final int MAX_METRIC_DAYS = 365;

    private final AiUsageLogRepository usageLogRepository;
    private final EntityLookup lookup;
    private final GoalService goalService;
    private final VisionStepService stepService;
    private final Clock clock;

    /** Records how many items an answer suggested and returns its id for the client. */
    @Transactional
    public Long suggested(AiUsageLog usage, int count) {
        usage.setSuggestedItems(count);
        return usageLogRepository.save(usage).getId();
    }

    /** PB-FR-34: thumbs up (1), down (-1) or cleared (0) on one plan builder answer. */
    @Transactional
    public void rate(Long requestId, AiRatingRequest request) {
        AiUsageLog usage = ownPlanRequest(requestId);
        usage.setRating(request.rating() == 0 ? null : request.rating());
        usage.setRatingComment(request.rating() == 0 || request.comment() == null || request.comment().isBlank()
                ? null : request.comment().trim());
    }

    /** PB-FR-35: outcomes for suggestions saved through the normal create endpoints (vision areas, dreams). */
    @Transactional
    public void recordOutcome(Long requestId, AiPlanOutcomeRequest request) {
        addOutcome(ownPlanRequest(requestId), request.added(), request.edited());
    }

    /**
     * PB-FR-31: every kept goal and step in one transaction, through the same
     * services (and so the same validation and ownership checks) as the normal
     * create endpoints. If one fails, nothing is saved.
     */
    @Transactional
    public PlanAddResult add(AiPlanAddRequest request) {
        List<AiPlanAddRequest.GoalItem> goals = Optional.ofNullable(request.goals()).orElse(List.of());
        List<AiPlanAddRequest.StepItem> steps = Optional.ofNullable(request.steps()).orElse(List.of());
        if (goals.isEmpty() && steps.isEmpty()) {
            throw new BusinessRuleException("Choose at least one goal or step to add.");
        }
        AiUsageLog usage = null;
        if (request.requestId() != null) {
            usage = ownPlanRequest(request.requestId());
            Set<String> allowed = steps.isEmpty() ? GOAL_FEATURES : goals.isEmpty() ? STEP_FEATURES : Set.of();
            if (!allowed.contains(usage.getFeature())) {
                throw new BusinessRuleException("These items do not match the suggestions they were said to come from.");
            }
        }
        List<Long> goalIds = new ArrayList<>();
        for (AiPlanAddRequest.GoalItem item : goals) {
            goalIds.add(goalService.createGoal(item.goal()).id());
        }
        List<Long> stepIds = new ArrayList<>();
        for (AiPlanAddRequest.StepItem item : steps) {
            stepIds.add(stepService.createStep(item.step()).id());
        }
        if (usage != null) {
            int edited = (int) (goals.stream().filter(AiPlanAddRequest.GoalItem::edited).count()
                    + steps.stream().filter(AiPlanAddRequest.StepItem::edited).count());
            addOutcome(usage, goals.size() + steps.size() - edited, edited);
        }
        return new PlanAddResult(goalIds, stepIds);
    }

    /**
     * PB-FR-32: archives (soft delete, BR-12) the goals and steps one Add saved.
     * Each id is looked up as the caller's own, so another user's id answers 404.
     * The answer's outcome counts go back to zero, since nothing was kept.
     */
    @Transactional
    public void undo(AiPlanUndoRequest request) {
        List<Long> goalIds = Optional.ofNullable(request.goalIds()).orElse(List.of());
        List<Long> stepIds = Optional.ofNullable(request.stepIds()).orElse(List.of());
        AiUsageLog usage = request.requestId() == null ? null : ownPlanRequest(request.requestId());
        // Look every id up first, so one wrong id changes nothing.
        List<VisionStep> stepsToArchive = stepIds.stream().map(lookup::step).toList();
        List<Goal> goalsToArchive = goalIds.stream().map(lookup::goal).toList();
        for (VisionStep step : stepsToArchive) {
            if (!step.isArchived()) {
                stepService.archiveStep(step.getId());
            }
        }
        for (Goal goal : goalsToArchive) {
            if (!goal.isArchived()) {
                goalService.archiveGoal(goal.getId());
            }
        }
        if (usage != null) {
            usage.setAddedItems(0);
            usage.setEditedItems(0);
        }
    }

    /** PB-FR-36: per plan builder feature over the last days, for admins only. */
    @Transactional(readOnly = true)
    public PlanMetrics metrics(int days) {
        Roles.requireAdmin(lookup.currentUser(), "Only an administrator can see AI plan builder metrics.");
        int period = Math.max(1, Math.min(days, MAX_METRIC_DAYS));
        Instant from = Instant.now(clock).minus(Duration.ofDays(period));
        List<PlanFeatureMetrics> features = new ArrayList<>();
        for (Object[] row : usageLogRepository.planMetricsSince(from, PLAN_FEATURES)) {
            String feature = (String) row[0];
            long requests = number(row[1]);
            long suggestedItems = number(row[2]);
            long added = number(row[3]);
            long edited = number(row[4]);
            long ratings = number(row[5]);
            long thumbsUp = number(row[6]);
            BigDecimal cost = row[7] == null ? BigDecimal.ZERO : (BigDecimal) row[7];
            features.add(new PlanFeatureMetrics(feature, requests, suggestedItems, added, edited,
                    rate(added + edited, suggestedItems), rate(edited, added + edited), ratings, rate(thumbsUp, ratings),
                    cost, requests == 0 ? BigDecimal.ZERO : cost.divide(BigDecimal.valueOf(requests), 6, RoundingMode.HALF_UP)));
        }
        features.sort((left, right) -> left.feature().compareTo(right.feature()));
        return new PlanMetrics(period, features);
    }

    /** The caller's own plan builder answer; anything else answers 404, the same as one that does not exist. */
    private AiUsageLog ownPlanRequest(Long requestId) {
        Long userId = lookup.userId();
        return usageLogRepository.findById(requestId)
                .filter(usage -> usage.getUser() != null && Objects.equals(usage.getUser().getId(), userId))
                .filter(usage -> PLAN_FEATURES.contains(usage.getFeature()))
                .orElseThrow(() -> new ResourceNotFoundException("AI suggestion not found: " + requestId));
    }

    /** Adds to the counts, never past what the answer suggested. */
    private static void addOutcome(AiUsageLog usage, int added, int edited) {
        if (usage.getSuggestedItems() == null) {
            throw new BusinessRuleException("This AI answer had no suggestions to add.");
        }
        int room = Math.max(0, usage.getSuggestedItems() - usage.getAddedItems() - usage.getEditedItems());
        int keptEdited = Math.min(edited, room);
        int keptAdded = Math.min(added, room - keptEdited);
        usage.setEditedItems(usage.getEditedItems() + keptEdited);
        usage.setAddedItems(usage.getAddedItems() + keptAdded);
    }

    private static Double rate(long part, long whole) {
        return whole == 0 ? null : Math.round(part * 1000.0 / whole) / 1000.0;
    }

    private static long number(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }
}
