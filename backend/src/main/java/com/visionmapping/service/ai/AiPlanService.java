package com.visionmapping.service.ai;

import static com.visionmapping.service.ai.AiContextBuilder.clip;

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
    static final int MAX_AI_ISSUES = 8;
    private static final Set<String> AI_ISSUE_KINDS = Set.of("VAGUE_STEP", "UNREALISTIC_TIMING", "GAP");

    private final EntityLookup lookup;
    private final AiAccessService accessService;
    private final VisionAreaRepository visionAreaRepository;
    private final DreamRepository dreamRepository;
    private final GoalRepository goalRepository;
    private final VisionStepRepository visionStepRepository;
    private final AiStructuredRequests requests;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /** Vision areas the participant does not have yet, to start a plan from. */
    public VisionAreaSuggestions suggestVisionAreas(String language) {
        AppUser user = lookup.currentUser();
        String apiKey = accessService.requireAvailable(user);
        String plan = transactions.execute(status -> describeAreas(user));
        String prompt = "Suggest between 1 and " + MAX_AREAS + " vision areas (major parts of life or work) this participant "
                + "could plan in. Do not repeat or rename areas they already have. Fit them to what their current areas and "
                + "dreams show; when they have none yet, suggest common, balanced ones.";
        AiDrafts.AreaDrafts drafts = requests.ask(user, apiKey, "AREA_BUILDER", language, plan, prompt, AiDrafts.AreaDrafts.class);
        List<VisionAreaSuggestion> areas = new ArrayList<>();
        for (AiDrafts.AreaDraft draft : Optional.ofNullable(drafts.areas()).orElse(List.of())) {
            if (blank(draft.name()) || areas.size() >= MAX_AREAS) {
                continue;
            }
            areas.add(new VisionAreaSuggestion(limit(draft.name(), 160), limit(draft.description(), 2000),
                    limit(draft.visionStatement(), 3000), priority(draft.priority()), limit(draft.reason(), 300)));
        }
        return new VisionAreaSuggestions(areas);
    }

    /** Dreams for one vision area, or with stretch one bold moonshot dream. */
    public DreamSuggestions suggestDreams(Long visionAreaId, boolean stretch, String language) {
        AppUser user = lookup.currentUser();
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
        AiDrafts.DreamDrafts drafts = requests.ask(user, apiKey, stretch ? "STRETCH_DREAM" : "DREAM_BUILDER",
                language, plan, prompt, AiDrafts.DreamDrafts.class);
        List<DreamSuggestion> dreams = new ArrayList<>();
        for (AiDrafts.DreamDraft draft : Optional.ofNullable(drafts.dreams()).orElse(List.of())) {
            if (blank(draft.title()) || dreams.size() >= (stretch ? 1 : MAX_DREAMS)) {
                continue;
            }
            dreams.add(new DreamSuggestion(limit(draft.title(), 220), limit(draft.description(), 3000),
                    limit(draft.whyImportant(), 3000), limit(draft.successDefinition(), 3000), dreamType(draft.dreamType()),
                    priority(draft.priority()), futureDate(draft.targetDate(), today), stretch, limit(draft.reason(), 300)));
        }
        return new DreamSuggestions(dreams, dreams.isEmpty() ? limit(drafts.clarifyingQuestion(), 500) : null);
    }

    /** AI-FR-07, or with stretch one bold goal (AI-FR-10). */
    public GoalSuggestions suggestGoals(Long dreamId, boolean stretch, String language) {
        AppUser user = lookup.currentUser();
        String apiKey = accessService.requireAvailable(user);
        String plan = transactions.execute(status -> describeDream(user, lookup.dream(dreamId)));
        LocalDate today = LocalDate.now(clock);
        String task = stretch
                ? "Suggest exactly one stretch goal for this dream: bold enough to excite, still possible with effort, "
                + "and different from the goals the participant already has."
                : "Turn this dream into between 1 and " + MAX_GOALS + " SMART goals (specific, measurable, achievable, "
                + "relevant, time-bound) that together would make it happen. Do not repeat goals the participant already has.";
        String prompt = task + " Every target date must be after " + today + " and, when the dream has a target date, "
                + "not after it. If the dream is too vague to plan well, return no goals and ask one clarifying question.";
        AiDrafts.GoalDrafts drafts = requests.ask(user, apiKey, stretch ? "STRETCH_GOAL" : "GOAL_BUILDER",
                language, plan, prompt, AiDrafts.GoalDrafts.class);
        List<GoalSuggestion> goals = new ArrayList<>();
        for (AiDrafts.GoalDraft draft : Optional.ofNullable(drafts.goals()).orElse(List.of())) {
            if (blank(draft.title()) || goals.size() >= (stretch ? 1 : MAX_GOALS)) {
                continue;
            }
            goals.add(new GoalSuggestion(limit(draft.title(), 220), limit(draft.description(), 3000),
                    limit(draft.successCriteria(), 3000), futureDate(draft.targetDate(), today), priority(draft.priority()),
                    stretch, limit(draft.reason(), 300)));
        }
        return new GoalSuggestions(goals, goals.isEmpty() ? limit(drafts.clarifyingQuestion(), 500) : null);
    }

    /** AI-FR-08: steps for one goal, with owner, date, resources and people. */
    public StepSuggestions suggestSteps(Long goalId, String language) {
        AppUser user = lookup.currentUser();
        String apiKey = accessService.requireAvailable(user);
        LocalDate today = LocalDate.now(clock);
        String plan = transactions.execute(status -> {
            Goal goal = lookup.goal(goalId);
            return describeDream(user, goal.getDream()) + "\nThe goal to plan: [" + goal.getId() + "] " + clip(goal.getTitle())
                    + (goal.getTargetDate() == null ? "" : ", target " + goal.getTargetDate()) + "\n";
        });
        String prompt = "Break the goal to plan into between 3 and " + MAX_STEPS + " steps, in the order they should happen. "
                + "Do not repeat steps the goal already has. Owner is Me unless a role clearly fits better; never invent a "
                + "person's name. Dates must be after " + today + " and, when the goal has a target date, not after it. "
                + "If the goal is too vague to plan well, return no steps and ask one clarifying question.";
        AiDrafts.StepDrafts drafts = requests.ask(user, apiKey, "PLAN_BUILDER", language, plan, prompt,
                AiDrafts.StepDrafts.class);
        List<StepSuggestion> steps = new ArrayList<>();
        for (AiDrafts.StepDraft draft : Optional.ofNullable(drafts.steps()).orElse(List.of())) {
            if (blank(draft.title()) || steps.size() >= MAX_STEPS) {
                continue;
            }
            steps.add(new StepSuggestion(limit(draft.title(), 220), limit(draft.description(), 3000),
                    blank(draft.owner()) ? "Me" : limit(draft.owner(), 160), futureDate(draft.targetDate(), today),
                    limit(draft.resources(), 1000), limit(draft.people(), 500), limit(draft.reason(), 300)));
        }
        return new StepSuggestions(steps, steps.isEmpty() ? limit(drafts.clarifyingQuestion(), 500) : null);
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
        if (apiKey.isEmpty()) {
            return new PlanCheck(issues, false);
        }
        String prompt = "Review this plan like a careful coach. Report up to " + MAX_AI_ISSUES + " real problems of three "
                + "kinds only: VAGUE_STEP (a step too vague to act on), UNREALISTIC_TIMING (dates that do not fit the work), "
                + "GAP (something the goal clearly needs that no step covers). Missing owners, dates and resources are already "
                + "checked, so leave those out. Use the ids in brackets. Return no issues when the plan is sound.";
        AiDrafts.PlanReview review;
        try {
            review = requests.ask(user, apiKey.get(), "PLAN_CHECK", language, plan, prompt, AiDrafts.PlanReview.class);
        } catch (AiUnavailableException exception) {
            return new PlanCheck(issues, false);
        }
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
        return new PlanCheck(issues, true);
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
    private String describeDream(AppUser user, Dream dream) {
        StringBuilder out = new StringBuilder();
        out.append("Today is ").append(LocalDate.now(clock)).append(".\n");
        out.append("Dream: ").append(clip(dream.getTitle()));
        if (dream.getTargetDate() != null) {
            out.append(", target ").append(dream.getTargetDate());
        }
        out.append('\n');
        appendLine(out, "Description", dream.getDescription());
        appendLine(out, "Why it matters", dream.getWhyImportant());
        appendLine(out, "Success looks like", dream.getSuccessDefinition());
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
