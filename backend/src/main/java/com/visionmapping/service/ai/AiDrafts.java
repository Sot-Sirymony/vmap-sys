package com.visionmapping.service.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.visionmapping.util.Copies;
import java.util.List;

/**
 * V9 Phase 5: the JSON shapes Claude must answer in (structured output). They
 * are drafts only; AiPlanService checks every field before a participant sees
 * it, and nothing is saved until the participant chooses to (AI-FR-04).
 */
public final class AiDrafts {

    private AiDrafts() {
    }

    public record AreaDraft(
            @JsonPropertyDescription("A short area name such as Health or Career, under 40 characters") String name,
            @JsonPropertyDescription("One sentence on what this area covers") String description,
            @JsonPropertyDescription("A vision statement in the participant's voice, present tense, one or two sentences") String visionStatement,
            @JsonPropertyDescription("LOW, MEDIUM, HIGH or CRITICAL") String priority,
            @JsonPropertyDescription("One short sentence on why this area may matter to the participant") String reason) {
    }

    public record AreaDrafts(List<AreaDraft> areas) {
        public AreaDrafts {
            areas = Copies.list(areas);
        }
    
    }

    public record DreamDraft(
            @JsonPropertyDescription("A clear, meaningful dream in under 120 characters") String title,
            @JsonPropertyDescription("One or two sentences describing the dream") String description,
            @JsonPropertyDescription("Why this dream could matter to the participant, in their voice") String whyImportant,
            @JsonPropertyDescription("What success looks like, concrete enough to know when it is reached") String successDefinition,
            @JsonPropertyDescription("SHORT_TERM, LONG_TERM or LIFETIME") String dreamType,
            @JsonPropertyDescription("LOW, MEDIUM, HIGH or CRITICAL") String priority,
            @JsonPropertyDescription("Target date as YYYY-MM-DD; empty for a lifetime dream") String targetDate,
            @JsonPropertyDescription("One short sentence on why this dream fits the area") String reason) {
    }

    public record DreamDrafts(
            List<DreamDraft> dreams,
            @JsonPropertyDescription("One question to ask first when too little is known to suggest dreams; empty otherwise") String clarifyingQuestion) {
        public DreamDrafts {
            dreams = Copies.list(dreams);
        }
    
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
        public GoalDrafts {
            goals = Copies.list(goals);
        }
    
    }

    public record StepDraft(
            @JsonPropertyDescription("One concrete action in under 120 characters, starting with a verb") String title,
            @JsonPropertyDescription("What doing it involves, in one or two sentences") String description,
            @JsonPropertyDescription("Who does it: Me, or a role such as Accountant. Never a made-up person's name") String owner,
            @JsonPropertyDescription("Due date as YYYY-MM-DD") String targetDate,
            @JsonPropertyDescription("Money, tools, skills or information the step needs") String resources,
            @JsonPropertyDescription("Kinds of people who could help, as roles, never names") String people,
            @JsonPropertyDescription("One short sentence on why this step is needed") String reason,
            // V11 Phase 4 (PB-FR-12).
            @JsonPropertyDescription("LOW, MEDIUM, HIGH or CRITICAL") String priority,
            @JsonPropertyDescription("True when the step needs several separate tasks; false when it is one action") boolean complex,
            @JsonPropertyDescription("Estimated effort in hours, a number such as 2 or 0.5") Double estimatedHours,
            @JsonPropertyDescription("The number (1 = first) of an earlier step in this list that must finish before this one starts; 0 when none") int startsAfter) {

        /** The V9 shape, before effort and order were asked for. */
        public StepDraft(String title, String description, String owner, String targetDate, String resources, String people,
                         String reason) {
            this(title, description, owner, targetDate, resources, people, reason, null, false, null, 0);
        }
    }

    public record StepDrafts(
            List<StepDraft> steps,
            @JsonPropertyDescription("One question to ask first when the goal is too vague to plan; empty otherwise") String clarifyingQuestion) {
        public StepDrafts {
            steps = Copies.list(steps);
        }
    
    }

    // V11 Phase 4: the task builder (PB-FR-14).

    public record TaskDraft(
            @JsonPropertyDescription("One small action in under 120 characters, starting with a verb, doable in one sitting") String title,
            @JsonPropertyDescription("Who does it: Me, or a role such as Accountant. Never a made-up person's name") String owner,
            @JsonPropertyDescription("Due date as YYYY-MM-DD") String dueDate,
            @JsonPropertyDescription("LOW, MEDIUM, HIGH or CRITICAL") String priority,
            @JsonPropertyDescription("Estimated effort in hours, a number such as 1 or 0.5") Double estimatedHours,
            @JsonPropertyDescription("One short sentence on why this task is needed") String reason) {
    }

    public record TaskDrafts(
            List<TaskDraft> tasks,
            @JsonPropertyDescription("One question to ask first when the step is too vague to break down; empty otherwise") String clarifyingQuestion) {
        public TaskDrafts {
            tasks = Copies.list(tasks);
        }
    
    }

    // V11 Phase 5: helpers, risks and resources (PB-FR-23 to PB-FR-26, PB-FR-40).

    public record HelperDraft(
            @JsonPropertyDescription("The kind of person needed, as a role such as Mortgage adviser. Never a name") String role,
            @JsonPropertyDescription("MENTOR, EXPERT, ADVISOR, COLLEAGUE, FINANCIAL, TECHNICAL, EMOTIONAL or OTHER") String supportType,
            @JsonPropertyDescription("The key in brackets of the goal or step this helper is for; empty for the whole dream") String forKey,
            @JsonPropertyDescription("One short sentence on what they would help with") String reason) {
    }

    public record RiskDraft(
            @JsonPropertyDescription("The obstacle in under 120 characters") String title,
            @JsonPropertyDescription("One or two sentences on how it could block the plan") String description,
            @JsonPropertyDescription("KNOWLEDGE, SKILL, TIME, MONEY, MOTIVATION, PARTNER, SYSTEM, DECISION or OTHER") String obstacleType,
            @JsonPropertyDescription("LOW, MEDIUM, HIGH or CRITICAL") String severity,
            @JsonPropertyDescription("A practical first way to handle it, in one or two sentences") String solution,
            @JsonPropertyDescription("The key in brackets of the goal or step it threatens; empty for the whole dream") String forKey,
            @JsonPropertyDescription("True only when it comes from the participant's fear journal") boolean fromFears) {
    }

    public record HelpersRisksDraft(
            @JsonPropertyDescription("Up to five kinds of helpers the plan needs") List<HelperDraft> helpers,
            @JsonPropertyDescription("Up to five likely obstacles") List<RiskDraft> risks,
            @JsonPropertyDescription("Up to six resources") List<ResourceDraft> resources) {
        public HelpersRisksDraft {
            helpers = Copies.list(helpers);
            risks = Copies.list(risks);
            resources = Copies.list(resources);
        }
    
    }

    // V11 Phase 6: the draft check and its AI fixes (PB-FR-28, PB-FR-29).

    public record DraftIssueDraft(
            @JsonPropertyDescription("VAGUE_STEP, UNREALISTIC_TIMING or GAP") String kind,
            @JsonPropertyDescription("The key in brackets of the goal or step") String itemKey,
            @JsonPropertyDescription("The problem in one sentence") String problem,
            @JsonPropertyDescription("A concrete fix in one sentence") String fix) {
    }

    public record DraftReview(List<DraftIssueDraft> issues) {
        public DraftReview {
            issues = Copies.list(issues);
        }
    
    }

    public record FixDraft(
            @JsonPropertyDescription("For a vague step: a clearer title starting with a verb; empty otherwise") String title,
            @JsonPropertyDescription("For timing: a realistic new date as YYYY-MM-DD; empty otherwise") String targetDate,
            @JsonPropertyDescription("For a gap: the title of the missing step; empty otherwise") String newStepTitle,
            @JsonPropertyDescription("For a gap: who does the new step, Me or a role, never a name") String newStepOwner,
            @JsonPropertyDescription("For a gap: the new step's date as YYYY-MM-DD") String newStepTargetDate,
            @JsonPropertyDescription("One short sentence on the change") String note) {
    }

    // V11 Phase 3: the clarify conversation.

    public record ClarifyDraft(
            @JsonPropertyDescription("True when the dream is clear enough to plan; then question is empty") boolean done,
            @JsonPropertyDescription("One short, kind question about what matters most to plan well; empty when done") String question,
            @JsonPropertyDescription("WHY_IMPORTANT, SUCCESS_DEFINITION or TARGET_DATE when the answer belongs in that dream field; empty otherwise") String field) {
    }

    public record IssueDraft(
            @JsonPropertyDescription("VAGUE_STEP, UNREALISTIC_TIMING or GAP") String kind,
            @JsonPropertyDescription("GOAL or STEP") String itemType,
            @JsonPropertyDescription("The id shown in brackets for that goal or step") long itemId,
            @JsonPropertyDescription("The problem in one sentence") String problem,
            @JsonPropertyDescription("A concrete fix in one sentence") String fix) {
    }

    public record PlanReview(List<IssueDraft> issues) {
        public PlanReview {
            issues = Copies.list(issues);
        }
    
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
        public NextStepDraft {
            picks = Copies.list(picks);
        }
    
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
            @JsonPropertyDescription("The id in brackets when this is one of the approved resources; 0 otherwise") long approvedId,
            @JsonPropertyDescription("COURSE, BOOK, TOOL, ORGANISATION, FUNDING, PERSON or OTHER") String kind,
            @JsonPropertyDescription("A well-known name, or for PERSON the kind of person as a role") String name,
            @JsonPropertyDescription("How it helps this goal, in one sentence") String why) {
    }

    public record ResourceDrafts(List<ResourceDraft> resources) {
        public ResourceDrafts {
            resources = Copies.list(resources);
        }
    
    }

    // Phase 7: module help.

    public record FieldValue(
            @JsonPropertyDescription("The field key exactly as listed") String key,
            @JsonPropertyDescription("The suggested answer for that field") String value) {
    }

    public record RowDraft(List<FieldValue> values) {
        public RowDraft {
            values = Copies.list(values);
        }
    
    }

    public record ModuleHelpDraft(
            @JsonPropertyDescription("Up to three short questions to think through, most useful first; empty when not asked for") List<String> questions,
            @JsonPropertyDescription("A short example answer about a made-up person, not the participant; empty when not asked for") String example,
            @JsonPropertyDescription("Draft answers for the exercise, at most three rows; empty when not asked for") List<RowDraft> rows,
            @JsonPropertyDescription("Two or three sentences of honest, kind feedback; empty when not asked for") String feedback,
            @JsonPropertyDescription("Up to three specific improvements; empty when not asked for") List<String> improvements) {
        public ModuleHelpDraft {
            questions = Copies.list(questions);
            rows = Copies.list(rows);
            improvements = Copies.list(improvements);
        }
    
    }

    public record AssessmentExplanationDraft(
            @JsonPropertyDescription("Three to five plain sentences on what the scores say, strongest and weakest first") String explanation,
            @JsonPropertyDescription("The number of the one module to focus on next, 1 to 15") int focusModule,
            @JsonPropertyDescription("Why that module, in one sentence") String reason,
            @JsonPropertyDescription("One sentence for each barrier scored 1 or 2, by its key; empty when none") List<ChainNoteDraft> chainNotes,
            @JsonPropertyDescription("One or two sentences on the trend across every take; empty with only one take") String trend) {
        public AssessmentExplanationDraft {
            chainNotes = Copies.list(chainNotes);
        }

        /** The V9 shape, with no chain notes or trend. */
        public AssessmentExplanationDraft(String explanation, int focusModule, String reason) {
            this(explanation, focusModule, reason, List.of(), null);
        }
    }

    /** V15 (AH-FR-25): one sentence on one low barrier. */
    public record ChainNoteDraft(
            @JsonPropertyDescription("The barrier's key, such as fearScore") String key,
            @JsonPropertyDescription("One plain sentence about this score, describing the score, never the person") String note) {
    }

    /** V15 (AH-FR-26): one small practice for the next seven days. */
    public record PracticeDraft(
            @JsonPropertyDescription("The barrier's key, such as fearScore") String chainKey,
            @JsonPropertyDescription("One small practice for the next seven days, tied to the app action given for this barrier") String practice,
            @JsonPropertyDescription("A short habit name for it, under 60 characters") String habit) {
    }

    public record PracticeDrafts(@JsonPropertyDescription("One practice per barrier listed") List<PracticeDraft> practices) {
        public PracticeDrafts {
            practices = Copies.list(practices);
        }
    }

    /** V15 (AH-FR-29): what changed since the previous take. */
    public record WhatChangedDraft(@JsonPropertyDescription("Three or four short, encouraging sentences") String summary) {
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

    // Phase 9: admin content.

    public record QuizItem(
            @JsonPropertyDescription("A short question that checks understanding of the module") String question,
            @JsonPropertyDescription("The answer, in one or two sentences") String answer) {
    }

    public record ModuleContentDraft(
            @JsonPropertyDescription("Module title, under 80 characters") String title,
            @JsonPropertyDescription("One or two sentences on what the module does for the participant") String summary,
            @JsonPropertyDescription("The teaching text, in plain words, under 1500 characters") String guidance,
            @JsonPropertyDescription("What the participant does to complete it, one or two sentences") String exerciseInstructions,
            @JsonPropertyDescription("Two or three short worked examples about made-up people") String examples,
            @JsonPropertyDescription("Three to five quiz questions with answers") List<QuizItem> quiz) {
        public ModuleContentDraft {
            quiz = Copies.list(quiz);
        }
    
    }

    public record TranslationDraft(
            @JsonPropertyDescription("One translation per input text, in the same order") List<String> translations) {
        public TranslationDraft {
            translations = Copies.list(translations);
        }
    
    }
    // V11 Phase 7: the plan assistant's proposed changes (PB-FR-46).

    public record AssistantChangeDraft(
            @JsonPropertyDescription("EDIT, ADD_STEP, ADD_TASK or ADD_RISK") String kind,
            @JsonPropertyDescription("For EDIT: the ref in brackets of the item to change; empty otherwise") String itemRef,
            @JsonPropertyDescription("For EDIT: TITLE, SUCCESS, WHY, DATE or OWNER") String field,
            @JsonPropertyDescription("For EDIT: the new value; a date as YYYY-MM-DD; an owner is Me or a role, never a name") String value,
            @JsonPropertyDescription("For ADD_STEP: the goal's ref; ADD_TASK: the step's ref; ADD_RISK: the goal or step it threatens, or empty") String parentRef,
            @JsonPropertyDescription("For ADD_STEP, ADD_TASK and ADD_RISK: a short title starting with a verb (for a risk, the risk itself)") String title,
            @JsonPropertyDescription("For ADD_STEP and ADD_TASK: Me or a role, never a person's name") String owner,
            @JsonPropertyDescription("For ADD_STEP and ADD_TASK: a due date as YYYY-MM-DD") String date,
            @JsonPropertyDescription("For ADD_RISK: KNOWLEDGE, SKILL, TIME, MONEY, MOTIVATION, PARTNER, SYSTEM, DECISION or OTHER") String obstacleType,
            @JsonPropertyDescription("For ADD_RISK: LOW, MEDIUM, HIGH or CRITICAL") String severity,
            @JsonPropertyDescription("For ADD_RISK: one sentence on how to handle it") String solution,
            @JsonPropertyDescription("One short sentence on why this change helps") String reason) {
    }

    public record AssistantChangeDrafts(
            List<AssistantChangeDraft> changes,
            @JsonPropertyDescription("One or two sentences for the participant about these changes, or why none are needed") String note) {
        public AssistantChangeDrafts {
            changes = Copies.list(changes);
        }
    
    }

    /** V15 (AH-FR-06): one proposed change to a form field on the page assistant's page. */
    public record PageChangeDraft(
            @JsonPropertyDescription("The form field to change, exactly as listed under allowed fields") String field,
            @JsonPropertyDescription("The proposed value for that field, in the participant's voice where it is their text") String value,
            @JsonPropertyDescription("One short sentence on why this change helps") String reason) {
    }

    /** V15 (AH-FR-06): the page assistant's proposals for the area on screen. */
    public record PageAssistantDraft(
            @JsonPropertyDescription("One or two sentences for the participant; empty when the changes speak for themselves") String note,
            @JsonPropertyDescription("Up to five changes, most useful first; empty when nothing should change") List<PageChangeDraft> changes) {
        public PageAssistantDraft {
            changes = Copies.list(changes);
        }
    }

    /** V15 (AH-FR-31): a proposed type and severity for one obstacle. */
    public record ObstacleClassifyDraft(
            @JsonPropertyDescription("KNOWLEDGE, SKILL, TIME, MONEY, MOTIVATION, PARTNER, SYSTEM, DECISION or OTHER") String obstacleType,
            @JsonPropertyDescription("One line on why this type fits") String typeReason,
            @JsonPropertyDescription("LOW, MEDIUM, HIGH or CRITICAL") String severity,
            @JsonPropertyDescription("One line on why this severity fits") String severityReason) {
    }

    /** V15 (AH-FR-32): the next "why" question, or the root cause once it is clear. */
    public record RootCauseDraft(
            @JsonPropertyDescription("The next short 'why' question; empty when you give the root cause") String question,
            @JsonPropertyDescription("The root cause in one or two sentences in the participant's voice; empty while you still ask") String rootCause,
            @JsonPropertyDescription("One line on how the answers point to this cause; empty while you still ask") String reason) {
    }

    /** V15 (AH-FR-33): one idea with its first small step. */
    public record SolutionDraft(
            @JsonPropertyDescription("The idea in one sentence") String idea,
            @JsonPropertyDescription("The first small step, doable this week") String firstStep,
            @JsonPropertyDescription("solution for the main idea, creativeAlternatives for another option") String field) {
    }

    public record SolutionDrafts(@JsonPropertyDescription("Up to three ideas, most useful first") List<SolutionDraft> solutions) {
        public SolutionDrafts {
            solutions = Copies.list(solutions);
        }
    }

    /** V15 (AH-FR-37): one next task that moves the obstacle forward. */
    public record NextTaskDraft(
            @JsonPropertyDescription("A short task title starting with a verb") String title,
            @JsonPropertyDescription("One or two sentences on what to do") String description,
            @JsonPropertyDescription("Due date as YYYY-MM-DD, on or before the latest date given") String dueDate,
            @JsonPropertyDescription("LOW, MEDIUM, HIGH or CRITICAL") String priority,
            @JsonPropertyDescription("One line on why this task comes next") String reason) {
    }

    /** V15 (AH-FR-41): why one obstacle ranks where it does. */
    public record PriorityReason(
            @JsonPropertyDescription("The obstacle's number in the list given, 1 to 3") Integer rank,
            @JsonPropertyDescription("One sentence on why to tackle it at this point") String reason) {
    }

    public record PriorityReasons(@JsonPropertyDescription("A reason for each of the top three") List<PriorityReason> reasons) {
        public PriorityReasons {
            reasons = Copies.list(reasons);
        }
    }

    /** V15 (AH-FR-45): one problem Claude found in a message, with an optional fix to one field. */
    public record MessageIssueDraft(
            @JsonPropertyDescription("The problem in one short sentence") String problem,
            @JsonPropertyDescription("The field the fix changes, from the allowed fields; empty when there is no single fix") String field,
            @JsonPropertyDescription("The fixed value for that field; empty when there is no fix") String value) {
    }

    public record MessageCheckDraft(@JsonPropertyDescription("Up to six problems, most important first; empty when the message is good")
                                    List<MessageIssueDraft> issues) {
        public MessageCheckDraft {
            issues = Copies.list(issues);
        }
    }

    /** V15 (AH-FR-49, AH-FR-65): a new message draft. */
    public record NewMessageDraft(
            @JsonPropertyDescription("A short subject line") String subject,
            @JsonPropertyDescription("The full message body, ready to edit") String messageBody,
            @JsonPropertyDescription("The one request in this message, in a sentence; empty for a thank-you") String request) {
    }

    /** V15 (AH-FR-64): a short plan for a meeting a partner agreed to. */
    public record MeetingPlanDraft(
            @JsonPropertyDescription("The one outcome wanted from the meeting") String outcome,
            @JsonPropertyDescription("Exactly three questions to ask") List<String> questions,
            @JsonPropertyDescription("What to bring or send before") String bring,
            @JsonPropertyDescription("The closing ask") String closingAsk) {
        public MeetingPlanDraft {
            questions = Copies.list(questions);
        }
    }

    /** V15 (AH-FR-18): a module explained in plain words. */
    public record ModuleExplainDraft(
            @JsonPropertyDescription("The module's idea in two or three plain sentences") String explanation,
            @JsonPropertyDescription("One or two sentences on why it matters in the method") String whyItMatters,
            @JsonPropertyDescription("One short example about a made-up person, never the participant") String example) {
    }

    /** V15 (AH-FR-20): one exercise field that is empty or too short. */
    public record ModuleCheckItemDraft(
            @JsonPropertyDescription("The row number, counting from 1") Integer row,
            @JsonPropertyDescription("The field key, from the listed fields") String field,
            @JsonPropertyDescription("One line on how to make this answer more useful") String tip) {
    }

    public record ModuleCheckDraft(@JsonPropertyDescription("Fields that are empty or too short; empty when the answer is ready")
                                   List<ModuleCheckItemDraft> items) {
        public ModuleCheckDraft {
            items = Copies.list(items);
        }
    }

    /** V15 (AH-FR-13): the one module to do next. */
    public record ModuleNextDraft(
            @JsonPropertyDescription("The number of one module from the open list") Integer moduleNumber,
            @JsonPropertyDescription("Two sentences on why, naming the participant's own data") String reason) {
    }

    /** V15 (AH-FR-70, AH-FR-73): a short explanation. */
    public record StyleTextDraft(@JsonPropertyDescription("A few short, plain sentences") String text) {
    }

    /** V15 (AH-FR-71): one practice that uses the style's strength or covers its blind spot. */
    public record StylePracticeDraft(
            @JsonPropertyDescription("One small practice in a sentence") String text,
            @JsonPropertyDescription("UNDATED_STEPS, STEPS, TASKS, PARTNERS, TODAY or PLAN: the page where it is done") String action,
            @JsonPropertyDescription("A short habit name for it, under 60 characters") String habit) {
    }

    public record StylePracticeDrafts(@JsonPropertyDescription("Up to three practices") List<StylePracticeDraft> practices) {
        public StylePracticeDrafts {
            practices = Copies.list(practices);
        }
    }

    /** V15 (AH-FR-75): tips for working with one partner. */
    public record StyleTipsDraft(@JsonPropertyDescription("Two or three short tips") List<String> tips) {
        public StyleTipsDraft {
            tips = Copies.list(tips);
        }
    }

    /** V15 (AH-FR-51): a partner's reply, read. */
    public record ReplyDraft(
            @JsonPropertyDescription("Two sentences summarising the reply") String summary,
            @JsonPropertyDescription("YES, NO, QUESTION or LATER") String kind,
            @JsonPropertyDescription("REPLIED or CLOSED: the status the message should now have") String nextStatus,
            @JsonPropertyDescription("The one next action for the participant, in a sentence") String nextAction,
            @JsonPropertyDescription("A short, polite reply for the participant to edit") String replyBody) {
    }
}
