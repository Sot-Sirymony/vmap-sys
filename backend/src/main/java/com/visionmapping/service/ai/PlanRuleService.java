package com.visionmapping.service.ai;

import com.visionmapping.dto.request.PlanDraftContent;
import com.visionmapping.dto.request.PlanDraftContent.DraftGoal;
import com.visionmapping.dto.request.PlanDraftContent.DraftHelper;
import com.visionmapping.dto.request.PlanDraftContent.DraftRisk;
import com.visionmapping.dto.request.PlanDraftContent.DraftStep;
import com.visionmapping.dto.request.PlanDraftContent.DraftTask;
import com.visionmapping.dto.response.AiPlanResponses.DraftIssue;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.util.Copies;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * V31 (BRD 31.0.0, D-31-3): the one place the Build my plan rules R-01 to R-08
 * live. The draft check shows the result as a pass/fail list, and Save my plan
 * runs it again on the server and refuses any MUST fail (DC-FR-56). It never
 * calls AI, so a plan can always be checked and saved (D-31-11).
 *
 * Items already in the plan (existingId) are trusted as saved before V31
 * (EC-13): they are only used for the parent dates and the hierarchy.
 */
@Service
@RequiredArgsConstructor
public class PlanRuleService {

    public static final String MUST = "MUST";
    public static final String SHOULD = "SHOULD";
    public static final String INFO = "INFO";
    public static final List<String> RULES = List.of("R-01", "R-02", "R-03", "R-04", "R-05", "R-06", "R-07", "R-08");

    static final int MAX_NEW_GOALS = 5;
    /** R-06: the gaps that need a helper, and what the warning says for each (fixed text, so it can be translated). */
    private static final Map<String, String> GAP_NO_HELPER = Map.of(
            "KNOWLEDGE", "This step is missing knowledge and has no helper yet.",
            "SKILL", "This step is missing a skill and has no helper yet.",
            "MONEY", "This step is missing money and has no helper yet.");
    private static final String NEW_STEP_FOR_RISK = PlanBuilderDraftService.NEW_STEP_FOR_RISK;

    private final EntityLookup lookup;
    private final VisionStepRepository stepRepository;

    /** One rule's counts, for the pass/fail list and the metrics (section 7). */
    public record RuleStat(int must, int should, int items, int aiItems, int aiFails) {
    }

    /** Every issue found, and the counts per rule in R-01 to R-08 order. */
    public record Report(List<DraftIssue> issues, Map<String, RuleStat> stats) {
        public Report {
            issues = Copies.list(issues);
            stats = Copies.map(stats);
        }

        public long mustFails() {
            return issues.stream().filter(issue -> MUST.equals(issue.level())).count();
        }

        /** The rules with at least one SHOULD fail. */
        public Set<String> shouldRules() {
            return issues.stream().filter(issue -> SHOULD.equals(issue.level())).map(DraftIssue::rule)
                    .collect(Collectors.toCollection(TreeSet::new));
        }

        @Nullable
        public DraftIssue firstMust() {
            return issues.stream().filter(issue -> MUST.equals(issue.level())).findFirst().orElse(null);
        }
    }

    /**
     * Checks a draft for one of the caller's dreams. The caller has already
     * loaded the dream as the participant's own; existing goals and steps are
     * loaded through the same scoped lookup, so another user's id answers 404.
     */
    public Report evaluate(AppUser user, Dream dream, PlanDraftContent content, LocalDate today) {
        Run run = new Run(today);
        checkDream(run, dream);
        List<DraftGoal> goals = Optional.ofNullable(content.goals()).orElse(List.of());
        Map<String, String> stepKeys = new HashMap<>();
        Set<String> goalKeys = new HashSet<>();
        Set<String> helpedSteps = helpedSteps(content);
        int newGoals = 0;
        for (DraftGoal goal : goals) {
            if (goal.key() != null) {
                goalKeys.add(goal.key());
            }
            LocalDate goalDate = goal.targetDate();
            int savedSteps = 0;
            if (goal.existingId() != null) {
                Goal saved = lookup.goal(goal.existingId());
                goalDate = saved.getTargetDate();
                if (saved.isArchived() || !saved.getDream().getId().equals(dream.getId())) {
                    run.add("R-01", MUST, "GOAL_NOT_IN_DREAM", "GOAL", goal.key(), null, saved.getTitle(),
                            "This goal is no longer part of this dream.", "Remove it from your draft.", null, null);
                }
                savedSteps = stepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(saved.getId(), user.getId()).size();
            } else {
                newGoals++;
                run.newItem(goal.key(), goal.requestId(), goal.edited());
                checkNewGoal(run, dream, goal);
                run.goalKeys.add(goal.key());
            }
            List<DraftStep> steps = Optional.ofNullable(goal.steps()).orElse(List.of());
            if (savedSteps == 0 && steps.stream().noneMatch(step -> step.existingId() == null)) {
                run.add("R-03", MUST, "NO_STEPS", "GOAL", goal.key(), null, goal.title(), "This goal has no steps yet.",
                        "Add the first step.", "GO_TO_STEPS", null);
            }
            Map<String, DraftStep> byKey = steps.stream().filter(step -> step.key() != null)
                    .collect(Collectors.toMap(DraftStep::key, step -> step, (first, second) -> first));
            for (DraftStep step : steps) {
                if (step.key() != null) {
                    stepKeys.put(step.key(), goal.key());
                }
                LocalDate stepDate = step.targetDate();
                if (step.existingId() != null) {
                    VisionStep saved = lookup.step(step.existingId());
                    stepDate = saved.getTargetDate();
                    boolean underGoal = goal.existingId() != null && saved.getGoal().getId().equals(goal.existingId());
                    if (saved.isArchived() || !underGoal) {
                        run.add("R-01", MUST, "STEP_NOT_IN_GOAL", "STEP", step.key(), goal.key(), saved.getTitle(),
                                "This step is no longer under its goal.", "Remove it from your draft.", null, null);
                    }
                } else {
                    run.newItem(step.key(), step.requestId(), step.edited());
                    checkNewStep(run, goal, goalDate, step, byKey.get(step.startsAfterKey()), helpedSteps);
                }
                for (DraftTask task : Optional.ofNullable(step.tasks()).orElse(List.of())) {
                    run.newItem(task.key(), task.requestId(), task.edited());
                    checkTask(run, step, stepDate, task);
                }
            }
        }
        if (newGoals > MAX_NEW_GOALS) {
            run.add("R-02", SHOULD, "TOO_MANY_GOALS", "GOAL", null, null, null,
                    "This draft has " + newGoals + " new goals. Five or fewer keeps the dream focused.",
                    "Merge or remove goals, or split the dream.", "GO_TO_GOALS", null);
        }
        checkRisks(run, content, stepKeys, goalKeys);
        checkAiUnchanged(run);
        return run.report();
    }

    private static void checkDream(Run run, Dream dream) {
        // EC-13: the dream was saved before this check existed, so these warn rather than block.
        if (blank(dream.getWhyImportant())) {
            dreamPart(run, dream, "DREAM_NO_WHY", "Your dream does not say why it matters yet.");
        }
        if (blank(dream.getSuccessDefinition())) {
            dreamPart(run, dream, "DREAM_NO_SUCCESS", "Your dream does not say what success looks like yet.");
        }
        if (dream.getTargetDate() == null) {
            dreamPart(run, dream, "DREAM_NO_DATE", "Your dream has no target date yet.");
        }
        if (dream.getPriority() == null) {
            dreamPart(run, dream, "DREAM_NO_PRIORITY", "Your dream has no priority yet.");
        }
    }

    private static void dreamPart(Run run, Dream dream, String kind, String problem) {
        run.add("R-02", SHOULD, kind, "DREAM", "dream", null, dream.getTitle(), problem,
                "Answer the questions on Clarify, or edit the dream.", "GO_TO_CLARIFY", null);
    }

    private static void checkNewGoal(Run run, Dream dream, DraftGoal goal) {
        String title = goal.title();
        if (blank(title)) {
            run.add("R-02", MUST, "MISSING_TITLE", "GOAL", goal.key(), null, null, "This goal has no title.",
                    "Say what result this goal delivers.", "GO_TO_GOALS", null);
        }
        if (blank(goal.successCriteria())) {
            run.add("R-02", MUST, "GOAL_NO_MEASURE", "GOAL", goal.key(), null, title, "This goal does not say how you'll know it is done.",
                    "Add a measure of done.", "GO_TO_GOALS", null);
        }
        if (goal.targetDate() == null) {
            LocalDate dreamDate = dream.getTargetDate();
            run.add("R-02", MUST, "GOAL_NO_DATE", "GOAL", goal.key(), null, title, "This goal has no target date.",
                    dreamDate == null ? "Pick a target date." : "Use the dream's date for now, or pick an earlier one.",
                    dreamDate == null ? "GO_TO_GOALS" : "SET_DATE", dreamDate == null ? null : dreamDate.toString());
        } else {
            if (goal.targetDate().isBefore(run.today)) {
                run.add("R-04", MUST, "PAST_DATE", "GOAL", goal.key(), null, title, "This goal is dated in the past.",
                        "Pick a date from today on.", "GO_TO_GOALS", null);
            }
            if (dream.getTargetDate() != null && goal.targetDate().isAfter(dream.getTargetDate())) {
                run.add("R-04", MUST, "GOAL_AFTER_DREAM", "GOAL", goal.key(), null, title, "This goal is due after its dream.",
                        "Bring it forward to the dream's date.", "SET_DATE", dream.getTargetDate().toString());
            }
        }
        if (goal.requestId() != null && !goal.approved()) {
            run.add("R-07", MUST, "GOAL_NOT_APPROVED", "GOAL", goal.key(), null, title,
                    "This goal was suggested by AI and you have not approved it yet.",
                    "Read it, edit it if needed, and approve it.", "APPROVE", null);
        }
    }

    private static void checkNewStep(Run run, DraftGoal goal, @Nullable LocalDate goalDate, DraftStep step,
                                     @Nullable DraftStep waitsFor, Set<String> helpedSteps) {
        String title = step.title();
        if (blank(title)) {
            run.add("R-03", MUST, "MISSING_TITLE", "STEP", step.key(), goal.key(), null, "This step has no title.",
                    "Name the stage of work.", "GO_TO_STEPS", null);
        }
        if (blank(step.owner())) {
            run.add("R-03", MUST, "MISSING_OWNER", "STEP", step.key(), goal.key(), title, "This step has no owner.",
                    "Make it yours for now.", "SET_OWNER_ME", null);
        }
        if (step.targetDate() == null) {
            run.add("R-04", MUST, "MISSING_DATE", "STEP", step.key(), goal.key(), title, "This step has no date.",
                    goalDate == null ? "Pick a realistic due date." : "Use the goal's date for now, or pick an earlier one.",
                    goalDate == null ? null : "SET_DATE", goalDate == null ? null : goalDate.toString());
        } else {
            if (step.targetDate().isBefore(run.today)) {
                run.add("R-04", MUST, "PAST_DATE", "STEP", step.key(), goal.key(), title, "This step is dated in the past.",
                        "Pick a date from today on.", null, null);
            }
            if (goalDate != null && step.targetDate().isAfter(goalDate)) {
                run.add("R-04", MUST, "AFTER_GOAL_DATE", "STEP", step.key(), goal.key(), title, "This step is due after its goal.",
                        "Bring it forward to the goal's date.", "SET_DATE", goalDate.toString());
            }
            if (waitsFor != null && waitsFor.targetDate() != null && step.targetDate().isBefore(waitsFor.targetDate())) {
                run.add("R-04", MUST, "BEFORE_WAITED_STEP", "STEP", step.key(), goal.key(), title,
                        "This step is due before the step it waits for.", "Move it to that step's date.", "SET_DATE",
                        waitsFor.targetDate().toString());
            }
        }
        if (step.complex() && Optional.ofNullable(step.tasks()).map(List::isEmpty).orElse(true)) {
            run.add("R-03", MUST, "COMPLEX_NO_TASKS", "STEP", step.key(), goal.key(), title, "This complex step has no tasks yet.",
                    "Break it into tasks.", "GO_TO_TASKS", null);
        }
        if (blank(step.resources())) {
            run.add("R-06", INFO, "MISSING_RESOURCE", "STEP", step.key(), goal.key(), title, "This step does not say what it needs.",
                    "Write the resources and people it needs: money, tools, skills or a helper.", null, null);
        } else if (blank(step.gapKind())) {
            run.add("R-06", SHOULD, "GAP_NO_KIND", "STEP", step.key(), goal.key(), title,
                    "This step says what it needs but not what kind of gap it is.",
                    "Choose the kind of gap, so the right kind of helper can be suggested.", "GO_TO_STEPS", null);
        } else if (GAP_NO_HELPER.containsKey(step.gapKind()) && !helpedSteps.contains(step.key())) {
            run.add("R-06", SHOULD, "GAP_NO_HELPER", "STEP", step.key(), goal.key(), title,
                    GAP_NO_HELPER.get(step.gapKind()),
                    "Add a helper, or a step to find one.", "GO_TO_HELPERS", null);
        }
    }

    private static void checkTask(Run run, DraftStep step, @Nullable LocalDate stepDate, DraftTask task) {
        String title = task.title();
        if (blank(title)) {
            run.add("R-03", MUST, "MISSING_TITLE", "TASK", task.key(), step.key(), null, "This task has no title.",
                    "Name the action.", "GO_TO_TASKS", null);
        }
        if (blank(task.owner())) {
            run.add("R-03", MUST, "MISSING_OWNER", "TASK", task.key(), step.key(), title, "This task has no owner.",
                    "Make it yours for now.", "SET_OWNER_ME", null);
        }
        if (task.priority() == null) {
            run.add("R-03", MUST, "TASK_NO_PRIORITY", "TASK", task.key(), step.key(), title, "This task has no priority.",
                    "Use Medium for now.", "SET_PRIORITY", "MEDIUM");
        }
        if (task.dueDate() == null) {
            run.add("R-03", MUST, "MISSING_DATE", "TASK", task.key(), step.key(), title, "This task has no date.",
                    stepDate == null ? "Pick a realistic due date." : "Use the step's date for now, or pick an earlier one.",
                    stepDate == null ? null : "SET_DATE", stepDate == null ? null : stepDate.toString());
        } else {
            if (task.dueDate().isBefore(run.today)) {
                run.add("R-04", MUST, "PAST_DATE", "TASK", task.key(), step.key(), title, "This task is dated in the past.",
                        "Pick a date from today on.", null, null);
            }
            if (stepDate != null && task.dueDate().isAfter(stepDate)) {
                run.add("R-04", MUST, "TASK_AFTER_STEP", "TASK", task.key(), step.key(), title, "This task is due after its step.",
                        "Bring it forward to the step's date.", "SET_DATE", stepDate.toString());
            }
        }
        if (task.statusOrDefault() == WorkStatus.BLOCKED && blank(task.blockerReason())) {
            run.add("R-05", MUST, "BLOCKED_NO_REASON", "TASK", task.key(), step.key(), title,
                    "This task is blocked but does not say why.", "Write what is in the way.", "GO_TO_TASKS", null);
        }
    }

    /** V17 D-17-1: a risk sits on a step of the draft, or on a new step made for it under one of its goals. */
    private static void checkRisks(Run run, PlanDraftContent content, Map<String, String> stepKeys, Set<String> goalKeys) {
        for (DraftRisk risk : Optional.ofNullable(content.risks()).orElse(List.of())) {
            run.newItem(risk.key(), risk.requestId(), risk.edited());
            String key = risk.linkKey();
            boolean onStep = key != null && stepKeys.containsKey(key);
            boolean onNewStep = key != null && key.startsWith(NEW_STEP_FOR_RISK)
                    && goalKeys.contains(key.substring(NEW_STEP_FOR_RISK.length()));
            if (!onStep && !onNewStep) {
                run.add("R-05", MUST, "RISK_NOT_ON_STEP", "RISK", risk.key(), null, risk.title(),
                        "This risk is not on a step.", "Choose the step this risk holds up.", "GO_TO_HELPERS", null);
            }
        }
    }

    /** R-07: AI-suggested steps, tasks and risks kept exactly as suggested; one line for all. Goals need approval instead. */
    private static void checkAiUnchanged(Run run) {
        List<String> keys = run.aiUnchanged.stream().filter(key -> !run.goalKeys.contains(key)).sorted().toList();
        if (!keys.isEmpty()) {
            run.add("R-07", SHOULD, "AI_UNCHANGED", null, keys.get(0), null, null,
                    keys.size() + " suggested step(s) or task(s) are kept exactly as the AI wrote them.",
                    "Read them once more: every detail should be true for you.", null, null);
        }
    }

    /** Draft step keys that have a helper, or a risk with a helper, tied to them. */
    private static Set<String> helpedSteps(PlanDraftContent content) {
        Set<String> keys = new HashSet<>();
        for (DraftHelper helper : Optional.ofNullable(content.helpers()).orElse(List.of())) {
            if (helper.linkKey() != null) {
                keys.add(helper.linkKey());
            }
        }
        for (DraftRisk risk : Optional.ofNullable(content.risks()).orElse(List.of())) {
            if (risk.linkKey() != null && risk.helperKey() != null) {
                keys.add(risk.linkKey());
            }
        }
        return keys;
    }

    private static boolean blank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    /** The state of one evaluation. */
    private static final class Run {
        private final LocalDate today;
        private final List<DraftIssue> issues = new ArrayList<>();
        private final Set<String> items = new HashSet<>();
        private final Set<String> aiUnchanged = new HashSet<>();
        private final Set<String> goalKeys = new HashSet<>();
        private int unkeyed;

        Run(LocalDate today) {
            this.today = today;
        }

        void newItem(@Nullable String key, @Nullable Long requestId, boolean edited) {
            String id = key == null ? "#" + unkeyed++ : key;
            items.add(id);
            if (requestId != null && !edited) {
                aiUnchanged.add(id);
            }
        }

        void add(String rule, String level, String kind, @Nullable String itemType, @Nullable String itemKey,
                 @Nullable String parentKey, @Nullable String title, String problem, String fix, @Nullable String fixAction,
                 @Nullable String fixValue) {
            issues.add(new DraftIssue(kind, itemType, itemKey, parentKey, blank(title) ? null : title.trim(), problem, fix,
                    fixAction, fixValue, false, rule, level));
        }

        Report report() {
            Map<String, RuleStat> stats = new LinkedHashMap<>();
            for (String rule : RULES) {
                int must = 0;
                int should = 0;
                Set<String> aiFailed = new HashSet<>();
                for (DraftIssue issue : issues) {
                    if (!rule.equals(issue.rule())) {
                        continue;
                    }
                    if (MUST.equals(issue.level())) {
                        must++;
                    } else if (SHOULD.equals(issue.level())) {
                        should++;
                    }
                    if (!INFO.equals(issue.level()) && issue.itemKey() != null && aiUnchanged.contains(issue.itemKey())) {
                        aiFailed.add(issue.itemKey());
                    }
                }
                stats.put(rule, new RuleStat(must, should, items.size(), aiUnchanged.size(), aiFailed.size()));
            }
            return new Report(issues, stats);
        }
    }
}
