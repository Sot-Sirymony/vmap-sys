package com.visionmapping.dto.response;

import com.visionmapping.entity.enums.Priority;
import java.time.LocalDate;
import java.util.List;

/**
 * V9 Phase 5 (AI-FR-07 to AI-FR-11): what the goal and plan builders return.
 * Every item is a suggestion: nothing here is saved until the participant
 * adds it (AI-FR-04, AC-09).
 */
public final class AiPlanResponses {

    private AiPlanResponses() {
    }

    public record GoalSuggestion(String title, String description, String successCriteria, LocalDate targetDate,
                                 Priority priority, boolean moonshot, String reason) {
    }

    public record GoalSuggestions(List<GoalSuggestion> goals, String clarifyingQuestion) {
    }

    public record StepSuggestion(String title, String description, String owner, LocalDate targetDate,
                                 String resources, String people, String reason) {
    }

    public record StepSuggestions(List<StepSuggestion> steps, String clarifyingQuestion) {
    }

    /** kind: MISSING_OWNER, MISSING_DATE, MISSING_RESOURCE, NO_STEPS, AFTER_GOAL_DATE, OVERDUE, VAGUE_STEP, UNREALISTIC_TIMING, GAP. */
    public record PlanIssue(String kind, String itemType, Long itemId, String itemTitle, String problem, String fix, boolean fromAi) {
    }

    /** aiChecked is false when AI could not run; the rule-based checks still did. */
    public record PlanCheck(List<PlanIssue> issues, boolean aiChecked) {
    }

    public record SlippingStep(Long stepId, String title, Long goalId, String goalTitle, LocalDate targetDate, int overdueReschedules) {
    }

    public record StepRescue(Long stepId, String smallerStep, LocalDate newDate, String helper, String reason) {
    }
}
