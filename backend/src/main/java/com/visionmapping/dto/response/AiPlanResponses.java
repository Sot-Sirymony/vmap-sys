package com.visionmapping.dto.response;

import com.visionmapping.entity.enums.DreamType;
import com.visionmapping.entity.enums.Priority;
import java.time.LocalDate;
import java.util.List;

/**
 * V9 Phase 5 (AI-FR-07 to AI-FR-11): what the goal and plan builders return,
 * plus the vision area and dream builders that start a plan.
 * Every item is a suggestion: nothing here is saved until the participant
 * adds it (AI-FR-04, AC-09).
 */
public final class AiPlanResponses {

    private AiPlanResponses() {
    }

    public record VisionAreaSuggestion(String name, String description, String visionStatement, Priority priority, String reason) {
    }

    /** requestId (V11, PB-FR-34): the AI request to rate and report outcomes against. */
    public record VisionAreaSuggestions(List<VisionAreaSuggestion> areas, Long requestId) {
    }

    public record DreamSuggestion(String title, String description, String whyImportant, String successDefinition,
                                  DreamType dreamType, Priority priority, LocalDate targetDate, boolean moonshot, String reason) {
    }

    public record DreamSuggestions(List<DreamSuggestion> dreams, String clarifyingQuestion, Long requestId) {
    }

    public record GoalSuggestion(String title, String description, String successCriteria, LocalDate targetDate,
                                 Priority priority, boolean moonshot, String reason) {
    }

    public record GoalSuggestions(List<GoalSuggestion> goals, String clarifyingQuestion, Long requestId) {
    }

    public record StepSuggestion(String title, String description, String owner, LocalDate targetDate,
                                 String resources, String people, String reason) {
    }

    public record StepSuggestions(List<StepSuggestion> steps, String clarifyingQuestion, Long requestId) {
    }

    /** kind: MISSING_OWNER, MISSING_DATE, MISSING_RESOURCE, NO_STEPS, AFTER_GOAL_DATE, OVERDUE, VAGUE_STEP, UNREALISTIC_TIMING, GAP. */
    public record PlanIssue(String kind, String itemType, Long itemId, String itemTitle, String problem, String fix, boolean fromAi) {
    }

    /** aiChecked is false when AI could not run; the rule-based checks still did. requestId is null then. */
    public record PlanCheck(List<PlanIssue> issues, boolean aiChecked, Long requestId) {
    }

    public record SlippingStep(Long stepId, String title, Long goalId, String goalTitle, LocalDate targetDate, int overdueReschedules) {
    }

    public record StepRescue(Long stepId, String smallerStep, LocalDate newDate, String helper, String reason) {
    }

    /** V11 (PB-FR-31): the ids of everything one Add saved, kept by the client for Undo. */
    public record PlanAddResult(List<Long> goalIds, List<Long> stepIds) {
    }

    /** V11 (PB-FR-36): one plan builder feature over the chosen period. Rates are 0 to 1, null when there is nothing to divide by. */
    public record PlanFeatureMetrics(String feature, long requests, long suggestedItems, long addedItems, long editedItems,
                                     Double acceptanceRate, Double editRate, long ratings, Double thumbsUpRate,
                                     java.math.BigDecimal costUsd, java.math.BigDecimal averageCostUsd) {
    }

    public record PlanMetrics(int days, List<PlanFeatureMetrics> features) {
    }
}
