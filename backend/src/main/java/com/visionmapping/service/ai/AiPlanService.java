package com.visionmapping.service.ai;

import static com.visionmapping.service.ai.AiContextBuilder.clip;
import static com.visionmapping.service.ai.AiContextBuilder.clipLong;

import com.visionmapping.dto.request.DraftGoalStepsRequest;
import com.visionmapping.dto.request.DraftStepTasksRequest;
import com.visionmapping.dto.request.PlanAskRequest;
import com.visionmapping.dto.response.AiPlanResponses.TaskSuggestion;
import com.visionmapping.dto.response.AiPlanResponses.TaskSuggestions;
import com.visionmapping.dto.request.PlanClarification;
import com.visionmapping.dto.request.PlanClarifyRequest;
import com.visionmapping.dto.response.AiPlanResponses.ClarifyQuestion;
import com.visionmapping.dto.response.AiPlanResponses.DreamSuggestion;
import com.visionmapping.dto.response.AiPlanResponses.DreamSuggestions;
import com.visionmapping.dto.response.AiPlanResponses.GoalSuggestion;
import com.visionmapping.dto.response.AiPlanResponses.GoalSuggestions;
import com.visionmapping.dto.response.AiPlanResponses.PlanCheck;
import com.visionmapping.dto.response.AiPlanResponses.PlanIssue;
import com.visionmapping.dto.response.AiPlanResponses.SlippingStep;
import com.visionmapping.dto.response.AiPlanResponses.StepRescue;
import com.visionmapping.dto.response.AiPlanResponses.StepSuggestion;
import com.visionmapping.dto.response.AiPlanResponses.StepSuggestions;
import com.visionmapping.dto.response.AiPlanResponses.VisionAreaSuggestion;
import com.visionmapping.dto.response.AiPlanResponses.VisionAreaSuggestions;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.VisionArea;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.DreamType;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.exception.AiUnavailableException;
import com.visionmapping.repository.DreamRepository;
import com.visionmapping.repository.GoalRepository;
import com.visionmapping.repository.VisionAreaRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.service.support.EntityLookup;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * V9 Phase 5: the AI goal builder (AI-FR-07), plan builder (AI-FR-08), plan
 * check (AI-FR-09), stretch goal (AI-FR-10) and help for steps that keep
 * slipping (AI-FR-11). Every answer is a suggestion the participant accepts,
 * edits or ignores; this service never saves a goal or step (AI-FR-04, AC-09).
 * The plan check's rule-based half needs no AI, so it works even when AI is
 * off (AI-NFR-02) and always flags a missing owner, date or resource (AC-10).
 */
@Service
@RequiredArgsConstructor
public class AiPlanService {

    static final int MAX_AREAS = 5;
    static final int MAX_DREAMS = 5;
    static final int MAX_GOALS = 5;
    static final int MAX_STEPS = 10;
    static final int MAX_TASKS = 8;
    static final int MAX_AI_ISSUES = 8;
    private static final Set<String> AI_ISSUE_KINDS = Set.of("VAGUE_STEP", "UNREALISTIC_TIMING", "GAP");

    /** Goals are built from what the participant wrote about the dream, not from its title alone. */
    static final String GOAL_GROUNDING = "Before suggesting, read the dream's vision area, description, why it matters and "
            + "success definition. Every goal must move the dream toward its success definition, and together the goals "
            + "must fully meet it; let why it matters guide what to put first. In each goal's reason, say in one short "
            + "sentence which part of the success definition it covers. When the dream has no success definition or "
            + "description, work from its title and why it matters.";

    /** Steps are built from the goal's success criteria, so finishing them finishes the goal. */
    static final String STEP_GROUNDING = "Before suggesting, read the goal's description and success criteria, and the "
            + "dream's success definition. Together the steps must meet the goal's success criteria. In each step's "
            + "reason, say in one short sentence how it moves the goal toward them.";

    private final EntityLookup lookup;
    private final AiAccessService accessService;
    private final VisionAreaRepository visionAreaRepository;
    private final DreamRepository dreamRepository;
    private final GoalRepository goalRepository;
    private final VisionStepRepository visionStepRepository;
    private final com.visionmapping.repository.TaskItemRepository taskItemRepository;
    private final AiStructuredRequests requests;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final AiPlanFeedbackService feedback;
    private final PlanSessionGuard sessionGuard;

    /** Vision areas the participant does not have yet, to start a plan from. */
    public VisionAreaSuggestions suggestVisionAreas(String language) {
        AppUser user = lookup.currentUser();
        String apiKey = accessService.requireAvailable(user);
        String plan = transactions.execute(status -> describeAreas(user));
        String prompt = "Suggest between 1 and " + MAX_AREAS + " vision areas (major parts of life or work) this participant "
                + "could plan in. Do not repeat or rename areas they already have. Fit them to what their current areas and "
                + "dreams show; when they have none yet, suggest common, balanced ones.";
        AiStructuredRequests.Tracked<AiDrafts.AreaDrafts> answer = requests.askTracked(user, apiKey, "AREA_BUILDER", language, plan,
                prompt, AiDrafts.AreaDrafts.class);
        AiDrafts.AreaDrafts drafts = answer.value();
        List<VisionAreaSuggestion> areas = new ArrayList<>();
        for (AiDrafts.AreaDraft draft : Optional.ofNullable(drafts.areas()).orElse(List.of())) {
            if (blank(draft.name()) || areas.size() >= MAX_AREAS) {
                continue;
            }
            areas.add(new VisionAreaSuggestion(limit(draft.name(), 160), limit(draft.description(), 2000),
                    limit(draft.visionStatement(), 3000), priority(draft.priority()), limit(draft.reason(), 300)));
        }
        return new VisionAreaSuggestions(areas, feedback.suggested(answer.usage(), areas.size()));
    }

    /** Dreams for one vision area, or with stretch one bold moonshot dream. */
    public DreamSuggestions suggestDreams(Long visionAreaId, boolean stretch, String language) {
        AppUser user = lookup.currentUser();
        // V10 (SEC-01-02): ownership first, so another user's record answers 404
        // whether or not AI is available.
        lookup.visionArea(visionAreaId);
        String apiKey = accessService.requireAvailable(user);
        String plan = transactions.execute(status -> {
            VisionArea area = lookup.visionArea(visionAreaId);
            StringBuilder out = new StringBuilder(describeAreas(user));
            out.append("\nThe area to plan: [").append(area.getId()).append("] ").append(clip(area.getName())).append('\n');
            appendLine(out, "Area description", area.getDescription());
            appendLine(out, "Area vision", area.getVisionStatement());
            return out.toString();
        });
        LocalDate today = LocalDate.now(clock);
        String task = stretch
                ? "Suggest exactly one moonshot dream for the area to plan: bold enough to excite, still possible with years "
                + "of effort, and different from the dreams the participant already has."
                : "Suggest between 1 and " + MAX_DREAMS + " meaningful dreams for the area to plan. Each needs a clear title, "
                + "why it matters and what success looks like, so it can be turned into goals. Do not repeat dreams the "
                + "participant already has.";
        String prompt = task + " Every target date must be after " + today + ". If too little is known about what the "
                + "participant wants in this area, return no dreams and ask one clarifying question.";
        AiStructuredRequests.Tracked<AiDrafts.DreamDrafts> answer = requests.askTracked(user, apiKey,
                stretch ? "STRETCH_DREAM" : "DREAM_BUILDER", language, plan, prompt, AiDrafts.DreamDrafts.class);
        AiDrafts.DreamDrafts drafts = answer.value();
        List<DreamSuggestion> dreams = new ArrayList<>();
        for (AiDrafts.DreamDraft draft : Optional.ofNullable(drafts.dreams()).orElse(List.of())) {
            if (blank(draft.title()) || dreams.size() >= (stretch ? 1 : MAX_DREAMS)) {
                continue;
            }
            dreams.add(new DreamSuggestion(limit(draft.title(), 220), limit(draft.description(), 3000),
                    limit(draft.whyImportant(), 3000), limit(draft.successDefinition(), 3000), dreamType(draft.dreamType()),
                    priority(draft.priority()), futureDate(draft.targetDate(), today), stretch, limit(draft.reason(), 300)));
        }
        return new DreamSuggestions(dreams, dreams.isEmpty() ? limit(drafts.clarifyingQuestion(), 500) : null,
                feedback.suggested(answer.usage(), dreams.size()));
    }

    /** AI-FR-07, or with stretch one bold goal (AI-FR-10). */
    public GoalSuggestions suggestGoals(Long dreamId, boolean stretch, String language) {
        return suggestGoals(dreamId, stretch, language, null);
    }

    /**
     * AI-FR-07 and AI-FR-10, with V11 Phase 3: clarifying answers (PB-FR-09),
     * suggest anyway (PB-FR-11) and refining the last set (PB-FR-19 to PB-FR-21).
     */
    public GoalSuggestions suggestGoals(Long dreamId, boolean stretch, String language, PlanAskRequest ask) {
        AppUser user = lookup.currentUser();
        // V10 (SEC-01-02): ownership first, so another user's record answers 404
        // whether or not AI is available.
        // V11 (PB-FR-15): a goal may not be due after its dream.
        LocalDate latest = lookup.dream(dreamId).getTargetDate();
        String apiKey = accessService.requireAvailable(user);
        sessionGuard.use(user.getId(), dreamId); // PB-NFR-03
        String plan = transactions.execute(status -> describeDream(user, lookup.dream(dreamId))) + askRecords(ask);
        LocalDate today = LocalDate.now(clock);
        int max = refining(ask) ? refineMax(ask, MAX_GOALS) : stretch ? 1 : MAX_GOALS;
        String task = refining(ask)
                ? refineTask(ask, "goals", max) + " Goals stay SMART (specific, measurable, achievable, relevant, time-bound)."
                : stretch
                ? "Suggest exactly one stretch goal for this dream: bold enough to excite, still possible with effort, "
                + "and different from the goals the participant already has."
                : "Turn this dream into between 1 and " + MAX_GOALS + " SMART goals (specific, measurable, achievable, "
                + "relevant, time-bound) that together would make it happen. Do not repeat goals the participant already has.";
        String prompt = task + " " + GOAL_GROUNDING + " Every target date must be after " + today
                + " and, when the dream has a target date, not after it. " + questionRule(ask, "the dream", "goals");
        AiStructuredRequests.Tracked<AiDrafts.GoalDrafts> answer = requests.askTracked(user, apiKey,
                stretch ? "STRETCH_GOAL" : "GOAL_BUILDER", language, plan, prompt, AiDrafts.GoalDrafts.class);
        AiDrafts.GoalDrafts drafts = answer.value();
        List<GoalSuggestion> goals = new ArrayList<>();
        for (AiDrafts.GoalDraft draft : Optional.ofNullable(drafts.goals()).orElse(List.of())) {
            if (blank(draft.title()) || goals.size() >= max) {
                continue;
            }
            goals.add(new GoalSuggestion(limit(draft.title(), 220), limit(draft.description(), 3000),
                    limit(draft.successCriteria(), 3000), futureDate(draft.targetDate(), today, latest), priority(draft.priority()),
                    stretch, limit(draft.reason(), 300)));
        }
        return new GoalSuggestions(goals, goals.isEmpty() && !forced(ask) ? limit(drafts.clarifyingQuestion(), 500) : null,
                feedback.suggested(answer.usage(), goals.size()));
    }

    /** AI-FR-08: steps for one goal, with owner, date, resources and people. */
    public StepSuggestions suggestSteps(Long goalId, String language) {
        return suggestSteps(goalId, language, null);
    }

    /** AI-FR-08 with V11 Phase 3 answers and refinement. */
    public StepSuggestions suggestSteps(Long goalId, String language, PlanAskRequest ask) {
        AppUser user = lookup.currentUser();
        // V10 (SEC-01-02): ownership first, so another user's record answers 404
        // whether or not AI is available.
        Long goalDream = transactions.execute(status -> lookup.goal(goalId).getDream().getId());
        String apiKey = accessService.requireAvailable(user);
        sessionGuard.use(user.getId(), goalDream); // PB-NFR-03
        LocalDate today = LocalDate.now(clock);
        // V11 (PB-FR-15): a step may not be due after its goal, or its dream when the goal has no date.
        LocalDate[] latest = new LocalDate[1];
        String plan = transactions.execute(status -> {
            Goal goal = lookup.goal(goalId);
            latest[0] = goal.getTargetDate() != null ? goal.getTargetDate() : goal.getDream().getTargetDate();
            StringBuilder out = new StringBuilder(describeDream(user, goal.getDream()));
            out.append("\nThe goal to plan: [").append(goal.getId()).append("] ").append(clip(goal.getTitle()))
                    .append(goal.getTargetDate() == null ? "" : ", target " + goal.getTargetDate()).append('\n');
            appendLongLine(out, "Goal description", goal.getDescription());
            appendLongLine(out, "Goal success criteria", goal.getSuccessCriteria());
            return out.toString();
        });
        return stepsFor(user, apiKey, plan + askRecords(ask), today, latest[0], language, ask);
    }

    /**
     * V11 Phase 2: steps for a goal that so far exists only in the participant's
     * Build my plan draft, so it has no id yet. The dream is checked as the
     * caller's own first.
     */
    public StepSuggestions suggestDraftGoalSteps(Long dreamId, DraftGoalStepsRequest goal, String language) {
        AppUser user = lookup.currentUser();
        LocalDate dreamDate = lookup.dream(dreamId).getTargetDate();
        String apiKey = accessService.requireAvailable(user);
        sessionGuard.use(user.getId(), dreamId); // PB-NFR-03
        LocalDate today = LocalDate.now(clock);
        LocalDate latest = goal.targetDate() != null ? goal.targetDate() : dreamDate;
        String plan = transactions.execute(status -> describeDream(user, lookup.dream(dreamId))
                + "\nThe goal to plan (new, not saved yet): " + clip(goal.title())
                + (goal.targetDate() == null ? "" : ", target " + goal.targetDate()) + "\n"
                + (blank(goal.successCriteria()) ? "" : "Goal success criteria: " + clipLong(goal.successCriteria()) + "\n"));
        return stepsFor(user, apiKey, plan + askRecords(goal.ask()), today, latest, language, goal.ask());
    }

    private StepSuggestions stepsFor(AppUser user, String apiKey, String plan, LocalDate today, LocalDate latest, String language,
                                     PlanAskRequest ask) {
        int max = refining(ask) ? refineMax(ask, MAX_STEPS) : MAX_STEPS;
        String task = refining(ask)
                ? refineTask(ask, "steps", max) + " Steps stay in the order they should happen."
                : "Break the goal to plan into between 3 and " + MAX_STEPS + " steps, in the order they should happen. "
                + "Do not repeat steps the goal already has.";
        String prompt = task + " " + STEP_GROUNDING + " Owner is Me unless a role clearly fits better; never invent a "
                + "person's name. Dates must be after " + today + " and, when the goal has a target date, not after it. "
                + "For each step give a priority, a realistic effort in hours, whether it is complex (needs several "
                + "separate tasks), and the earlier step it must wait for, if any; a step is not due before that one. "
                + questionRule(ask, "the goal", "steps");
        AiStructuredRequests.Tracked<AiDrafts.StepDrafts> answer = requests.askTracked(user, apiKey, "PLAN_BUILDER", language,
                plan, prompt, AiDrafts.StepDrafts.class);
        AiDrafts.StepDrafts drafts = answer.value();
        List<StepSuggestion> steps = new ArrayList<>();
        // V11 (PB-FR-12): "starts after" names a position in Claude's list; blank items are dropped,
        // so positions are renumbered, and only an earlier kept step counts.
        Map<Integer, Integer> kept = new HashMap<>();
        List<AiDrafts.StepDraft> given = Optional.ofNullable(drafts.steps()).orElse(List.of());
        for (int index = 0; index < given.size(); index++) {
            AiDrafts.StepDraft draft = given.get(index);
            if (blank(draft.title()) || steps.size() >= max) {
                continue;
            }
            Integer after = kept.get(draft.startsAfter());
            LocalDate date = futureDate(draft.targetDate(), today, latest);
            LocalDate earlierDate = after == null ? null : steps.get(after - 1).targetDate();
            if (date != null && earlierDate != null && date.isBefore(earlierDate)) {
                date = null; // PB-FR-15: not due before the step it waits for.
            }
            steps.add(new StepSuggestion(limit(draft.title(), 220), limit(draft.description(), 3000),
                    blank(draft.owner()) ? "Me" : limit(draft.owner(), 160), date,
                    limit(draft.resources(), 1000), limit(draft.people(), 500), limit(draft.reason(), 300),
                    priority(draft.priority()), draft.complex(), hours(draft.estimatedHours()), after));
            kept.put(index + 1, steps.size());
        }
        return new StepSuggestions(steps, steps.isEmpty() && !forced(ask) ? limit(drafts.clarifyingQuestion(), 500) : null,
                feedback.suggested(answer.usage(), steps.size()));
    }

    /**
     * V11 (PB-FR-14): 2 to 8 small tasks for a complex step already in the
     * plan, with owner, due date, priority and effort. Due dates stay within
     * the step's date (or its goal's, or its dream's).
     */
    public TaskSuggestions suggestTasks(Long stepId, String language, PlanAskRequest ask) {
        AppUser user = lookup.currentUser();
        Long stepDream = transactions.execute(status -> lookup.step(stepId).getGoal().getDream().getId());
        String apiKey = accessService.requireAvailable(user);
        sessionGuard.use(user.getId(), stepDream); // PB-NFR-03
        LocalDate[] latest = new LocalDate[1];
        String plan = transactions.execute(status -> {
            VisionStep step = lookup.step(stepId);
            Goal goal = step.getGoal();
            latest[0] = firstDate(step.getTargetDate(), goal.getTargetDate(), goal.getDream().getTargetDate());
            StringBuilder out = new StringBuilder(describeDream(user, goal.getDream()));
            out.append("\nThe step to break into tasks: [").append(step.getId()).append("] ").append(clip(step.getTitle()))
                    .append(step.getTargetDate() == null ? "" : ", due " + step.getTargetDate())
                    .append(" | goal: ").append(clip(goal.getTitle())).append('\n');
            appendLine(out, "Step description", step.getDescription());
            taskItemRepository.findByStep_IdAndUser_IdAndArchivedFalse(step.getId(), user.getId())
                    .forEach(task -> out.append("  Task already there: ").append(clip(task.getTitle())).append('\n'));
            return out.toString();
        });
        return tasksFor(user, apiKey, plan + askRecords(ask), latest[0], language, ask);
    }

    /** V11 (PB-FR-14): tasks for a complex step that so far exists only in the Build my plan draft. */
    public TaskSuggestions suggestDraftStepTasks(Long dreamId, DraftStepTasksRequest step, String language) {
        AppUser user = lookup.currentUser();
        LocalDate dreamDate = lookup.dream(dreamId).getTargetDate();
        String apiKey = accessService.requireAvailable(user);
        sessionGuard.use(user.getId(), dreamId); // PB-NFR-03
        LocalDate latest = firstDate(step.stepTargetDate(), step.goalTargetDate(), dreamDate);
        String plan = transactions.execute(status -> describeDream(user, lookup.dream(dreamId)))
                + "\nThe step to break into tasks (new, not saved yet): " + clip(step.stepTitle())
                + (step.stepTargetDate() == null ? "" : ", due " + step.stepTargetDate())
                + (blank(step.goalTitle()) ? "" : " | goal: " + clip(step.goalTitle())) + "\n"
                + (blank(step.stepDescription()) ? "" : "Step description: " + clip(step.stepDescription()) + "\n");
        return tasksFor(user, apiKey, plan + askRecords(step.ask()), latest, language, step.ask());
    }

    private TaskSuggestions tasksFor(AppUser user, String apiKey, String plan, LocalDate latest, String language, PlanAskRequest ask) {
        LocalDate today = LocalDate.now(clock);
        int max = refining(ask) ? refineMax(ask, MAX_TASKS) : MAX_TASKS;
        String task = refining(ask)
                ? refineTask(ask, "tasks", max)
                : "Break the step to break into tasks into between 2 and " + MAX_TASKS + " small tasks, in order, each "
                + "doable in one sitting. Do not repeat tasks already there.";
        String prompt = task + " Owner is Me unless a role clearly fits better; never invent a person's name. Due dates must "
                + "be after " + today + " and, when the step has a date, not after it. Give each a priority and a realistic "
                + "effort in hours. " + questionRule(ask, "the step", "tasks");
        AiStructuredRequests.Tracked<AiDrafts.TaskDrafts> answer = requests.askTracked(user, apiKey, "TASK_BUILDER", language,
                plan, prompt, AiDrafts.TaskDrafts.class);
        AiDrafts.TaskDrafts drafts = answer.value();
        List<TaskSuggestion> tasks = new ArrayList<>();
        for (AiDrafts.TaskDraft draft : Optional.ofNullable(drafts.tasks()).orElse(List.of())) {
            if (blank(draft.title()) || tasks.size() >= max) {
                continue;
            }
            tasks.add(new TaskSuggestion(limit(draft.title(), 220), blank(draft.owner()) ? "Me" : limit(draft.owner(), 160),
                    futureDate(draft.dueDate(), today, latest), priority(draft.priority()), hours(draft.estimatedHours()),
                    limit(draft.reason(), 300)));
        }
        return new TaskSuggestions(tasks, tasks.isEmpty() && !forced(ask) ? limit(drafts.clarifyingQuestion(), 500) : null,
                feedback.suggested(answer.usage(), tasks.size()));
    }

    /** Effort Claude suggested, kept when it is a sensible number of hours (more than 0, at most 999.99). */
    static java.math.BigDecimal hours(Double value) {
        if (value == null || value.isNaN() || value <= 0 || value > 999.99) {
            return null;
        }
        return java.math.BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static LocalDate firstDate(LocalDate... dates) {
        for (LocalDate date : dates) {
            if (date != null) {
                return date;
            }
        }
        return null;
    }

    /**
     * V11 (PB-FR-08): the next clarifying question about the dream, one at a
     * time and at most three; after the third answer the dream counts as
     * clear enough without asking Claude again.
     */
    public ClarifyQuestion clarifyDream(Long dreamId, PlanClarifyRequest request, String language) {
        AppUser user = lookup.currentUser();
        lookup.dream(dreamId);
        List<PlanClarification> answers = Optional.ofNullable(request.answers()).orElse(List.of());
        if (answers.size() >= PlanAskRequest.MAX_QUESTIONS) {
            return new ClarifyQuestion(null, null, true, null);
        }
        String apiKey = accessService.requireAvailable(user);
        sessionGuard.use(user.getId(), dreamId); // PB-NFR-03
        String plan = transactions.execute(status -> describeDream(user, lookup.dream(dreamId)))
                + askRecords(new PlanAskRequest(answers, false, null, null, null, null));
        String prompt = "Decide whether this dream is clear enough to turn into goals: what exactly the participant wants, "
                + "why it matters, what success looks like and by when. If it is, set done. If not, ask the one question "
                + "that would help most, short and kind, and not one already answered. " + (PlanAskRequest.MAX_QUESTIONS
                - answers.size()) + " question(s) left at most.";
        AiStructuredRequests.Tracked<AiDrafts.ClarifyDraft> answer = requests.askTracked(user, apiKey, "CLARIFY", language, plan,
                prompt, AiDrafts.ClarifyDraft.class);
        AiDrafts.ClarifyDraft draft = answer.value();
        boolean done = draft.done() || blank(draft.question());
        String field = draft.field() == null ? "" : draft.field().trim().toUpperCase();
        return new ClarifyQuestion(done ? null : limit(draft.question(), 500),
                done || !CLARIFY_FIELDS.contains(field) ? null : field, done, answer.usage().getId());
    }

    // ---------- V11 Phase 3: answers and refinement ----------

    private static final Set<String> CLARIFY_FIELDS = Set.of("WHY_IMPORTANT", "SUCCESS_DEFINITION", "TARGET_DATE");

    private static boolean refining(PlanAskRequest ask) {
        return ask != null && !blank(ask.refine());
    }

    /** PB-FR-11: no more questions after "suggest anyway" or the third answer. */
    static boolean forced(PlanAskRequest ask) {
        return ask != null && (ask.suggestAnyway() || refining(ask)
                || Optional.ofNullable(ask.answers()).map(List::size).orElse(0) >= PlanAskRequest.MAX_QUESTIONS);
    }

    private static String questionRule(PlanAskRequest ask, String subject, String items) {
        return forced(ask)
                ? "Do not ask a question: make sensible assumptions and suggest " + items + "."
                : "If " + subject + " is too vague to plan well, return no " + items + " and ask one clarifying question, "
                + "not one already answered.";
    }

    static int refineMax(PlanAskRequest ask, int max) {
        int replacing = Optional.ofNullable(ask.replace()).map(List::size).orElse(0);
        return switch (ask.refine()) {
            case "ANOTHER" -> 1;
            case "FEWER" -> Math.max(1, Math.min(max, replacing - 1));
            case "MORE" -> max;
            default -> Math.max(1, Math.min(max, replacing == 0 ? max : replacing));
        };
    }

    /** The instruction only: the participant's own words (note, kept and replaced items) travel as data in askRecords. */
    private static String refineTask(PlanAskRequest ask, String items, int max) {
        String change = switch (ask.refine()) {
            case "FEWER" -> "fewer " + items + ", keeping only the most important";
            case "MORE" -> "more " + items + ", covering what is still missing";
            case "SMALLER" -> items + " that are each smaller and quicker to finish";
            case "BOLDER" -> "bolder, more ambitious " + items + " that are still possible";
            case "EARLIER" -> items + " with earlier dates where that is realistic";
            case "LATER" -> items + " with more time, moving dates later within the limits";
            default -> "exactly one different " + items.substring(0, items.length() - 1) + " to use instead of the one to replace";
        };
        return "The participant wants to change your last suggestions. Suggest at most " + max + " " + change + ". Do not repeat "
                + "or change the items they kept, and do not repeat the items to replace. Follow their note when there is one.";
    }

    /** PB-FR-09, PB-FR-20: the participant's answers, note and kept items, appended to the records sent as data. */
    static String askRecords(PlanAskRequest ask) {
        if (ask == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        List<PlanClarification> answers = Optional.ofNullable(ask.answers()).orElse(List.of());
        if (!answers.isEmpty()) {
            out.append("\nThe participant's answers to clarifying questions:\n");
            answers.forEach(item -> out.append("Q: ").append(clip(item.question())).append("\nA: ").append(clip(item.answer())).append('\n'));
        }
        if (refining(ask)) {
            List<String> keep = Optional.ofNullable(ask.keep()).orElse(List.of());
            List<String> replace = Optional.ofNullable(ask.replace()).orElse(List.of());
            if (!keep.isEmpty()) {
                out.append("\nItems the participant kept (leave as they are):\n");
                keep.forEach(item -> out.append("- ").append(clip(item)).append('\n'));
            }
            if (!replace.isEmpty()) {
                out.append("\nEarlier suggestions to replace:\n");
                replace.forEach(item -> out.append("- ").append(clip(item)).append('\n'));
            }
            if (!blank(ask.note())) {
                out.append("\nThe participant's note on what to change: ").append(clip(ask.note())).append('\n');
            }
        }
        return out.toString();
    }

    /**
     * AI-FR-09: rule-based checks always run; when AI is available, Claude adds
     * vague steps, unrealistic timing and gaps, each with a fix.
     */
    public PlanCheck checkPlan(Long dreamId, String language) {
        AppUser user = lookup.currentUser();
        LocalDate today = LocalDate.now(clock);
        Map<Long, String> titles = new HashMap<>();
        List<PlanIssue> issues = new ArrayList<>();
        String plan = transactions.execute(status -> {
            Dream dream = lookup.dream(dreamId);
            for (Goal goal : goalRepository.findByDream_IdAndUser_IdAndArchivedFalse(dream.getId(), user.getId())) {
                titles.put(goal.getId(), goal.getTitle());
                List<VisionStep> steps = visionStepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(goal.getId(), user.getId());
                if (goal.getStatus() != WorkStatus.COMPLETED && steps.isEmpty()) {
                    issues.add(rule("NO_STEPS", "GOAL", goal.getId(), goal.getTitle(), "This goal has no steps yet.",
                            "Add the first step, or ask the Dream Coach to suggest steps."));
                }
                for (VisionStep step : steps) {
                    titles.put(step.getId(), step.getTitle());
                    if (step.getStatus() != WorkStatus.COMPLETED) {
                        issues.addAll(ruleIssues(step, goal, today));
                    }
                }
            }
            return describeDream(user, dream);
        });
        Optional<String> apiKey = accessService.availableKey(user);
        // PB-NFR-03: past the session's limit the rule checks still run.
        if (apiKey.isEmpty() || !sessionGuard.tryUse(user.getId(), dreamId)) {
            return new PlanCheck(issues, false, null);
        }
        String prompt = "Review this plan like a careful coach. Report up to " + MAX_AI_ISSUES + " real problems of three "
                + "kinds only: VAGUE_STEP (a step too vague to act on), UNREALISTIC_TIMING (dates that do not fit the work), "
                + "GAP (something the goal clearly needs that no step covers). Missing owners, dates and resources are already "
                + "checked, so leave those out. Use the ids in brackets. Return no issues when the plan is sound.";
        AiStructuredRequests.Tracked<AiDrafts.PlanReview> answer;
        try {
            answer = requests.askTracked(user, apiKey.get(), "PLAN_CHECK", language, plan, prompt, AiDrafts.PlanReview.class);
        } catch (AiUnavailableException exception) {
            return new PlanCheck(issues, false, null);
        }
        AiDrafts.PlanReview review = answer.value();
        int added = 0;
        for (AiDrafts.IssueDraft draft : Optional.ofNullable(review.issues()).orElse(List.of())) {
            String kind = draft.kind() == null ? "" : draft.kind().trim().toUpperCase();
            // Only ids from this participant's own plan; anything else is dropped.
            if (added >= MAX_AI_ISSUES || !AI_ISSUE_KINDS.contains(kind) || !titles.containsKey(draft.itemId()) || blank(draft.problem())) {
                continue;
            }
            String itemType = "GOAL".equalsIgnoreCase(draft.itemType()) ? "GOAL" : "STEP";
            issues.add(new PlanIssue(kind, itemType, draft.itemId(), titles.get(draft.itemId()), limit(draft.problem(), 300),
                    limit(draft.fix(), 300), true));
            added++;
        }
        return new PlanCheck(issues, true, answer.usage().getId());
    }

    /**
     * V11 Phase 7 (PB-FR-49): the rule half of the plan check alone, never AI,
     * for the plan assistant's section help on the Plan page. Runs inside the
     * caller's transaction, which has checked the dream is the participant's own.
     */
    List<PlanIssue> ruleCheck(AppUser user, Dream dream) {
        LocalDate today = LocalDate.now(clock);
        List<PlanIssue> issues = new ArrayList<>();
        for (Goal goal : goalRepository.findByDream_IdAndUser_IdAndArchivedFalse(dream.getId(), user.getId())) {
            List<VisionStep> steps = visionStepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(goal.getId(), user.getId());
            if (goal.getStatus() != WorkStatus.COMPLETED && steps.isEmpty()) {
                issues.add(rule("NO_STEPS", "GOAL", goal.getId(), goal.getTitle(), "This goal has no steps yet.",
                        "Add the first step, or ask the Dream Coach to suggest steps."));
            }
            for (VisionStep step : steps) {
                if (step.getStatus() != WorkStatus.COMPLETED) {
                    issues.addAll(ruleIssues(step, goal, today));
                }
            }
        }
        return issues;
    }

    /** AI-FR-11: unfinished steps that are overdue again after being given a new date. No AI needed to list them. */
    public List<SlippingStep> slippingSteps() {
        Long userId = lookup.userId();
        LocalDate today = LocalDate.now(clock);
        return transactions.execute(status -> visionStepRepository.findByUser_IdAndArchivedFalse(userId).stream()
                .filter(step -> step.getStatus() != WorkStatus.COMPLETED && step.getOverdueReschedules() >= 1
                        && step.getTargetDate() != null && step.getTargetDate().isBefore(today))
                .sorted(Comparator.comparing(VisionStep::getTargetDate))
                .map(step -> new SlippingStep(step.getId(), step.getTitle(), step.getGoal().getId(), step.getGoal().getTitle(),
                        step.getTargetDate(), step.getOverdueReschedules()))
                .toList());
    }

    /** AI-FR-11: a smaller step, a new date or a helper for one slipping step. */
    public StepRescue rescueStep(Long stepId, String language) {
        AppUser user = lookup.currentUser();
        // V10 (SEC-01-02): ownership first, so another user's record answers 404
        // whether or not AI is available.
        lookup.step(stepId);
        String apiKey = accessService.requireAvailable(user);
        LocalDate today = LocalDate.now(clock);
        String plan = transactions.execute(status -> {
            VisionStep step = lookup.step(stepId);
            Goal goal = step.getGoal();
            return describeDream(user, goal.getDream()) + "\nThe step that keeps slipping: [" + step.getId() + "] "
                    + clip(step.getTitle()) + " | owner: " + clip(step.getOwner()) + " | due " + step.getTargetDate()
                    + " | moved " + step.getOverdueReschedules() + " time(s) after it was overdue | goal: " + clip(goal.getTitle()) + "\n";
        });
        String prompt = "This step has been overdue more than once. Without blame, suggest three options: a smaller first step "
                + "that could be done within a few days, a realistic new date after " + today + ", and the kind of person who "
                + "could help. Add one short sentence on why it may keep slipping.";
        AiDrafts.RescueDraft draft = requests.ask(user, apiKey, "STEP_RESCUE", language, plan, prompt, AiDrafts.RescueDraft.class);
        return new StepRescue(stepId, limit(draft.smallerStep(), 220), futureDate(draft.newDate(), today),
                limit(draft.helper(), 300), limit(draft.reason(), 300));
    }

    /** The participant's vision areas and the dream titles under each. Never partner names or contacts (AI-NFR-04). */
    private String describeAreas(AppUser user) {
        StringBuilder out = new StringBuilder();
        out.append("Today is ").append(LocalDate.now(clock)).append(".\n");
        List<VisionArea> areas = visionAreaRepository.findByUser_IdAndArchivedFalse(user.getId());
        if (areas.isEmpty()) {
            out.append("No vision areas yet.\n");
        }
        for (VisionArea area : areas) {
            out.append("Vision area [").append(area.getId()).append("] ").append(clip(area.getName()))
                    .append(" (").append(area.getPriority()).append(")\n");
            dreamRepository.findByVisionArea_IdAndUser_IdAndArchivedFalse(area.getId(), user.getId())
                    .forEach(dream -> out.append("  Dream: ").append(clip(dream.getTitle())).append(" | ")
                            .append(dream.getStatus()).append('\n'));
        }
        return out.toString();
    }

    /** The dream and its plan, with ids so Claude can point at items. Never partner names or contacts (AI-NFR-04). */
    String describeDream(AppUser user, Dream dream) {
        StringBuilder out = new StringBuilder();
        out.append("Today is ").append(LocalDate.now(clock)).append(".\n");
        out.append("Dream: ").append(clip(dream.getTitle()));
        if (dream.getTargetDate() != null) {
            out.append(", target ").append(dream.getTargetDate());
        }
        out.append('\n');
        VisionArea area = dream.getVisionArea();
        if (area != null) {
            out.append("Vision area: ").append(clip(area.getName())).append('\n');
            appendLine(out, "Area description", area.getDescription());
            appendLine(out, "Area vision", area.getVisionStatement());
        }
        appendLongLine(out, "Description", dream.getDescription());
        appendLongLine(out, "Why it matters", dream.getWhyImportant());
        appendLongLine(out, "Success looks like", dream.getSuccessDefinition());
        List<Goal> goals = goalRepository.findByDream_IdAndUser_IdAndArchivedFalse(dream.getId(), user.getId());
        if (goals.isEmpty()) {
            out.append("No goals yet.\n");
        }
        for (Goal goal : goals) {
            out.append("Goal [").append(goal.getId()).append("] ").append(clip(goal.getTitle()))
                    .append(" (").append(goal.getStatus());
            if (goal.getTargetDate() != null) {
                out.append(", target ").append(goal.getTargetDate());
            }
            out.append(")\n");
            visionStepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(goal.getId(), user.getId()).stream()
                    .sorted(Comparator.comparing(VisionStep::getSequenceNumber, Comparator.nullsLast(Comparator.naturalOrder())))
                    .forEach(step -> out.append("  Step [").append(step.getId()).append("] ").append(clip(step.getTitle()))
                            .append(" | owner: ").append(blank(step.getOwner()) ? "none" : clip(step.getOwner()))
                            .append(" | due: ").append(step.getTargetDate() == null ? "none" : step.getTargetDate())
                            .append(" | resources: ").append(blank(step.getResources()) ? "none" : clip(step.getResources()))
                            .append(" | ").append(step.getStatus()).append('\n'));
        }
        return out.toString();
    }

    private static List<PlanIssue> ruleIssues(VisionStep step, Goal goal, LocalDate today) {
        List<PlanIssue> issues = new ArrayList<>();
        if (blank(step.getOwner())) {
            issues.add(rule("MISSING_OWNER", "STEP", step.getId(), step.getTitle(), "This step has no owner.",
                    "Name who will do it, even if it is you."));
        }
        if (step.getTargetDate() == null) {
            issues.add(rule("MISSING_DATE", "STEP", step.getId(), step.getTitle(), "This step has no date.",
                    "Pick a realistic due date."));
        } else {
            if (goal.getTargetDate() != null && step.getTargetDate().isAfter(goal.getTargetDate())) {
                issues.add(rule("AFTER_GOAL_DATE", "STEP", step.getId(), step.getTitle(),
                        "This step is due after its goal's target date.", "Bring the step forward, or move the goal's date."));
            }
            if (step.getTargetDate().isBefore(today)) {
                issues.add(rule("OVERDUE", "STEP", step.getId(), step.getTitle(), "This step is overdue.",
                        "Finish it, or give it a new realistic date."));
            }
        }
        if (blank(step.getResources())) {
            issues.add(rule("MISSING_RESOURCE", "STEP", step.getId(), step.getTitle(), "This step does not say what it needs.",
                    "Write the resources and people it needs: money, tools, skills or a helper."));
        }
        return issues;
    }

    private static PlanIssue rule(String kind, String itemType, Long itemId, String itemTitle, String problem, String fix) {
        return new PlanIssue(kind, itemType, itemId, itemTitle, problem, fix, false);
    }

    private static void appendLine(StringBuilder out, String label, String value) {
        if (!blank(value)) {
            out.append(label).append(": ").append(clip(value)).append('\n');
        }
    }

    /** A field a suggestion is built from, so Claude sees it in full (see AiContextBuilder.clipLong). */
    private static void appendLongLine(StringBuilder out, String label, String value) {
        if (!blank(value)) {
            out.append(label).append(": ").append(clipLong(value)).append('\n');
        }
    }

    /**
     * V11 (PB-FR-15): a date Claude suggested, kept only when it is real, in the
     * future and not after the parent's date (latest, when there is one).
     * Otherwise the participant picks one. The prompt asks for the same; this
     * makes sure of it.
     */
    static LocalDate futureDate(String value, LocalDate today, LocalDate latest) {
        LocalDate date = futureDate(value, today);
        return date != null && latest != null && date.isAfter(latest) ? null : date;
    }

    /** A date Claude suggested, kept only when it is real and in the future; otherwise the participant picks one. */
    static LocalDate futureDate(String value, LocalDate today) {
        if (blank(value)) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(value.trim());
            return date.isAfter(today) ? date : null;
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    static Priority priority(String value) {
        try {
            return value == null ? Priority.MEDIUM : Priority.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return Priority.MEDIUM;
        }
    }

    static DreamType dreamType(String value) {
        try {
            return value == null ? DreamType.LONG_TERM : DreamType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return DreamType.LONG_TERM;
        }
    }

    private static String limit(String value, int max) {
        if (blank(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max - 3) + "...";
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
