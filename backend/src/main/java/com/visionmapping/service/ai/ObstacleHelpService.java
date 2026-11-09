package com.visionmapping.service.ai;

import static com.visionmapping.service.ai.AiContextBuilder.clip;

import com.visionmapping.dto.request.ObstacleHelpRequest;
import com.visionmapping.dto.response.ObstacleHelpResponses.ObstacleHelpAnswer;
import com.visionmapping.dto.response.ObstacleHelpResponses.RankedObstacle;
import com.visionmapping.dto.response.ObstacleHelpResponses.TaskProposal;
import com.visionmapping.dto.response.PageAssistantResponses.PageChange;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Obstacle;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.service.ObstacleSupportService;
import com.visionmapping.service.support.EntityLookup;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * V15 Phase 2: the AI obstacle helpers on the Obstacles page (AH-FR-31 to
 * AH-FR-33, AH-FR-37, AH-FR-41). Each reads the obstacle exactly as the page
 * assistant does (section B: never partner names or contacts, never the private
 * reaction note) and checks what Claude returns before it is shown. Nothing is
 * saved: cards fill the form, the next task opens the normal task form, and no
 * helper ever marks an obstacle Resolved (AH-FR-42). Logged as OBSTACLE_HELP.
 */
@Service
@RequiredArgsConstructor
public class ObstacleHelpService {

    static final String FEATURE = "OBSTACLE_HELP";
    static final int MAX_WHY = 3;
    static final int MAX_SOLUTIONS = 3;
    static final int TOP = 3;

    private final EntityLookup lookup;
    private final AiAccessService accessService;
    private final AiStructuredRequests requests;
    private final AiPlanFeedbackService feedback;
    private final PageAssistantService pageAssistant;
    private final ObstacleSupportService support;
    private final org.springframework.transaction.support.TransactionTemplate transactions;
    private final AiSafetyAlertService alerts;
    private final com.visionmapping.repository.AiUsageLogRepository usageLogRepository;
    private final com.visionmapping.service.AiSettingsService settingsService;
    private final Clock clock;

    public ObstacleHelpAnswer help(ObstacleHelpRequest request, String language) {
        AppUser user = lookup.currentUser();
        if ("PRIORITISE".equals(request.mode())) {
            return prioritise(user, language);
        }
        // Ownership first (AH-NFR-07): another user's obstacle or linked record answers 404 before AI is checked.
        PageAssistantService.View view = pageAssistant.viewInTransaction(user, request.pageRequest());
        if (request.obstacleId() == null && (request.form() == null || request.form().isEmpty())) {
            throw new com.visionmapping.exception.BusinessRuleException("Open an obstacle to use this helper.");
        }
        TaskStep taskStep = "NEXT_TASK".equals(request.mode()) ? transactions.execute(status -> taskStep(request)) : null;
        // AH-FR-40, AI-NFR-09: crisis wording in what the participant wrote gets the safety response, never Claude.
        if ("CONFLICT".equals(request.mode()) && crisisIn(request)) {
            return crisis(user, language);
        }
        String apiKey = accessService.requireAvailable(user, "OBSTACLES");
        String answerIn = " Answer in " + ("km".equals(language) ? "Khmer" : "English") + ".";
        if ("CONFLICT".equals(request.mode())) {
            return conflict(user, apiKey, language, view, request, answerIn);
        }
        return switch (request.mode()) {
            case "CLASSIFY" -> classify(user, apiKey, language, view, answerIn);
            case "ROOT_CAUSE" -> rootCause(user, apiKey, language, view, request.answerList(), answerIn);
            case "SOLUTIONS" -> solutions(user, apiKey, language, view, answerIn);
            default -> nextTask(user, apiKey, language, view, taskStep, answerIn);
        };
    }

    // ---------- AH-FR-31 ----------

    private ObstacleHelpAnswer classify(AppUser user, String apiKey, String language, PageAssistantService.View view, String answerIn) {
        String task = view.guard() + "\nFrom the obstacle's title and description, say which type it is (knowledge, skill, time, money, "
                + "motivation, partner, system, decision or other) and how severe it is for the participant's plan (low, medium, high "
                + "or critical), each with a one-line reason." + answerIn;
        AiStructuredRequests.Tracked<AiDrafts.ObstacleClassifyDraft> answer = requests.askTracked(user, apiKey, FEATURE, language,
                view.records(), task, AiDrafts.ObstacleClassifyDraft.class);
        AiDrafts.ObstacleClassifyDraft draft = answer.value();
        List<PageChange> changes = PageAssistantService.checked(List.of(
                new AiDrafts.PageChangeDraft("obstacleType", draft.obstacleType(), draft.typeReason()),
                new AiDrafts.PageChangeDraft("severity", draft.severity(), draft.severityReason())), view.writable());
        return cards(answer, "CLASSIFY", changes, view);
    }

    // ---------- AH-FR-32 ----------

    private ObstacleHelpAnswer rootCause(AppUser user, String apiKey, String language, PageAssistantService.View view,
                                         List<ObstacleHelpRequest.WhyAnswer> answers, String answerIn) {
        StringBuilder records = new StringBuilder(view.records());
        if (!answers.isEmpty()) {
            records.append("\nThe participant's answers so far:\n");
            answers.forEach(item -> records.append("- Q: ").append(clip(item.question())).append("\n  A: ")
                    .append(AiContextBuilder.clipLong(item.answer())).append('\n'));
        }
        boolean mustConclude = answers.size() >= MAX_WHY;
        String task = view.guard() + "\nHelp the participant find the root cause of this obstacle by asking 'why' questions, one "
                + "at a time. " + (mustConclude
                        ? "They have answered three questions: now give the root cause, and leave the question empty."
                        : "Ask the next short 'why' question that builds on their answers, or, if the cause is already clear, give "
                        + "the root cause instead and leave the question empty.")
                + " Base the root cause only on what they wrote; never invent facts." + answerIn;
        AiStructuredRequests.Tracked<AiDrafts.RootCauseDraft> answer = requests.askTracked(user, apiKey, FEATURE, language,
                records.toString(), task, AiDrafts.RootCauseDraft.class);
        AiDrafts.RootCauseDraft draft = answer.value();
        boolean hasCause = draft.rootCause() != null && !draft.rootCause().isBlank();
        if (!mustConclude && !hasCause && draft.question() != null && !draft.question().isBlank()) {
            Long requestId = feedback.suggested(answer.usage(), 0);
            return new ObstacleHelpAnswer(requestId, "ROOT_CAUSE", "", List.of(), clipTo(draft.question().trim(), 300), null, List.of(),
                    view.sources());
        }
        List<PageChange> changes = PageAssistantService.checked(
                List.of(new AiDrafts.PageChangeDraft("rootCause", draft.rootCause(), draft.reason())), view.writable());
        return cards(answer, "ROOT_CAUSE", changes, view);
    }

    // ---------- AH-FR-33 ----------

    private ObstacleHelpAnswer solutions(AppUser user, String apiKey, String language, PageAssistantService.View view, String answerIn) {
        String task = view.guard() + "\nSuggest up to three things the participant could try, each with a first small step they could "
                + "take this week. Fit the obstacle's type: a money obstacle gets funding and cost ideas, a time obstacle gets ideas to "
                + "free or delegate time, a knowledge obstacle gets ways to learn or ask, and so on. If they solved this kind of "
                + "obstacle before, build on how. Use solution for the main idea and creativeAlternatives for other options." + answerIn;
        AiStructuredRequests.Tracked<AiDrafts.SolutionDrafts> answer = requests.askTracked(user, apiKey, FEATURE, language,
                view.records(), task, AiDrafts.SolutionDrafts.class);
        List<AiDrafts.PageChangeDraft> drafts = new ArrayList<>();
        for (AiDrafts.SolutionDraft solution : Optional.ofNullable(answer.value().solutions()).orElse(List.of())) {
            if (solution == null || solution.idea() == null || solution.idea().isBlank() || drafts.size() >= MAX_SOLUTIONS) {
                continue;
            }
            String field = "creativeAlternatives".equals(solution.field()) ? "creativeAlternatives" : "solution";
            String step = solution.firstStep() == null || solution.firstStep().isBlank() ? "" : " First step: " + solution.firstStep().trim();
            drafts.add(new AiDrafts.PageChangeDraft(field, solution.idea().trim() + step, ""));
        }
        return cards(answer, "SOLUTIONS", PageAssistantService.checked(drafts, view.writable()), view);
    }

    // ---------- AH-FR-40 ----------

    /** The conflict worksheet and criticism fields, and the private note only when the participant ticked it. */
    private static List<String> conflictTexts(ObstacleHelpRequest request) {
        List<String> keys = new ArrayList<>(List.of("title", "description", "conflictIncident", "conflictCost", "conflictOtherPerspective",
                "conflictLesson", "criticismOverstated", "criticismDelivery", "criticismSubstance"));
        if (Boolean.TRUE.equals(request.includePrivateNote())) {
            keys.add("conflictPrivateNote");
        }
        return keys.stream().map(request::formValue).filter(value -> value != null && !value.isBlank()).toList();
    }

    private static boolean crisisIn(ObstacleHelpRequest request) {
        return conflictTexts(request).stream().anyMatch(AiSafety::looksLikeCrisis);
    }

    private ObstacleHelpAnswer crisis(AppUser user, String language) {
        boolean coachAlerted = alerts.raise(user.getId());
        usageLogRepository.save(AiUsage.entry(user, FEATURE, null, 0, 0, com.visionmapping.entity.enums.AiOutcome.CRISIS_HANDLED));
        return new ObstacleHelpAnswer(null, "CRISIS", AiSafety.crisisReply(language, settingsService.current().getHelpLines(), coachAlerted),
                List.of(), null, null, List.of(), List.of());
    }

    /**
     * AH-FR-40: for the conflict worksheet and criticism mining, the useful
     * point apart from the emotion, the other person's likely view, and one
     * calm next action, as cards. The private reaction is sent only when the
     * participant ticked it for this request.
     */
    private ObstacleHelpAnswer conflict(AppUser user, String apiKey, String language, PageAssistantService.View view,
                                        ObstacleHelpRequest request, String answerIn) {
        StringBuilder records = new StringBuilder(view.records());
        String note = request.formValue("conflictPrivateNote");
        if (Boolean.TRUE.equals(request.includePrivateNote()) && note != null && !note.isBlank()) {
            records.append("\nThe participant's own reaction, shared for this request only: ").append(AiContextBuilder.clipLong(note)).append('\n');
        }
        String task = view.guard() + "\nHelp the participant with this conflict or criticism: separate the useful point from the emotion "
                + "(criticismSubstance), describe the other person's likely view kindly and fairly (conflictOtherPerspective), and suggest "
                + "one calm next action (conflictNextAction). Do not take sides, blame anyone or diagnose anyone." + answerIn;
        Map<String, PageAssistantService.FieldRule> allowed = new java.util.LinkedHashMap<>();
        for (String field : List.of("criticismSubstance", "conflictOtherPerspective", "conflictNextAction")) {
            allowed.put(field, PageAssistantService.FieldRule.text(2000));
        }
        AiStructuredRequests.Tracked<AiDrafts.PageAssistantDraft> answer = requests.askTracked(user, apiKey, FEATURE, language,
                records.toString(), task, AiDrafts.PageAssistantDraft.class);
        List<PageChange> changes = PageAssistantService.checked(answer.value().changes(), allowed);
        return new ObstacleHelpAnswer(feedback.suggested(answer.usage(), changes.size()), "CONFLICT",
                answer.value().note() == null ? "" : clipTo(answer.value().note().trim(), 600), changes, null, null, List.of(), view.sources());
    }

    // ---------- AH-FR-37 ----------

    /** The step a new task goes under, and the latest date it may have (the step's, else its goal's). */
    record TaskStep(@Nullable VisionStep step, @Nullable LocalDate latest) {
    }

    private TaskStep taskStep(ObstacleHelpRequest request) {
        Obstacle saved = request.obstacleId() == null ? null : lookup.obstacle(request.obstacleId());
        VisionStep step = null;
        Long stepId = parseId(request.formValue("relatedStepId"));
        Long taskId = parseId(request.formValue("relatedTaskId"));
        if (stepId != null) {
            step = lookup.step(stepId);
        } else if (request.formValue("relatedStepId") == null && saved != null && saved.getRelatedStep() != null) {
            step = saved.getRelatedStep();
        }
        if (step == null) {
            TaskItem linked = taskId != null ? lookup.task(taskId)
                    : request.formValue("relatedTaskId") == null && saved != null ? saved.getRelatedTask() : null;
            step = linked == null ? null : linked.getStep();
        }
        LocalDate latest = null;
        if (step != null) {
            latest = step.getTargetDate() != null ? step.getTargetDate() : step.getGoal() == null ? null : step.getGoal().getTargetDate();
        }
        return new TaskStep(step, latest);
    }

    private ObstacleHelpAnswer nextTask(AppUser user, String apiKey, String language, PageAssistantService.View view, TaskStep taskStep,
                                        String answerIn) {
        LocalDate today = LocalDate.now(clock);
        String limit = taskStep.latest() == null ? "" : " The due date must be between " + today + " and " + taskStep.latest() + ".";
        String task = view.guard() + "\nPropose one next task, small enough to finish in a few days, that moves this obstacle forward. "
                + "The participant will own it." + limit + answerIn;
        AiStructuredRequests.Tracked<AiDrafts.NextTaskDraft> answer = requests.askTracked(user, apiKey, FEATURE, language,
                view.records(), task, AiDrafts.NextTaskDraft.class);
        AiDrafts.NextTaskDraft draft = answer.value();
        if (draft.title() == null || draft.title().isBlank()) {
            Long requestId = feedback.suggested(answer.usage(), 0);
            return new ObstacleHelpAnswer(requestId, "NEXT_TASK", "", List.of(), null, null, List.of(), view.sources());
        }
        VisionStep step = taskStep.step();
        // PB-FR-15, PB-FR-18: owner is the participant; a date outside today..the step's date is dropped for them to pick.
        TaskProposal proposal = new TaskProposal(clipTo(draft.title().trim(), 200),
                draft.description() == null ? "" : clipTo(draft.description().trim(), 1000), "Me",
                dueDate(draft.dueDate(), today, taskStep.latest()), priority(draft.priority()),
                step == null ? null : step.getId(), step == null ? null : step.getTitle(), taskStep.latest());
        Long requestId = feedback.suggested(answer.usage(), 1);
        String note = draft.reason() == null ? "" : clipTo(draft.reason().trim(), 300);
        return new ObstacleHelpAnswer(requestId, "NEXT_TASK", note, List.of(), null, proposal, List.of(), view.sources());
    }

    @Nullable
    static LocalDate dueDate(@Nullable String value, LocalDate today, @Nullable LocalDate latest) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(value.trim());
            return date.isBefore(today) || (latest != null && date.isAfter(latest)) ? null : date;
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    static String priority(@Nullable String value) {
        String upper = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(Priority.values()).anyMatch(item -> item.name().equals(upper)) ? upper : Priority.MEDIUM.name();
    }

    // ---------- AH-FR-41 ----------

    private ObstacleHelpAnswer prioritise(AppUser user, String language) {
        List<RankedObstacle> ranked = support.priority();
        if (ranked.isEmpty()) {
            return new ObstacleHelpAnswer(null, "PRIORITISE", "", List.of(), null, null, List.of(), List.of(PageAssistantService.SOURCE_PAGE));
        }
        String apiKey = accessService.requireAvailable(user, "OBSTACLES");
        List<RankedObstacle> top = ranked.subList(0, Math.min(TOP, ranked.size()));
        StringBuilder records = new StringBuilder("The participant's open obstacles in the app's order (severity, then blocked tasks "
                + "held up, then days open):\n");
        for (int index = 0; index < top.size(); index++) {
            RankedObstacle item = top.get(index);
            records.append(index + 1).append(". ").append(clip(item.title())).append(" – severity ").append(item.severity())
                    .append(", holds up ").append(item.blockedTasks()).append(" blocked task(s), open ").append(item.daysOpen())
                    .append(" days\n");
        }
        String task = "Give one sentence for each numbered obstacle on why to tackle it at that point, using only the facts given. "
                + "Keep the order. Answer in " + ("km".equals(language) ? "Khmer" : "English") + ".";
        AiStructuredRequests.Tracked<AiDrafts.PriorityReasons> answer = requests.askTracked(user, apiKey, FEATURE, language,
                records.toString(), task, AiDrafts.PriorityReasons.class);
        String[] reasons = new String[top.size()];
        for (AiDrafts.PriorityReason reason : Optional.ofNullable(answer.value().reasons()).orElse(List.of())) {
            if (reason != null && reason.rank() != null && reason.rank() >= 1 && reason.rank() <= top.size()
                    && reason.reason() != null && !reason.reason().isBlank()) {
                reasons[reason.rank() - 1] = clipTo(reason.reason().trim(), 300);
            }
        }
        List<RankedObstacle> out = new ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            RankedObstacle item = ranked.get(index);
            out.add(index < reasons.length && reasons[index] != null
                    ? new RankedObstacle(item.id(), item.title(), item.severity(), item.blockedTasks(), item.daysOpen(), reasons[index])
                    : item);
        }
        Long requestId = feedback.suggested(answer.usage(), 0);
        return new ObstacleHelpAnswer(requestId, "PRIORITISE", "", List.of(), null, null, out,
                List.of(PageAssistantService.SOURCE_PAGE, AiContextBuilder.OBSTACLES));
    }

    // ---------- helpers ----------

    private <T> ObstacleHelpAnswer cards(AiStructuredRequests.Tracked<T> answer, String mode, List<PageChange> changes,
                                         PageAssistantService.View view) {
        Long requestId = feedback.suggested(answer.usage(), changes.size());
        return new ObstacleHelpAnswer(requestId, mode, "", changes, null, null, List.of(), view.sources());
    }

    @Nullable
    private static Long parseId(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String clipTo(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
