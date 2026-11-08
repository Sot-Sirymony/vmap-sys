package com.visionmapping.service.ai;

import static com.visionmapping.service.ai.AiContextBuilder.clip;

import com.visionmapping.dto.request.GoalRequest;
import com.visionmapping.dto.request.ObstacleRequest;
import com.visionmapping.dto.request.PlanAssistantApplyRequest;
import com.visionmapping.dto.request.PlanAssistantRequest;
import com.visionmapping.dto.request.PlanClarification;
import com.visionmapping.dto.request.PlanDraftContent;
import com.visionmapping.dto.request.PlanDraftContent.DraftGoal;
import com.visionmapping.dto.request.PlanDraftContent.DraftHelper;
import com.visionmapping.dto.request.PlanDraftContent.DraftRisk;
import com.visionmapping.dto.request.PlanDraftContent.DraftStep;
import com.visionmapping.dto.request.PlanDraftContent.DraftTask;
import com.visionmapping.dto.request.VisionStepRequest;
import com.visionmapping.dto.response.AiPlanResponses.AssistantApplied;
import com.visionmapping.dto.response.AiPlanResponses.AssistantChange;
import com.visionmapping.dto.response.AiPlanResponses.AssistantProposals;
import com.visionmapping.dto.response.AiPlanResponses.DraftIssue;
import com.visionmapping.dto.response.AiPlanResponses.PlanIssue;
import com.visionmapping.dto.response.AiPlanResponses.SectionCheck;
import com.visionmapping.dto.response.AiPlanResponses.SectionIssue;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.Milestone;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.Severity;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.repository.GoalRepository;
import com.visionmapping.repository.MilestoneRepository;
import com.visionmapping.repository.PartnerRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.service.DreamService;
import com.visionmapping.service.GoalService;
import com.visionmapping.service.ObstacleService;
import com.visionmapping.service.VisionStepService;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.util.Copies;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * V11 Phase 7: the plan assistant (PB-FR-41 to PB-FR-50). It helps with one
 * section of the Plan page (surface PLAN) or of Build my plan (BUILDER).
 *
 * <p>For each section it gathers only that section's items (PB-NFR-12): the
 * dream, and the goals, steps, tasks, helpers or risks the section is about,
 * with a ref in brackets Claude can point at. Partner names and contacts are
 * never included. The same view feeds the chat context, the rule-based section
 * check that works without AI (PB-FR-49), and proposed change cards
 * (PB-FR-46), each checked here against the section's own refs and the date
 * and owner rules (PB-FR-47). Nothing is saved by a proposal: in Build my plan
 * the participant applies a card to the draft in the browser; on the Plan page
 * they save it through {@link #apply}, which uses the normal services.
 */
@Service
@RequiredArgsConstructor
public class PlanAssistantService {

    /** Usage-log feature for proposed changes; one of the plan features, so it can be rated and counted. */
    public static final String FEATURE = "PLAN_ASSISTANT";
    /** Usage-log feature for the plan assistant's chat answers. */
    public static final String CHAT_FEATURE = "PLAN_ASSISTANT_CHAT";
    /** Source key shown under a chat answer (AI-FR-05). */
    public static final String SOURCE = "section";
    static final int MAX_CHANGES = 5;
    private static final String DREAM_REF = "dream";

    private static final Set<String> PLAN_SECTIONS = Set.of("WHOLE", "STATEMENT", "GOALS", "STEPS", "DATES", "RESOURCES", "PEOPLE", "MILESTONES");
    private static final Set<String> BUILDER_SECTIONS = Set.of("WHOLE", "CLARIFY", "GOALS", "STEPS", "TASKS", "HELPERS", "REVIEW");

    private final EntityLookup lookup;
    private final AiAccessService accessService;
    private final PlanSessionGuard sessionGuard;
    private final AiStructuredRequests requests;
    private final AiPlanService planService;
    private final AiPlanFeedbackService feedback;
    private final PlanDraftCheckService draftCheck;
    private final PlanBuilderDraftService drafts;
    private final GoalRepository goalRepository;
    private final VisionStepRepository stepRepository;
    private final MilestoneRepository milestoneRepository;
    private final PartnerRepository partnerRepository;
    private final DreamService dreamService;
    private final GoalService goalService;
    private final VisionStepService stepService;
    private final ObstacleService obstacleService;
    private final Validator validator;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * One item the section shows. date is the item's own date; latest is the
     * latest date the item may have (its parent's date). Only editable items
     * may be the target of an EDIT card: in Build my plan, items already in the
     * plan are shown and never changed.
     */
    record Ref(String ref, String type, String title, boolean editable, LocalDate date, LocalDate latest) {
    }

    /** What the section holds: records for Claude, refs by ref, and its rule issues. */
    record View(String surface, String section, String records, Map<String, Ref> refs, List<SectionIssue> issues) {
        public View {
            refs = Copies.map(refs);
            issues = Copies.list(issues);
        }
    
    }

    /** The chat context for a section, and the sources to show under the answer. */
    public record ChatContext(String text, List<String> sources) {
        public ChatContext {
            sources = Copies.list(sources);
        }
    
    }

    // ---------- section check (PB-FR-49), no AI ----------

    @Transactional(readOnly = true)
    public SectionCheck sectionCheck(Long dreamId, PlanAssistantRequest request) {
        View view = view(lookup.currentUser(), dreamId, request.surface(), request.section(), request.itemRef());
        return new SectionCheck(view.surface(), view.section(), view.issues(), !kinds(view.section()).isEmpty());
    }

    // ---------- chat context (PB-FR-43 to PB-FR-45) ----------

    /** The plan assistant's part of the system prompt for a chat answer. Checks the dream is the participant's own. */
    @Transactional(readOnly = true)
    public ChatContext chatContext(AppUser user, Long dreamId, String surface, String section, String itemRef) {
        if (dreamId == null) {
            throw new BusinessRuleException("Open a dream to use the plan assistant.");
        }
        View view = view(user, dreamId, surface, section, itemRef);
        String text = "You are the plan assistant on the participant's " + pageName(view.surface()) + ". They are working on the "
                + sectionName(view.section()) + " section. Help with this section only: what it needs, why it matters in the "
                + "Vision Mapping method (Vision Area, Dream, Goal, Step, Task, Partner, Review), and a short example when it "
                + "helps. Refer to items by their titles, never by the refs in brackets. Keep answers short, under 150 words, "
                + "unless asked for more. You cannot change the plan: when a change would help, say what to change; the "
                + "participant can press Suggest changes to get it as a card they apply themselves. An owner is Me or a "
                + "role, never a person's name. Do not write a whole plan; the page's own buttons do that.\n\n"
                + AiContextBuilder.asParticipantData(view.records() + issuesText(view.issues()),
                "the participant's own plan for this section.");
        return new ChatContext(text, List.of(SOURCE));
    }

    // ---------- proposed changes (PB-FR-46, PB-FR-47) ----------

    public AssistantProposals propose(Long dreamId, PlanAssistantRequest request, String language) {
        AppUser user = lookup.currentUser();
        // V10 (SEC-01-02): ownership first, so another user's dream answers 404 whether or not AI is available.
        lookup.dream(dreamId);
        checkPair(request.surface(), request.section());
        Set<String> kinds = kinds(request.section());
        if (kinds.isEmpty()) {
            throw new BusinessRuleException("This section has no changes to suggest. Ask a question about it instead.");
        }
        String apiKey = accessService.requireAvailable(user);
        if ("BUILDER".equals(request.surface())) {
            sessionGuard.use(user.getId(), dreamId); // PB-NFR-03
        }
        View view = transactions.execute(status -> view(user, dreamId, request.surface(), request.section(), request.itemRef()));
        LocalDate today = LocalDate.now(clock);
        Ref focus = request.itemRef() == null ? null : view.refs().get(request.itemRef().trim());
        String task = "Propose up to " + MAX_CHANGES + " small, concrete changes to the " + sectionName(view.section())
                + " section of this plan, and nothing outside it. Allowed kinds: " + kindHelp(view.section()) + " Use the refs in "
                + "brackets exactly as written. Only items marked (can change) may be edited. Every date must be after " + today
                + " and not after the latest date shown for the item. An owner is Me or a role, never a person's name. "
                + (focus == null ? "" : "Focus on [" + focus.ref() + "]. ")
                + "If the section is already sound, return no changes and say so in the note.";
        AiStructuredRequests.Tracked<AiDrafts.AssistantChangeDrafts> answer = requests.askTracked(user, apiKey, FEATURE, language,
                view.records() + issuesText(view.issues()), task, AiDrafts.AssistantChangeDrafts.class);
        List<AssistantChange> changes = new ArrayList<>();
        for (AiDrafts.AssistantChangeDraft draft : Optional.ofNullable(answer.value().changes()).orElse(List.of())) {
            AssistantChange change = checked(draft, view, kinds, today);
            if (change != null && changes.size() < MAX_CHANGES) {
                changes.add(change);
            }
        }
        return new AssistantProposals(changes, limit(answer.value().note(), 500), feedback.suggested(answer.usage(), changes.size()));
    }

    @Nullable
    private AssistantChange checked(AiDrafts.AssistantChangeDraft draft, View view, Set<String> kinds, LocalDate today) {
        String kind = upper(draft.kind());
        if (!kinds.contains(kind)) {
            return null;
        }
        String reason = limit(draft.reason(), 300);
        Ref parent = blank(draft.parentRef()) ? null : view.refs().get(draft.parentRef().trim());
        return switch (kind) {
            case "EDIT" -> {
                Ref item = blank(draft.itemRef()) ? null : view.refs().get(draft.itemRef().trim());
                String field = upper(draft.field());
                if (item == null || !item.editable() || !editFields(view.section(), item.type()).contains(field)) {
                    yield null;
                }
                String value = switch (field) {
                    case "DATE" -> Optional.ofNullable(AiPlanService.futureDate(draft.value(), today, item.latest()))
                            .map(LocalDate::toString).orElse(null);
                    case "OWNER" -> blank(draft.value()) ? "Me" : limit(draft.value(), 160);
                    case "TITLE" -> limit(draft.value(), 220);
                    default -> limit(draft.value(), 3000);
                };
                yield value == null ? null : new AssistantChange(kind, item.type(), item.ref(), item.title(), field, value,
                        null, null, null, null, null, null, null, null, null, reason);
            }
            case "ADD_STEP", "ADD_TASK" -> {
                String parentType = "ADD_STEP".equals(kind) ? "GOAL" : "STEP";
                if (parent == null || !parentType.equals(parent.type()) || blank(draft.title())) {
                    yield null;
                }
                yield new AssistantChange(kind, "ADD_STEP".equals(kind) ? "STEP" : "TASK", null, null, null, null,
                        parent.ref(), parent.type(), parent.title(), limit(draft.title(), 220),
                        blank(draft.owner()) ? "Me" : limit(draft.owner(), 160),
                        AiPlanService.futureDate(draft.date(), today, parent.date()), null, null, null, reason);
            }
            case "ADD_RISK" -> {
                if (blank(draft.title())) {
                    yield null;
                }
                Ref tied = parent != null && ("GOAL".equals(parent.type()) || "STEP".equals(parent.type())) ? parent : null;
                yield new AssistantChange(kind, "OBSTACLE", null, null, null, null,
                        tied == null ? null : tied.ref(), tied == null ? null : tied.type(), tied == null ? null : tied.title(),
                        limit(draft.title(), 220), null, null, obstacleType(draft.obstacleType()).name(),
                        severity(draft.severity()).name(), limit(draft.solution(), 3000), reason);
            }
            default -> null;
        };
    }

    // ---------- saving a card on the Plan page (PB-FR-46) ----------

    /**
     * Saves one change card on the Plan page through the normal services, so
     * the usual validation and ownership checks apply. Every ref must be an
     * item of this dream's plan; another user's item answers 404.
     */
    @Transactional
    public AssistantApplied apply(Long dreamId, PlanAssistantApplyRequest request) {
        Dream dream = lookup.dream(dreamId);
        AssistantApplied applied = switch (request.kind()) {
            case "EDIT" -> edit(dream, request);
            case "ADD_STEP" -> addStep(dream, request);
            case "ADD_RISK" -> addRisk(dream, request);
            default -> throw new BusinessRuleException("This change can only be applied in Build my plan.");
        };
        if (request.requestId() != null) {
            feedback.recordOutcome(request.requestId(), FEATURE, request.edited() ? 0 : 1, request.edited() ? 1 : 0);
        }
        return applied;
    }

    private AssistantApplied edit(Dream dream, PlanAssistantApplyRequest request) {
        String ref = blank(request.itemRef()) ? "" : request.itemRef().trim();
        String field = request.field() == null ? "" : request.field();
        if (DREAM_REF.equals(ref)) {
            String dreamField = switch (field) {
                case "WHY" -> "WHY_IMPORTANT";
                case "SUCCESS" -> "SUCCESS_DEFINITION";
                case "DATE" -> "TARGET_DATE";
                default -> throw new BusinessRuleException("That part of the dream cannot be changed here.");
            };
            String value = "DATE".equals(field) ? dateOf(request).toString() : required(request.value(), "Write the new text first.");
            return new AssistantApplied("DREAM", dreamService.saveClarifiedField(dream.getId(), dreamField, value).id());
        }
        if (ref.startsWith("goal:")) {
            Goal goal = goalOf(dream, ref);
            String title = goal.getTitle();
            String success = goal.getSuccessCriteria();
            LocalDate date = goal.getTargetDate();
            switch (field) {
                case "TITLE" -> title = required(request.value(), "Write the new title first.");
                case "SUCCESS" -> success = required(request.value(), "Write the new text first.");
                case "DATE" -> date = dateOf(request);
                default -> throw new BusinessRuleException("That part of a goal cannot be changed here.");
            }
            GoalRequest update = new GoalRequest(dream.getId(), title, goal.getDescription(), success, goal.getPriority(),
                    goal.getLetterRank(), date, goal.getStatus(), goal.isMoonshot(), goal.getMoonshotVision(), goal.getScheduleMode());
            check(update);
            return new AssistantApplied("GOAL", goalService.updateGoal(goal.getId(), update).id());
        }
        if (ref.startsWith("step:")) {
            VisionStep step = stepOf(dream, ref);
            String title = step.getTitle();
            String owner = step.getOwner();
            LocalDate date = step.getTargetDate();
            switch (field) {
                case "TITLE" -> title = required(request.value(), "Write the new title first.");
                case "OWNER" -> owner = blank(request.value()) ? "Me" : request.value().trim();
                case "DATE" -> date = dateOf(request);
                default -> throw new BusinessRuleException("That part of a step cannot be changed here.");
            }
            // Resources, hours and "starts after" left out keep what is saved.
            VisionStepRequest update = new VisionStepRequest(step.getGoal().getId(), title, step.getDescription(), owner,
                    step.getSequenceNumber() == null ? 1 : step.getSequenceNumber(), step.isComplex(), step.getPriority(), date,
                    step.getStatus(), null, null, null);
            check(update);
            return new AssistantApplied("STEP", stepService.updateStep(step.getId(), update).id());
        }
        throw new ResourceNotFoundException("That item is not in this plan.");
    }

    private AssistantApplied addStep(Dream dream, PlanAssistantApplyRequest request) {
        Goal goal = goalOf(dream, blank(request.parentRef()) ? "" : request.parentRef().trim());
        int next = stepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(goal.getId(), lookup.userId()).size() + 1;
        VisionStepRequest create = new VisionStepRequest(goal.getId(), required(request.title(), "Write the step's title first."), null,
                blank(request.owner()) ? "Me" : request.owner().trim(), next, false, Priority.MEDIUM, dateOf(request),
                WorkStatus.NOT_STARTED, null, null, null);
        check(create);
        return new AssistantApplied("STEP", stepService.createStep(create).id());
    }

    private AssistantApplied addRisk(Dream dream, PlanAssistantApplyRequest request) {
        String parent = blank(request.parentRef()) ? "" : request.parentRef().trim();
        Long goalId = null;
        Long stepId = null;
        if (parent.startsWith("goal:")) {
            goalId = goalOf(dream, parent).getId();
        } else if (parent.startsWith("step:")) {
            VisionStep step = stepOf(dream, parent);
            stepId = step.getId();
            goalId = step.getGoal().getId();
        } else if (!parent.isEmpty()) {
            throw new ResourceNotFoundException("That item is not in this plan.");
        }
        ObstacleRequest create = new ObstacleRequest(dream.getId(), goalId, stepId, null,
                required(request.title(), "Write what could get in the way first."), null, obstacleType(request.obstacleType()),
                severity(request.severity()), null, blank(request.solution()) ? null : request.solution().trim(), null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, ObstacleStatus.OPEN);
        check(create);
        return new AssistantApplied("OBSTACLE", obstacleService.createObstacle(create).id());
    }

    private Goal goalOf(Dream dream, String ref) {
        Goal goal = lookup.goal(idOf(ref, "goal:"));
        if (goal.isArchived() || !goal.getDream().getId().equals(dream.getId())) {
            throw new ResourceNotFoundException("That goal is not in this plan.");
        }
        return goal;
    }

    private VisionStep stepOf(Dream dream, String ref) {
        VisionStep step = lookup.step(idOf(ref, "step:"));
        if (step.isArchived() || !step.getGoal().getDream().getId().equals(dream.getId())) {
            throw new ResourceNotFoundException("That step is not in this plan.");
        }
        return step;
    }

    private static Long idOf(@Nullable String ref, String prefix) {
        if (ref == null) {
            throw new ResourceNotFoundException("That item is not in this plan.");
        }
        try {
            return Long.parseLong(ref.substring(prefix.length()));
        } catch (NumberFormatException | IndexOutOfBoundsException exception) {
            // A reference the client made up or mangled: the same 404 as an unknown id.
            throw new ResourceNotFoundException("That item is not in this plan.");
        }
    }

    private static LocalDate dateOf(PlanAssistantApplyRequest request) {
        if (request.date() != null) {
            return request.date();
        }
        try {
            return LocalDate.parse(request.value() == null ? "" : request.value().trim());
        } catch (DateTimeParseException exception) {
            throw new BusinessRuleException("Pick a date first.");
        }
    }

    private <T> void check(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new BusinessRuleException("This change needs a fix: " + violations.iterator().next().getMessage());
        }
    }

    // ---------- the section's view ----------

    private View view(AppUser user, Long dreamId, String surface, String section, String itemRef) {
        Dream dream = lookup.dream(dreamId);
        checkPair(surface, section);
        View view = "PLAN".equals(surface) ? planView(user, dream, section) : builderView(user, dream, section);
        if (!blank(itemRef) && view.refs().containsKey(itemRef.trim())) {
            Ref focus = view.refs().get(itemRef.trim());
            return new View(view.surface(), view.section(),
                    view.records() + "\nThe participant is asking about [" + focus.ref() + "] " + clip(focus.title()) + ".\n",
                    view.refs(), view.issues());
        }
        return view;
    }

    private View planView(AppUser user, Dream dream, String section) {
        Map<String, Ref> refs = new LinkedHashMap<>();
        StringBuilder out = dreamRecords(dream, refs, true);
        boolean goals = !"STATEMENT".equals(section) && !"MILESTONES".equals(section);
        boolean steps = Set.of("STEPS", "DATES", "RESOURCES", "WHOLE").contains(section);
        if (goals) {
            List<Goal> list = goalRepository.findByDream_IdAndUser_IdAndArchivedFalse(dream.getId(), user.getId());
            if (list.isEmpty()) {
                out.append("No goals yet.\n");
            }
            for (Goal goal : list) {
                String ref = "goal:" + goal.getId();
                refs.put(ref, new Ref(ref, "GOAL", goal.getTitle(), true, goal.getTargetDate(), dream.getTargetDate()));
                out.append("Goal [").append(ref).append("] (can change) ").append(clip(goal.getTitle()))
                        .append(" | success: ").append(blank(goal.getSuccessCriteria()) ? "not written" : clip(goal.getSuccessCriteria()))
                        .append(" | target: ").append(goal.getTargetDate() == null ? "none" : goal.getTargetDate())
                        .append(" | latest allowed: ").append(dream.getTargetDate() == null ? "none" : dream.getTargetDate())
                        .append(" | ").append(goal.getStatus()).append('\n');
                if (!steps) {
                    continue;
                }
                List<VisionStep> goalSteps = stepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(goal.getId(), user.getId()).stream()
                        .sorted(Comparator.comparing(VisionStep::getSequenceNumber, Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList();
                if (goalSteps.isEmpty()) {
                    out.append("  No steps yet.\n");
                }
                for (VisionStep step : goalSteps) {
                    String stepRef = "step:" + step.getId();
                    refs.put(stepRef, new Ref(stepRef, "STEP", step.getTitle(), true, step.getTargetDate(), goal.getTargetDate()));
                    out.append("  Step [").append(stepRef).append("] (can change) ").append(clip(step.getTitle()))
                            .append(" | owner: ").append(blank(step.getOwner()) ? "none" : clip(step.getOwner()))
                            .append(" | due: ").append(step.getTargetDate() == null ? "none" : step.getTargetDate())
                            .append(" | latest allowed: ").append(goal.getTargetDate() == null ? "none" : goal.getTargetDate())
                            .append(" | resources: ").append(blank(step.getResources()) ? "none" : clip(step.getResources()))
                            .append(" | ").append(step.getStatus()).append('\n');
                }
            }
        }
        if ("PEOPLE".equals(section)) {
            // Whether a partner is linked, never who (AI-NFR-04).
            out.append(partnerRepository.existsAnyPartnerForDream(user.getId(), dream.getId())
                    ? "At least one partner is linked to this dream or one of its goals.\n"
                    : "No partner is linked to this dream or its goals yet.\n");
        }
        if ("MILESTONES".equals(section)) {
            List<Milestone> milestones = milestoneRepository.findByDream_IdAndUser_IdAndArchivedFalseOrderByAchievedOnDesc(
                    dream.getId(), user.getId());
            out.append(milestones.isEmpty() ? "No milestones recorded yet.\n" : "Milestones reached:\n");
            milestones.stream().limit(10).forEach(milestone -> out.append("- ").append(clip(milestone.getTitle()))
                    .append(", ").append(milestone.getAchievedOn()).append('\n'));
        }
        Set<String> kinds = switch (section) {
            case "STEPS" -> Set.of("NO_STEPS");
            case "DATES" -> Set.of("MISSING_OWNER", "MISSING_DATE", "AFTER_GOAL_DATE", "OVERDUE");
            case "RESOURCES" -> Set.of("MISSING_RESOURCE");
            case "WHOLE" -> null;
            default -> Set.of();
        };
        List<SectionIssue> issues = new ArrayList<>();
        for (PlanIssue issue : planService.ruleCheck(user, dream)) {
            if (kinds == null || kinds.contains(issue.kind())) {
                issues.add(new SectionIssue(issue.kind(), issue.itemType(), issue.itemType().toLowerCase() + ":" + issue.itemId(),
                        issue.itemTitle(), issue.problem(), issue.fix()));
            }
        }
        return new View("PLAN", section, out.toString(), refs, issues);
    }

    private View builderView(AppUser user, Dream dream, String section) {
        PlanDraftContent content;
        try {
            content = drafts.stored(dream.getId());
        } catch (ResourceNotFoundException exception) {
            content = new PlanDraftContent(List.of(), List.of(), List.of(), List.of());
        }
        Map<String, Ref> refs = new LinkedHashMap<>();
        StringBuilder out = dreamRecords(dream, refs, true);
        boolean all = "REVIEW".equals(section) || "WHOLE".equals(section);
        if ("CLARIFY".equals(section) || all) {
            for (PlanClarification clarification : Optional.ofNullable(content.clarifications()).orElse(List.of())) {
                out.append("Asked: ").append(clip(clarification.question())).append(" | answered: ")
                        .append(clip(clarification.answer())).append('\n');
            }
        }
        boolean goals = !"CLARIFY".equals(section);
        boolean steps = Set.of("STEPS", "TASKS", "HELPERS").contains(section) || all;
        boolean tasks = "TASKS".equals(section) || all;
        if (goals) {
            out.append("\nThe draft plan, with refs in brackets:\n");
            if (Optional.ofNullable(content.goals()).orElse(List.of()).isEmpty()) {
                out.append("No goals in the draft yet.\n");
            }
            for (DraftGoal goal : Optional.ofNullable(content.goals()).orElse(List.of())) {
                if (blank(goal.key())) {
                    continue;
                }
                boolean isNew = goal.existingId() == null;
                LocalDate goalDate = isNew ? goal.targetDate() : lookup.goal(goal.existingId()).getTargetDate();
                refs.put(goal.key(), new Ref(goal.key(), "GOAL", goal.title(), isNew, goalDate, dream.getTargetDate()));
                out.append("Goal [").append(clip(goal.key())).append("] ").append(isNew ? "(new, can change) " : "(already in the plan) ")
                        .append(clip(goal.title()))
                        .append(" | success: ").append(blank(goal.successCriteria()) ? "not written" : clip(goal.successCriteria()))
                        .append(" | target: ").append(goalDate == null ? "none" : goalDate)
                        .append(" | latest allowed: ").append(dream.getTargetDate() == null ? "none" : dream.getTargetDate()).append('\n');
                if (!steps) {
                    continue;
                }
                for (DraftStep step : Optional.ofNullable(goal.steps()).orElse(List.of())) {
                    if (blank(step.key())) {
                        continue;
                    }
                    boolean stepNew = step.existingId() == null;
                    LocalDate stepDate = stepNew ? step.targetDate() : lookup.step(step.existingId()).getTargetDate();
                    refs.put(step.key(), new Ref(step.key(), "STEP", step.title(), stepNew, stepDate, goalDate));
                    out.append("  Step [").append(clip(step.key())).append("] ").append(stepNew ? "(new, can change) " : "(already in the plan) ")
                            .append(clip(step.title()))
                            .append(" | owner: ").append(blank(step.owner()) ? "none" : clip(step.owner()))
                            .append(" | due: ").append(stepDate == null ? "none" : stepDate)
                            .append(" | latest allowed: ").append(goalDate == null ? "none" : goalDate)
                            .append(step.estimatedHours() == null ? "" : " | " + step.estimatedHours() + " h")
                            .append(step.complex() ? " | complex" : "").append('\n');
                    if (!tasks) {
                        continue;
                    }
                    for (DraftTask task : Optional.ofNullable(step.tasks()).orElse(List.of())) {
                        if (blank(task.key())) {
                            continue;
                        }
                        refs.put(task.key(), new Ref(task.key(), "TASK", task.title(), true, task.dueDate(), stepDate));
                        out.append("    Task [").append(clip(task.key())).append("] (new, can change) ").append(clip(task.title()))
                                .append(" | owner: ").append(blank(task.owner()) ? "none" : clip(task.owner()))
                                .append(" | due: ").append(task.dueDate() == null ? "none" : task.dueDate())
                                .append(" | latest allowed: ").append(stepDate == null ? "none" : stepDate).append('\n');
                    }
                }
            }
        }
        if ("HELPERS".equals(section) || all) {
            // Roles and support types only: a helper's name stays out (AI-NFR-04).
            for (DraftHelper helper : Optional.ofNullable(content.helpers()).orElse(List.of())) {
                out.append("Helper needed: ").append(clip(helper.role()))
                        .append(helper.supportType() == null ? "" : " (" + helper.supportType() + ")")
                        .append(helper.partnerId() != null ? " | linked to one of their partners" : " | new contact").append('\n');
            }
            for (DraftRisk risk : Optional.ofNullable(content.risks()).orElse(List.of())) {
                out.append("Risk: ").append(clip(risk.title())).append(" | ").append(risk.obstacleType())
                        .append(" | severity ").append(risk.severity()).append('\n');
            }
        }
        List<SectionIssue> issues = new ArrayList<>();
        for (DraftIssue issue : draftCheck.ruleIssues(dream.getId(), content)) {
            boolean keep = switch (section) {
                case "STEPS" -> "NO_STEPS".equals(issue.kind()) || ("STEP".equals(issue.itemType()) && !"COMPLEX_NO_TASKS".equals(issue.kind()));
                case "TASKS" -> "TASK".equals(issue.itemType()) || "COMPLEX_NO_TASKS".equals(issue.kind());
                case "REVIEW", "WHOLE" -> true;
                default -> false;
            };
            if (keep) {
                issues.add(new SectionIssue(issue.kind(), issue.itemType(), issue.itemKey(), issue.itemTitle(), issue.problem(), issue.fix()));
            }
        }
        return new View("BUILDER", section, out.toString(), refs, issues);
    }

    private StringBuilder dreamRecords(Dream dream, Map<String, Ref> refs, boolean editable) {
        refs.put(DREAM_REF, new Ref(DREAM_REF, "DREAM", dream.getTitle(), editable, dream.getTargetDate(), null));
        StringBuilder out = new StringBuilder();
        out.append("Today is ").append(LocalDate.now(clock)).append(".\n");
        out.append("Dream [").append(DREAM_REF).append("] (can change) ").append(clip(dream.getTitle()))
                .append(" | target: ").append(dream.getTargetDate() == null ? "none" : dream.getTargetDate()).append('\n');
        out.append("  Why it matters: ").append(blank(dream.getWhyImportant()) ? "not written" : clip(dream.getWhyImportant())).append('\n');
        out.append("  Success looks like: ").append(blank(dream.getSuccessDefinition()) ? "not written" : clip(dream.getSuccessDefinition()))
                .append('\n');
        return out;
    }

    private static String issuesText(List<SectionIssue> issues) {
        if (issues.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder("\nProblems the plan check found in this section:\n");
        issues.stream().limit(15).forEach(issue -> out.append("- ").append(clip(issue.itemTitle())).append(": ")
                .append(issue.problem()).append('\n'));
        return out.toString();
    }

    // ---------- section rules ----------

    private static void checkPair(String surface, String section) {
        Set<String> sections = "PLAN".equals(surface) ? PLAN_SECTIONS : "BUILDER".equals(surface) ? BUILDER_SECTIONS : Set.of();
        if (!sections.contains(section)) {
            throw new BusinessRuleException("That section is not on this page.");
        }
    }

    /** The kinds of change card a section takes; none for sections the assistant only explains. */
    static Set<String> kinds(String section) {
        return switch (section) {
            case "STATEMENT", "CLARIFY", "GOALS", "DATES" -> Set.of("EDIT");
            case "STEPS" -> Set.of("EDIT", "ADD_STEP");
            case "TASKS" -> Set.of("EDIT", "ADD_TASK");
            case "RESOURCES", "HELPERS" -> Set.of("ADD_RISK");
            case "REVIEW" -> Set.of("EDIT", "ADD_STEP", "ADD_TASK");
            default -> Set.of();
        };
    }

    /** The fields an EDIT card may change, for an item type in a section. */
    static Set<String> editFields(String section, String type) {
        return switch (section) {
            case "STATEMENT", "CLARIFY" -> "DREAM".equals(type) ? Set.of("WHY", "SUCCESS", "DATE") : Set.of();
            case "GOALS" -> "GOAL".equals(type) ? Set.of("TITLE", "SUCCESS", "DATE") : Set.of();
            case "STEPS" -> "STEP".equals(type) ? Set.of("TITLE", "OWNER", "DATE") : Set.of();
            case "DATES" -> "STEP".equals(type) ? Set.of("OWNER", "DATE") : Set.of();
            case "TASKS" -> "TASK".equals(type) ? Set.of("TITLE", "OWNER", "DATE") : Set.of();
            case "REVIEW" -> "STEP".equals(type) || "TASK".equals(type) ? Set.of("TITLE", "OWNER", "DATE") : Set.of();
            default -> Set.of();
        };
    }

    private static String kindHelp(String section) {
        return switch (section) {
            case "STATEMENT", "CLARIFY" -> "EDIT the dream [dream]: WHY (why it matters), SUCCESS (what success looks like) or DATE.";
            case "GOALS" -> "EDIT a goal: TITLE (specific and measurable), SUCCESS (how they will know it is reached) or DATE.";
            case "STEPS" -> "EDIT a step: TITLE (one clear action), OWNER or DATE; ADD_STEP under a goal for a missing step.";
            case "DATES" -> "EDIT a step: OWNER or DATE.";
            case "TASKS" -> "EDIT a task: TITLE, OWNER or DATE; ADD_TASK under a step, most of all a complex step without tasks.";
            case "RESOURCES", "HELPERS" -> "ADD_RISK: something likely to get in the way, with its type, severity and a solution.";
            case "REVIEW" -> "EDIT a step or task: TITLE, OWNER or DATE; ADD_STEP under a goal; ADD_TASK under a step.";
            default -> "";
        };
    }

    private static String sectionName(String section) {
        return switch (section) {
            case "STATEMENT" -> "Dream statement";
            case "GOALS" -> "Goals";
            case "STEPS" -> "Steps";
            case "DATES" -> "Owners and dates";
            case "RESOURCES" -> "Resources needed";
            case "PEOPLE" -> "People";
            case "MILESTONES" -> "Milestones";
            case "CLARIFY" -> "Clarify";
            case "TASKS" -> "Tasks";
            case "HELPERS" -> "Helpers and risks";
            case "REVIEW" -> "Check and add";
            default -> "whole plan";
        };
    }

    private static String pageName(String surface) {
        return "PLAN".equals(surface) ? "Plan page" : "Build my plan page";
    }

    private static ObstacleType obstacleType(String value) {
        try {
            return value == null ? ObstacleType.OTHER : ObstacleType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return ObstacleType.OTHER;
        }
    }

    private static Severity severity(String value) {
        try {
            return value == null ? Severity.MEDIUM : Severity.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return Severity.MEDIUM;
        }
    }

    private static String required(String value, String message) {
        if (blank(value)) {
            throw new BusinessRuleException(message);
        }
        return value.trim();
    }

    private static String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    @Nullable
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
