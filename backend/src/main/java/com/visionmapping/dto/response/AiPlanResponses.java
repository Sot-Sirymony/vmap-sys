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

    /**
     * V11 (PB-FR-12) adds priority, complex, estimatedHours and startsAfter:
     * the 1-based position in this list of an earlier step this one starts
     * after, or null.
     */
    public record StepSuggestion(String title, String description, String owner, LocalDate targetDate,
                                 String resources, String people, String reason, Priority priority, boolean complex,
                                 java.math.BigDecimal estimatedHours, Integer startsAfter) {
    }

    public record StepSuggestions(List<StepSuggestion> steps, String clarifyingQuestion, Long requestId) {
    }

    /** V11 (PB-FR-14): one suggested task for a complex step. */
    public record TaskSuggestion(String title, String owner, LocalDate dueDate, Priority priority,
                                 java.math.BigDecimal estimatedHours, String reason) {
    }

    public record TaskSuggestions(List<TaskSuggestion> tasks, String clarifyingQuestion, Long requestId) {
    }

    /** V11 (PB-FR-23): one of the participant's own partners who may fit a helper. */
    public record PartnerMatch(Long partnerId, String name, String role, String supportType, String status) {
    }

    /** V11 (PB-FR-23): a kind of helper the plan needs, and the participant's partners who match by role or support type. */
    public record HelperSuggestion(String role, String supportType, String forKey, String reason, List<PartnerMatch> matches) {
    }

    /**
     * V11 (PB-FR-24 to PB-FR-26): a likely obstacle. helperType and helperLabel
     * are the kind of partner that fits it (CLAUDE.md); fromFears says it came
     * from the participant's fear journal.
     */
    public record RiskSuggestion(String title, String description, String obstacleType, String severity, String solution,
                                 String forKey, String helperType, String helperLabel, boolean fromFears) {
    }

    public record HelpersAndRisks(List<HelperSuggestion> helpers, List<RiskSuggestion> risks,
                                  List<com.visionmapping.dto.response.AiDailyResponses.ResourceIdea> resources, Long requestId) {
    }

    /**
     * V11 (PB-FR-28, PB-FR-29): a problem in the Build my plan draft, before it
     * is saved. itemKey and parentKey are draft keys. fixAction says what one
     * click does: SET_OWNER_ME, SET_DATE (to fixValue), GO_TO_STEPS, GO_TO_TASKS,
     * or FIX_WITH_AI for the kinds only Claude finds; null when there is no
     * clear fix.
     */
    public record DraftIssue(String kind, String itemType, String itemKey, String parentKey, String itemTitle, String problem,
                             String fix, String fixAction, String fixValue, boolean fromAi) {
    }

    public record DraftCheck(List<DraftIssue> issues, boolean aiChecked, Long requestId) {
    }

    /**
     * V11 (PB-FR-29): Claude's proposed change for one AI issue, applied only
     * when the participant chooses: a clearer title, a new date, or for a gap a
     * new step under the goal.
     */
    public record DraftFix(String itemKey, String kind, String title, LocalDate targetDate, String newStepTitle,
                           String newStepOwner, LocalDate newStepTargetDate, String note, Long requestId) {
    }

    /** V11 (PB-FR-16): hours already planned in one week (Monday start) of the saved plan. */
    public record WeekLoad(LocalDate weekStart, java.math.BigDecimal hours) {
    }

    public record PlanCapacity(java.math.BigDecimal hoursPerWeek, List<WeekLoad> planned) {
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

    /**
     * V11 (PB-FR-08): the next clarifying question about the dream, or done.
     * field says which dream field the answer belongs in (WHY_IMPORTANT,
     * SUCCESS_DEFINITION, TARGET_DATE), or null, so it can be saved there (PB-FR-10).
     */
    public record ClarifyQuestion(String question, String field, boolean done, Long requestId) {
    }

    /** V11 (PB-FR-31): the ids of everything one Add saved, kept by the client for Undo. */
    /**
     * V11 (PB-FR-31): partnerIds and obstacleIds were created and are archived
     * by Undo; linkedPartnerIds are existing partners given a link to a goal or
     * step that had none, which Undo leaves as they are.
     */
    public record PlanAddResult(List<Long> goalIds, List<Long> stepIds, List<Long> taskIds, List<Long> partnerIds,
                                List<Long> obstacleIds, List<Long> linkedPartnerIds) {

        public PlanAddResult(List<Long> goalIds, List<Long> stepIds) {
            this(goalIds, stepIds, List.of(), List.of(), List.of(), List.of());
        }
    }

    /** V11 (PB-FR-36): one plan builder feature over the chosen period. Rates are 0 to 1, null when there is nothing to divide by. */
    public record PlanFeatureMetrics(String feature, long requests, long suggestedItems, long addedItems, long editedItems,
                                     Double acceptanceRate, Double editRate, long ratings, Double thumbsUpRate,
                                     java.math.BigDecimal costUsd, java.math.BigDecimal averageCostUsd) {
    }

    public record PlanMetrics(int days, List<PlanFeatureMetrics> features) {
    }
    // V11 Phase 7: the plan assistant (PB-FR-41 to PB-FR-50).

    /**
     * One rule-based problem in a section. itemRef is "goal:12" or "step:34"
     * on the Plan page and the draft key in Build my plan. No AI is used.
     */
    public record SectionIssue(String kind, String itemType, String itemRef, String itemTitle, String problem, String fix) {
    }

    /** PB-FR-49: what the panel shows for a section, with or without AI. canPropose says the section takes change cards. */
    public record SectionCheck(String surface, String section, List<SectionIssue> issues, boolean canPropose) {
    }

    /**
     * PB-FR-46: one proposed change. kind EDIT changes field (TITLE, SUCCESS,
     * WHY, DATE or OWNER) of itemRef to value (a date as YYYY-MM-DD).
     * ADD_STEP and ADD_TASK add title, owner and date under parentRef; ADD_RISK
     * adds an obstacle, optionally tied to parentRef. itemTitle and parentTitle
     * are the current titles, for display. date is null when Claude's date
     * broke a limit, so the participant picks one.
     */
    public record AssistantChange(String kind, String itemType, String itemRef, String itemTitle, String field, String value,
                                  String parentRef, String parentType, String parentTitle, String title, String owner,
                                  LocalDate date, String obstacleType, String severity, String solution, String reason) {
    }

    /** requestId: the answer to rate and count outcomes against (PB-FR-48). */
    public record AssistantProposals(List<AssistantChange> changes, String note, Long requestId) {
    }

    /** PB-FR-46: what a saved change card became on the Plan page. */
    public record AssistantApplied(String itemType, Long id) {
    }
}
