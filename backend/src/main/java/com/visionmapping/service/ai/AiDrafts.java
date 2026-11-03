package com.visionmapping.service.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * V9 Phase 5: the JSON shapes Claude must answer in (structured output). They
 * are drafts only; AiPlanService checks every field before a participant sees
 * it, and nothing is saved until the participant chooses to (AI-FR-04).
 */
public final class AiDrafts {

    private AiDrafts() {
    }

    public record GoalDraft(
            @JsonPropertyDescription("A specific, measurable goal in under 120 characters") String title,
            @JsonPropertyDescription("One or two sentences on what doing this involves") String description,
            @JsonPropertyDescription("How the participant will know it is done, with a number or clear test") String successCriteria,
            @JsonPropertyDescription("Target date as YYYY-MM-DD") String targetDate,
            @JsonPropertyDescription("LOW, MEDIUM, HIGH or CRITICAL") String priority,
            @JsonPropertyDescription("One short sentence on why this goal serves the dream") String reason) {
    }

    public record GoalDrafts(
            List<GoalDraft> goals,
            @JsonPropertyDescription("One question to ask first when the dream is too vague to plan; empty otherwise") String clarifyingQuestion) {
    }

    public record StepDraft(
            @JsonPropertyDescription("One concrete action in under 120 characters, starting with a verb") String title,
            @JsonPropertyDescription("What doing it involves, in one or two sentences") String description,
            @JsonPropertyDescription("Who does it: Me, or a role such as Accountant. Never a made-up person's name") String owner,
            @JsonPropertyDescription("Due date as YYYY-MM-DD") String targetDate,
            @JsonPropertyDescription("Money, tools, skills or information the step needs") String resources,
            @JsonPropertyDescription("Kinds of people who could help, as roles, never names") String people,
            @JsonPropertyDescription("One short sentence on why this step is needed") String reason) {
    }

    public record StepDrafts(
            List<StepDraft> steps,
            @JsonPropertyDescription("One question to ask first when the goal is too vague to plan; empty otherwise") String clarifyingQuestion) {
    }

    public record IssueDraft(
            @JsonPropertyDescription("VAGUE_STEP, UNREALISTIC_TIMING or GAP") String kind,
            @JsonPropertyDescription("GOAL or STEP") String itemType,
            @JsonPropertyDescription("The id shown in brackets for that goal or step") long itemId,
            @JsonPropertyDescription("The problem in one sentence") String problem,
            @JsonPropertyDescription("A concrete fix in one sentence") String fix) {
    }

    public record PlanReview(List<IssueDraft> issues) {
    }

    public record RescueDraft(
            @JsonPropertyDescription("A smaller first step that could be done within a few days") String smallerStep,
            @JsonPropertyDescription("A realistic new due date as YYYY-MM-DD") String newDate,
            @JsonPropertyDescription("The kind of person who could help, as a role, never a name") String helper,
            @JsonPropertyDescription("One short, kind sentence on why the step may keep slipping") String reason) {
    }
}
