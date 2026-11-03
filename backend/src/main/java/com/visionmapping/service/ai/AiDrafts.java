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

    // Phase 6: daily use.

    public record PickDraft(
            @JsonPropertyDescription("STEP or TASK") String itemType,
            @JsonPropertyDescription("The id shown in brackets") long itemId,
            @JsonPropertyDescription("Why this fits now, in one short sentence") String reason,
            @JsonPropertyDescription("Minutes it should take, as a whole number") int minutes) {
    }

    public record NextStepDraft(
            @JsonPropertyDescription("Best first, at most three") List<PickDraft> picks,
            @JsonPropertyDescription("One short encouraging line; empty if nothing fits") String note) {
    }

    public record WeeklyReviewDraft(
            @JsonPropertyDescription("Two or three sentences on how the week went") String summary,
            @JsonPropertyDescription("What was finished, one item per line") String completedTasks,
            @JsonPropertyDescription("What is late, one item per line") String delayedTasks,
            @JsonPropertyDescription("What is blocked and why, one item per line") String blockedTasks,
            @JsonPropertyDescription("One or two lessons, phrased as the participant's own reflection") String lessonsLearned,
            @JsonPropertyDescription("The top three priorities for next week, one per line") String nextActions) {
    }

    public record PitchFeedbackDraft(
            @JsonPropertyDescription("How the listener honestly reacts, in their voice, two or three sentences") String reaction,
            @JsonPropertyDescription("1 to 5: how clear the pitch is") int clarity,
            @JsonPropertyDescription("1 to 5: how clear the benefit to the listener is") int benefit,
            @JsonPropertyDescription("1 to 5: how specific and easy to say yes to the ask is") int ask,
            @JsonPropertyDescription("The pitch rewritten, keeping the participant's facts and voice") String improvedPitch,
            @JsonPropertyDescription("The one change that would help most") String tip) {
    }

    public record OutreachDraft(
            @JsonPropertyDescription("A short, specific subject line") String subject,
            @JsonPropertyDescription("The full message: warm, brief, one clear ask, never demanding") String message) {
    }

    public record ResourceDraft(
            @JsonPropertyDescription("COURSE, BOOK, TOOL, ORGANISATION, FUNDING, PERSON or OTHER") String kind,
            @JsonPropertyDescription("A well-known name, or for PERSON the kind of person as a role") String name,
            @JsonPropertyDescription("How it helps this goal, in one sentence") String why) {
    }

    public record ResourceDrafts(List<ResourceDraft> resources) {
    }

    // Phase 7: module help.

    public record FieldValue(
            @JsonPropertyDescription("The field key exactly as listed") String key,
            @JsonPropertyDescription("The suggested answer for that field") String value) {
    }

    public record RowDraft(List<FieldValue> values) {
    }

    public record ModuleHelpDraft(
            @JsonPropertyDescription("Up to three short questions to think through, most useful first; empty when not asked for") List<String> questions,
            @JsonPropertyDescription("A short example answer about a made-up person, not the participant; empty when not asked for") String example,
            @JsonPropertyDescription("Draft answers for the exercise, at most three rows; empty when not asked for") List<RowDraft> rows,
            @JsonPropertyDescription("Two or three sentences of honest, kind feedback; empty when not asked for") String feedback,
            @JsonPropertyDescription("Up to three specific improvements; empty when not asked for") List<String> improvements) {
    }

    public record AssessmentExplanationDraft(
            @JsonPropertyDescription("Three to five plain sentences on what the scores say, strongest and weakest first") String explanation,
            @JsonPropertyDescription("The number of the one module to focus on next, 1 to 15") int focusModule,
            @JsonPropertyDescription("Why that module, in one sentence") String reason) {
    }

    // Phase 8: coach co-pilot.

    public record CoachSummaryDraft(
            @JsonPropertyDescription("Progress against the plan this week, two to four sentences") String progress,
            @JsonPropertyDescription("Mood trend from attitude check-ins; empty when there are none in the records") String mood,
            @JsonPropertyDescription("Risks, each with the reason from the records; empty when nothing stands out") String risks,
            @JsonPropertyDescription("One specific action the coach could take this week") String suggestedAction) {
    }

    public record CoachFeedbackDraft(
            @JsonPropertyDescription("The comment, in the coach's voice: one thing done well, one concrete suggestion, one question") String message) {
    }

    public record CohortSummaryDraft(
            @JsonPropertyDescription("The cohort's week in two or three sentences") String overview,
            @JsonPropertyDescription("Blockers several participants share, one per line") String commonBlockers,
            @JsonPropertyDescription("Modules where people are stuck or slow, one per line") String strugglingModules,
            @JsonPropertyDescription("Up to three actions for the coach, one per line") String suggestedActions) {
    }
}
