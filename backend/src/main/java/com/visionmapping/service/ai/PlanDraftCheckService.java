package com.visionmapping.service.ai;

import static com.visionmapping.service.ai.AiContextBuilder.clip;

import com.visionmapping.dto.request.DraftFixRequest;
import com.visionmapping.dto.request.PlanCapacityRequest;
import com.visionmapping.dto.request.PlanDraftContent;
import com.visionmapping.dto.request.PlanDraftContent.DraftGoal;
import com.visionmapping.dto.request.PlanDraftContent.DraftStep;
import com.visionmapping.dto.request.PlanDraftContent.DraftTask;
import com.visionmapping.dto.response.AiPlanResponses.DraftCheck;
import com.visionmapping.dto.response.AiPlanResponses.DraftFix;
import com.visionmapping.dto.response.AiPlanResponses.DraftIssue;
import com.visionmapping.dto.response.AiPlanResponses.PlanCapacity;
import com.visionmapping.dto.response.AiPlanResponses.WeekLoad;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.exception.AiUnavailableException;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.repository.TaskItemRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.service.support.EntityLookup;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * V11 Phase 6. The plan check on the Build my plan draft, before anything is
 * saved (PB-FR-28): rule-based checks always run, so it works without AI;
 * with AI, Claude adds vague steps, unrealistic timing and gaps. Every rule
 * issue with a clear fix carries a one-click fix the participant applies to the
 * draft, and AI issues can ask for a proposed change (PB-FR-29). Nothing here
 * saves plan data. Also the weekly hours already planned, for the capacity
 * check (PB-FR-16).
 */
@Service
@RequiredArgsConstructor
public class PlanDraftCheckService {

    static final int MAX_AI_ISSUES = 8;
    static final int CAPACITY_WEEKS = 26;
    private static final Set<String> AI_KINDS = Set.of("VAGUE_STEP", "UNREALISTIC_TIMING", "GAP");

    private final EntityLookup lookup;
    private final AiAccessService accessService;
    private final AiStructuredRequests requests;
    private final AiPlanService planService;
    private final PlanBuilderDraftService drafts;
    private final VisionStepRepository stepRepository;
    private final TaskItemRepository taskRepository;
    private final AppUserRepository userRepository;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /** PB-FR-28: rule issues always; AI issues when AI is available. */
    public DraftCheck check(Long dreamId, String language) {
        AppUser user = lookup.currentUser();
        PlanDraftContent content = drafts.stored(dreamId);
        LocalDate today = LocalDate.now(clock);
        Rules rules = transactions.execute(status -> rules(user, dreamId, content, today));
        Map<String, String> titles = rules.titles();
        Map<String, String> types = rules.types();
        List<DraftIssue> issues = new ArrayList<>(rules.issues());
        String records = rules.records();

        Optional<String> apiKey = accessService.availableKey(user);
        if (apiKey.isEmpty() || titles.isEmpty()) {
            return new DraftCheck(issues, false, null);
        }
        String prompt = "Review this draft plan like a careful coach before it is saved. Report up to " + MAX_AI_ISSUES
                + " real problems of three kinds only, about new items or gaps: VAGUE_STEP (a step too vague to act on), "
                + "UNREALISTIC_TIMING (dates or hours that do not fit the work), GAP (something a goal clearly needs that no "
                + "step covers; use the goal's key). Missing owners, dates and resources are already checked, so leave those "
                + "out. Use the keys in brackets. Return no issues when the draft is sound.";
        AiStructuredRequests.Tracked<AiDrafts.DraftReview> answer;
        try {
            answer = requests.askTracked(user, apiKey.get(), "DRAFT_CHECK", language, records, prompt, AiDrafts.DraftReview.class);
        } catch (AiUnavailableException exception) {
            return new DraftCheck(issues, false, null);
        }
        int added = 0;
        for (AiDrafts.DraftIssueDraft draft : Optional.ofNullable(answer.value().issues()).orElse(List.of())) {
            String kind = draft.kind() == null ? "" : draft.kind().trim().toUpperCase();
            String key = draft.itemKey() == null ? null : draft.itemKey().trim();
            // Only keys from this draft; anything else is dropped.
            if (added >= MAX_AI_ISSUES || !AI_KINDS.contains(kind) || key == null || !titles.containsKey(key) || blank(draft.problem())) {
                continue;
            }
            issues.add(new DraftIssue(kind, types.get(key), key, null, titles.get(key), limit(draft.problem(), 300),
                    limit(draft.fix(), 300), "FIX_WITH_AI", null, true));
            added++;
        }
        return new DraftCheck(issues, true, answer.usage().getId());
    }

    /** The draft described for Claude, its titles and types by key, and the rule issues. */
    record Rules(String records, Map<String, String> titles, Map<String, String> types, List<DraftIssue> issues) {
    }

    /**
     * V11 Phase 7 (PB-FR-49): the rule checks alone on a draft, never AI, for
     * the plan assistant's section help. The caller has checked the dream is
     * the participant's own.
     */
    @Transactional(readOnly = true)
    public List<DraftIssue> ruleIssues(Long dreamId, PlanDraftContent content) {
        return rules(lookup.currentUser(), dreamId, content, LocalDate.now(clock)).issues();
    }

    private Rules rules(AppUser user, Long dreamId, PlanDraftContent content, LocalDate today) {
        Map<String, String> titles = new HashMap<>();
        Map<String, String> types = new HashMap<>();
        List<DraftIssue> issues = new ArrayList<>();
        Dream dream = lookup.dream(dreamId);
        StringBuilder out = new StringBuilder(planService.describeDream(user, dream));
        out.append("\nThe draft being built, with keys in brackets:\n");
        for (DraftGoal goal : goals(content)) {
            LocalDate goalDate = goal.targetDate();
            int savedSteps = 0;
            if (goal.existingId() != null) {
                Goal saved = lookup.goal(goal.existingId());
                goalDate = saved.getTargetDate();
                savedSteps = stepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(saved.getId(), user.getId()).size();
            }
            String goalTitle = goal.title();
            titles.put(goal.key(), goalTitle);
            types.put(goal.key(), "GOAL");
            out.append("Goal [").append(clip(goal.key())).append("] ").append(clip(goalTitle))
                    .append(goal.existingId() != null ? " (already in the plan)" : " (new)")
                    .append(goalDate == null ? "" : ", target " + goalDate).append('\n');
            List<DraftStep> steps = Optional.ofNullable(goal.steps()).orElse(List.of());
            if (savedSteps == 0 && steps.stream().noneMatch(step -> step.existingId() == null)) {
                issues.add(issue("NO_STEPS", "GOAL", goal.key(), null, goalTitle, "This goal has no steps yet.",
                        "Add the first step.", "GO_TO_STEPS", null));
            }
            Map<String, DraftStep> byKey = steps.stream().filter(step -> step.key() != null)
                    .collect(Collectors.toMap(DraftStep::key, step -> step, (first, second) -> first));
            for (DraftStep step : steps) {
                titles.put(step.key(), step.title());
                types.put(step.key(), "STEP");
                LocalDate stepDate = step.targetDate();
                if (step.existingId() != null) {
                    VisionStep saved = lookup.step(step.existingId());
                    stepDate = saved.getTargetDate();
                } else {
                    out.append("  Step [").append(clip(step.key())).append("] ").append(clip(step.title()))
                            .append(" | owner: ").append(blank(step.owner()) ? "none" : clip(step.owner()))
                            .append(" | due: ").append(stepDate == null ? "none" : stepDate)
                            .append(step.estimatedHours() == null ? "" : " | " + step.estimatedHours() + " h")
                            .append(step.complex() ? " | complex" : "")
                            .append(step.startsAfterKey() == null ? "" : " | after [" + clip(step.startsAfterKey()) + "]").append('\n');
                    stepIssues(issues, goal, goalDate, step, byKey.get(step.startsAfterKey()), today);
                }
                for (DraftTask task : Optional.ofNullable(step.tasks()).orElse(List.of())) {
                    out.append("    Task: ").append(clip(task.title())).append(" | due: ")
                            .append(task.dueDate() == null ? "none" : task.dueDate())
                            .append(task.estimatedHours() == null ? "" : " | " + task.estimatedHours() + " h").append('\n');
                    taskIssues(issues, step, stepDate, task, today);
                }
            }
        }
        return new Rules(out.toString(), titles, types, issues);
    }

    /** PB-FR-29: Claude's proposed change for one AI issue; the participant applies it to the draft, or not. */
    public DraftFix fix(Long dreamId, DraftFixRequest request, String language) {
        AppUser user = lookup.currentUser();
        PlanDraftContent content = drafts.stored(dreamId);
        String apiKey = accessService.requireAvailable(user);
        LocalDate today = LocalDate.now(clock);
        DraftGoal goal = null;
        DraftStep step = null;
        for (DraftGoal candidate : goals(content)) {
            if (request.itemKey().equals(candidate.key())) {
                goal = candidate;
            }
            for (DraftStep item : Optional.ofNullable(candidate.steps()).orElse(List.of())) {
                if (request.itemKey().equals(item.key())) {
                    goal = candidate;
                    step = item;
                }
            }
        }
        if (goal == null) {
            throw new BusinessRuleException("That item is no longer in your draft.");
        }
        DraftGoal theGoal = goal;
        DraftStep theStep = step;
        LocalDate[] goalDate = {theGoal.targetDate()};
        String records = transactions.execute(status -> {
            if (theGoal.existingId() != null) {
                goalDate[0] = lookup.goal(theGoal.existingId()).getTargetDate();
            }
            StringBuilder out = new StringBuilder(planService.describeDream(user, lookup.dream(dreamId)));
            out.append("\nThe goal: ").append(clip(theGoal.title())).append(goalDate[0] == null ? "" : ", target " + goalDate[0]).append('\n');
            Optional.ofNullable(theGoal.steps()).orElse(List.of())
                    .forEach(item -> out.append("  Step: ").append(clip(item.title()))
                            .append(item.targetDate() == null ? "" : ", due " + item.targetDate()).append('\n'));
            if (theStep != null) {
                out.append("The step to fix: ").append(clip(theStep.title()))
                        .append(theStep.targetDate() == null ? "" : ", due " + theStep.targetDate()).append('\n');
            }
            if (!blank(request.problem())) {
                out.append("The problem found: ").append(clip(request.problem())).append('\n');
            }
            return out.toString();
        });
        String task = switch (request.kind()) {
            case "VAGUE_STEP" -> "Rewrite the step to fix as one clear, concrete action. Give only a new title.";
            case "UNREALISTIC_TIMING" -> "Suggest a realistic new date for the step to fix, after " + today
                    + (goalDate[0] == null ? "" : " and not after " + goalDate[0]) + ". Give only a date.";
            default -> "Suggest the one missing step this goal needs, with owner (Me or a role, never a name) and a date after "
                    + today + (goalDate[0] == null ? "" : " and not after " + goalDate[0]) + ".";
        } + " Add one short note on the change.";
        AiStructuredRequests.Tracked<AiDrafts.FixDraft> answer = requests.askTracked(user, apiKey, "PLAN_FIX", language, records,
                task, AiDrafts.FixDraft.class);
        AiDrafts.FixDraft draft = answer.value();
        boolean gap = "GAP".equals(request.kind());
        return new DraftFix(gap ? theGoal.key() : request.itemKey(), request.kind(),
                "VAGUE_STEP".equals(request.kind()) ? limit(draft.title(), 220) : null,
                "UNREALISTIC_TIMING".equals(request.kind()) ? AiPlanService.futureDate(draft.targetDate(), today, goalDate[0]) : null,
                gap ? limit(draft.newStepTitle(), 220) : null,
                gap ? (blank(draft.newStepOwner()) ? "Me" : limit(draft.newStepOwner(), 160)) : null,
                gap ? AiPlanService.futureDate(draft.newStepTargetDate(), today, goalDate[0]) : null,
                limit(draft.note(), 300), answer.usage().getId());
    }

    /**
     * PB-FR-16: the participant's hours a week and what the saved plan already
     * asks of each coming week. Open tasks count in the week they are due; an
     * open step counts only when it has no open tasks, so nothing is counted twice.
     */
    @Transactional(readOnly = true)
    public PlanCapacity capacity() {
        AppUser user = lookup.currentUser();
        LocalDate start = LocalDate.now(clock).with(DayOfWeek.MONDAY);
        LocalDate end = start.plusWeeks(CAPACITY_WEEKS);
        Map<LocalDate, BigDecimal> weeks = new TreeMap<>();
        List<TaskItem> tasks = taskRepository.findByUser_IdAndArchivedFalse(user.getId()).stream()
                .filter(task -> task.getStatus() != WorkStatus.COMPLETED).toList();
        Set<Long> stepsWithTasks = tasks.stream().map(task -> task.getStep().getId()).collect(Collectors.toSet());
        for (TaskItem task : tasks) {
            addLoad(weeks, task.getDueDate(), task.getEstimatedHours(), start, end);
        }
        for (VisionStep step : stepRepository.findByUser_IdAndArchivedFalse(user.getId())) {
            if (step.getStatus() != WorkStatus.COMPLETED && !stepsWithTasks.contains(step.getId())) {
                addLoad(weeks, step.getTargetDate(), step.getEstimatedHours(), start, end);
            }
        }
        List<WeekLoad> planned = weeks.entrySet().stream().map(entry -> new WeekLoad(entry.getKey(), entry.getValue())).toList();
        return new PlanCapacity(user.getPlanHoursPerWeek(), planned);
    }

    /** PB-FR-16: remember the hours a week for next time. */
    @Transactional
    public PlanCapacity saveCapacity(PlanCapacityRequest request) {
        AppUser user = userRepository.findById(lookup.userId()).orElseThrow();
        user.setPlanHoursPerWeek(request.hoursPerWeek());
        return capacity();
    }

    private static void addLoad(Map<LocalDate, BigDecimal> weeks, LocalDate date, BigDecimal hours, LocalDate start, LocalDate end) {
        if (date == null || hours == null || date.isBefore(start) || !date.isBefore(end)) {
            return;
        }
        weeks.merge(date.with(DayOfWeek.MONDAY), hours, BigDecimal::add);
    }

    private static void stepIssues(List<DraftIssue> issues, DraftGoal goal, LocalDate goalDate, DraftStep step, DraftStep waitsFor,
                                   LocalDate today) {
        String title = step.title();
        if (blank(step.owner())) {
            issues.add(issue("MISSING_OWNER", "STEP", step.key(), goal.key(), title, "This step has no owner.",
                    "Make it yours for now.", "SET_OWNER_ME", null));
        }
        if (step.targetDate() == null) {
            issues.add(issue("MISSING_DATE", "STEP", step.key(), goal.key(), title, "This step has no date.",
                    goalDate == null ? "Pick a realistic due date." : "Use the goal's date for now, or pick an earlier one.",
                    goalDate == null ? null : "SET_DATE", goalDate == null ? null : goalDate.toString()));
        } else {
            if (step.targetDate().isBefore(today)) {
                issues.add(issue("PAST_DATE", "STEP", step.key(), goal.key(), title, "This step is dated in the past.",
                        "Pick a date from today on.", null, null));
            }
            if (goalDate != null && step.targetDate().isAfter(goalDate)) {
                issues.add(issue("AFTER_GOAL_DATE", "STEP", step.key(), goal.key(), title, "This step is due after its goal.",
                        "Bring it forward to the goal's date.", "SET_DATE", goalDate.toString()));
            }
            if (waitsFor != null && waitsFor.targetDate() != null && step.targetDate().isBefore(waitsFor.targetDate())) {
                issues.add(issue("BEFORE_WAITED_STEP", "STEP", step.key(), goal.key(), title,
                        "This step is due before the step it waits for.", "Move it to that step's date.", "SET_DATE",
                        waitsFor.targetDate().toString()));
            }
        }
        if (blank(step.resources())) {
            issues.add(issue("MISSING_RESOURCE", "STEP", step.key(), goal.key(), title, "This step does not say what it needs.",
                    "Write the resources and people it needs: money, tools, skills or a helper.", null, null));
        }
        if (step.complex() && Optional.ofNullable(step.tasks()).map(List::isEmpty).orElse(true)) {
            issues.add(issue("COMPLEX_NO_TASKS", "STEP", step.key(), goal.key(), title, "This complex step has no tasks yet.",
                    "Break it into tasks.", "GO_TO_TASKS", null));
        }
    }

    private static void taskIssues(List<DraftIssue> issues, DraftStep step, LocalDate stepDate, DraftTask task, LocalDate today) {
        String title = task.title();
        if (blank(task.owner())) {
            issues.add(issue("MISSING_OWNER", "TASK", task.key(), step.key(), title, "This task has no owner.",
                    "Make it yours for now.", "SET_OWNER_ME", null));
        }
        if (task.dueDate() == null) {
            issues.add(issue("MISSING_DATE", "TASK", task.key(), step.key(), title, "This task has no date.",
                    stepDate == null ? "Pick a realistic due date." : "Use the step's date for now, or pick an earlier one.",
                    stepDate == null ? null : "SET_DATE", stepDate == null ? null : stepDate.toString()));
        } else {
            if (task.dueDate().isBefore(today)) {
                issues.add(issue("PAST_DATE", "TASK", task.key(), step.key(), title, "This task is dated in the past.",
                        "Pick a date from today on.", null, null));
            }
            if (stepDate != null && task.dueDate().isAfter(stepDate)) {
                issues.add(issue("TASK_AFTER_STEP", "TASK", task.key(), step.key(), title, "This task is due after its step.",
                        "Bring it forward to the step's date.", "SET_DATE", stepDate.toString()));
            }
        }
    }

    private static DraftIssue issue(String kind, String itemType, String itemKey, String parentKey, String title, String problem,
                                    String fix, String fixAction, String fixValue) {
        return new DraftIssue(kind, itemType, itemKey, parentKey, blank(title) ? null : title.trim(), problem, fix, fixAction,
                fixValue, false);
    }

    private static List<DraftGoal> goals(PlanDraftContent content) {
        return Optional.ofNullable(content.goals()).orElse(List.of());
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
