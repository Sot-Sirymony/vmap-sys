package com.visionmapping.service.ai;

import static com.visionmapping.service.ai.AiContextBuilder.clip;
import static com.visionmapping.service.ai.AiContextBuilder.clipLong;

import com.visionmapping.dto.request.PageAssistantRequest;
import com.visionmapping.dto.response.LearningModuleDetailResponse;
import com.visionmapping.dto.response.LearningModuleSummaryResponse;
import com.visionmapping.dto.response.PageAssistantResponses.PageBaselines;
import com.visionmapping.dto.response.PageAssistantResponses.PageChange;
import com.visionmapping.dto.response.PageAssistantResponses.PageCheck;
import com.visionmapping.dto.response.PageAssistantResponses.PageFeatureMetrics;
import com.visionmapping.dto.response.PageAssistantResponses.PageIssue;
import com.visionmapping.dto.response.PageAssistantResponses.PageMetrics;
import com.visionmapping.dto.response.PageAssistantResponses.PageProposals;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.ChainAssessment;
import com.visionmapping.entity.CommunicationMessage;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.ModuleCompletion;
import com.visionmapping.entity.Obstacle;
import com.visionmapping.entity.Partner;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.CommunicationStatus;
import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.entity.enums.Severity;
import com.visionmapping.repository.AiUsageLogRepository;
import com.visionmapping.repository.ChainAssessmentRepository;
import com.visionmapping.repository.CommunicationMessageRepository;
import com.visionmapping.repository.DreamRepository;
import com.visionmapping.repository.GoalRepository;
import com.visionmapping.repository.ModuleCompletionRepository;
import com.visionmapping.repository.ObstacleRepository;
import com.visionmapping.repository.PartnerRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.security.Roles;
import com.visionmapping.service.LearningModuleService;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.util.Copies;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V15 Phase 1: the page assistant on the Modules, Assessment, Obstacles,
 * Communication and Work Style pages (AH-FR-01 to AH-FR-12).
 *
 * <p>For each page it gathers only what section B of the BRD allows: never
 * partner names, emails, phones or notes on the Obstacles page, only a
 * partner's name and role on the Communication page, never the private
 * conflict note, never other modules' answers. The open item is looked up as
 * the participant's own before anything else, so another user's obstacle or
 * message answers 404 whether or not AI is available (AH-NFR-07).
 *
 * <p>The same view feeds the chat context, the rule checks that work without
 * AI (AH-FR-10), and the change cards (AH-FR-06), which are checked here
 * against the page's allowed fields and values before they are shown
 * (AH-FR-07). Nothing is saved by a proposal: a card only fills the form in the
 * browser, and the page's own Save stores it through the normal rules.
 */
@Service
@RequiredArgsConstructor
public class PageAssistantService {

    /** Usage-log feature for the page assistant's chat answers (AH-FR-55). */
    public static final String CHAT_FEATURE = "PAGE_ASSISTANT_CHAT";
    /** Usage-log feature for the page assistant's change cards. */
    public static final String FEATURE = "PAGE_ASSISTANT";
    /** AH-FR-55: every V15 feature name, so their answers can be rated, counted and shown to admins. */
    public static final Set<String> FEATURES = Set.of(
            CHAT_FEATURE, FEATURE, "MODULE_NEXT", "MODULE_HELP", "ASSESSMENT_EXPLAIN", "ASSESSMENT_PRACTICE", "OBSTACLE_HELP",
            "MESSAGE_FIELD_HELP", "MESSAGE_CHECK", "MESSAGE_ADAPT", "MESSAGE_OBJECTIONS", "MESSAGE_BODY", "MEETING_PREP",
            "THANK_YOU_DRAFT", "FOLLOW_UP_DRAFT", "REPLY_HELP", "WORK_STYLE_EXPLAIN", "WORK_STYLE_TIPS", "WORK_STYLE_PARTNER");
    /** Conversation tag prefix, so page conversations never mix with plan assistant ones (AH-FR-11). */
    public static final String SECTION_PREFIX = "PAGE:";
    static final int MAX_CHANGES = 5;
    static final int MAX_VALUE = 2000;
    static final int MAX_BODY = 4000;
    static final int MAX_OTHER_OBSTACLES = 10;
    static final int MAX_SOLVED = 3;
    static final int MESSAGE_WORD_LIMIT = 250;
    private static final int METRIC_MAX_DAYS = 365;

    /** Source keys shown under an answer (AH-FR-08). */
    static final String SOURCE_PAGE = "page";
    static final String SOURCE_MODULES = "modules";
    static final String SOURCE_ASSESSMENT = "assessment";
    static final String SOURCE_OBSTACLE = "obstacle";
    static final String SOURCE_MESSAGE = "message";
    static final String SOURCE_EARLIER = "earlierMessages";
    static final String SOURCE_LINKED = "linked";
    static final String SOURCE_WORK_STYLE = "workStyle";
    static final String SOURCE_FORM = "form";
    static final String SOURCE_SOLVED = "solvedBefore";

    // ---------- what each page may read from the unsaved form (section B) and change (AH-FR-07) ----------

    private static final List<String> OBSTACLE_TEXT_FIELDS = List.of(
            "title", "description", "solution", "rootCause", "creativeAlternatives");
    /** Read from the unsaved obstacle form. Never the private reaction note or partner fields. */
    private static final Set<String> OBSTACLE_READ = Set.of(
            "title", "description", "obstacleType", "severity", "status", "solution", "rootCause", "creativeAlternatives",
            "conflictIncident", "conflictCost", "conflictOtherPerspective", "conflictLesson", "conflictNextAction",
            "conflictExpectation", "criticismOverstated", "criticismDelivery", "criticismSubstance",
            "relatedDreamId", "relatedGoalId", "relatedStepId", "relatedTaskId");
    private static final Set<String> OBSTACLE_WRITE = Set.of(
            "title", "description", "obstacleType", "severity", "solution", "rootCause", "creativeAlternatives");

    /** The twelve structured message fields plus subject, audience and the body (AH-FR-43). */
    static final List<String> MESSAGE_TEXT_FIELDS = List.of(
            "audience", "purpose", "subject", "hook", "wordPicture", "problem", "request", "objectionsAndAnswers", "socialProof",
            "valueComparison", "benefitToPartner", "expectedOutcome", "callToAction", "messageBody");
    private static final Set<String> MESSAGE_READ;
    private static final Set<String> MESSAGE_WRITE;

    static {
        Set<String> read = new LinkedHashSet<>(MESSAGE_TEXT_FIELDS);
        read.addAll(List.of("status", "followUpDate", "partnerId", "relatedDreamId", "relatedGoalId", "relatedTaskId"));
        MESSAGE_READ = Set.copyOf(read);
        Set<String> write = new LinkedHashSet<>(MESSAGE_TEXT_FIELDS);
        write.add("status");
        MESSAGE_WRITE = Set.copyOf(write);
    }

    /** A module exercise field in the unsaved form: row number and field key, for example row0.fear. */
    private static final Pattern MODULE_FIELD = Pattern.compile("row(\\d)\\.([A-Za-z0-9_]{1,40})");
    private static final int MAX_MODULE_ROWS = 3;

    private static final Map<String, String> CHAIN_LABELS = new LinkedHashMap<>();

    static {
        CHAIN_LABELS.put("settlingScore", "Settling for average");
        CHAIN_LABELS.put("fearScore", "Fear of failure");
        CHAIN_LABELS.put("criticismScore", "Avoiding criticism");
        CHAIN_LABELS.put("visionScore", "Unclear vision");
        CHAIN_LABELS.put("knowledgeScore", "Not knowing how");
        CHAIN_LABELS.put("resourcesScore", "Lack of resources");
    }

    private final EntityLookup lookup;
    private final AiAccessService accessService;
    private final AiStructuredRequests requests;
    private final AiPlanFeedbackService feedback;
    private final LearningModuleService moduleService;
    private final ChainAssessmentRepository assessmentRepository;
    private final ObstacleRepository obstacleRepository;
    private final CommunicationMessageRepository messageRepository;
    private final PartnerRepository partnerRepository;
    private final DreamRepository dreamRepository;
    private final GoalRepository goalRepository;
    private final VisionStepRepository stepRepository;
    private final ModuleCompletionRepository completionRepository;
    private final AiUsageLogRepository usageLogRepository;
    private final org.springframework.transaction.support.TransactionTemplate transactions;
    private final Clock clock;

    /** What the area on screen holds: records for Claude, sources, rule issues and the fields cards may change. */
    record View(String page, String area, String records, List<String> sources, List<PageIssue> issues,
                Map<String, FieldRule> writable, String guard) {
        View {
            sources = Copies.list(sources);
            issues = Copies.list(issues);
            writable = Copies.map(writable);
        }
    }

    /** One field a card may change: its allowed values (empty means free text) and longest value. */
    record FieldRule(Set<String> allowed, int maxLength) {
        FieldRule {
            allowed = Copies.set(allowed);
        }

        static FieldRule text(int max) {
            return new FieldRule(Set.of(), max);
        }

        static FieldRule oneOf(Enum<?>[] values) {
            return new FieldRule(Arrays.stream(values).map(Enum::name).collect(Collectors.toSet()), 40);
        }
    }

    /** The chat context for an area, and the sources to show under the answer. */
    public record ChatContext(String text, List<String> sources) {
        public ChatContext {
            sources = Copies.list(sources);
        }
    }

    /**
     * AH-NFR-07: the open item must be the participant's own. Called before AI
     * availability is checked, so another user's record always answers 404.
     */
    @Transactional(readOnly = true)
    public void checkOwnership(PageAssistantRequest request) {
        view(lookup.currentUser(), request);
    }

    /** AH-FR-10: the rule checks for the area; never uses AI. */
    @Transactional(readOnly = true)
    public PageCheck check(PageAssistantRequest request) {
        View view = view(lookup.currentUser(), request);
        return new PageCheck(view.page(), view.area(), !view.writable().isEmpty(), view.issues());
    }

    /** AH-FR-05, AH-FR-11: the chat context for the area, with the page's guard rules. */
    @Transactional(readOnly = true)
    public ChatContext chatContext(AppUser user, PageAssistantRequest request) {
        View view = view(user, request);
        String text = view.guard() + "\n\n" + AiContextBuilder.asParticipantData(view.records(),
                "what the participant has open on this page, saved and unsaved. Use it to make your help specific.");
        return new ChatContext(text, view.sources());
    }

    /** AH-FR-06 to AH-FR-08: change cards for the area; nothing is saved. */
    public PageProposals propose(PageAssistantRequest request, String language) {
        AppUser user = lookup.currentUser();
        // Ownership first (AH-NFR-07), then AI availability. Linked records load lazily, so the view is read in a transaction.
        View view = viewInTransaction(user, request);
        if (view.writable().isEmpty()) {
            return new PageProposals(null, "", List.of(), view.sources());
        }
        String apiKey = accessService.requireAvailable(user, view.page());
        String fields = view.writable().entrySet().stream()
                .map(entry -> entry.getValue().allowed().isEmpty()
                        ? entry.getKey()
                        : entry.getKey() + " (one of " + String.join(", ", new java.util.TreeSet<>(entry.getValue().allowed())) + ")")
                .collect(Collectors.joining("; "));
        String task = view.guard() + "\nPropose changes to the form on screen that would make it clearer and more useful. "
                + "Allowed fields: " + fields + ". Use only these field names. Do not change a field the participant has "
                + "already written well. Never invent facts about the participant's life, people, numbers or quotes. "
                + "At most " + MAX_CHANGES + " changes. Answer in " + ("km".equals(language) ? "Khmer" : "English") + ".";
        // AH-FR-55: help with one message field is logged as MESSAGE_FIELD_HELP.
        String feature = "COMMUNICATION".equals(view.page()) && view.area().startsWith("field:") ? "MESSAGE_FIELD_HELP" : FEATURE;
        AiStructuredRequests.Tracked<AiDrafts.PageAssistantDraft> answer = requests.askTracked(user, apiKey, feature, language,
                view.records(), task, AiDrafts.PageAssistantDraft.class);
        List<PageChange> changes = checked(answer.value().changes(), view.writable());
        Long requestId = feedback.suggested(answer.usage(), changes.size());
        String note = answer.value().note() == null ? "" : clipTo(answer.value().note().trim(), 600);
        return new PageProposals(requestId, note, changes, view.sources());
    }

    /** AH-FR-09: applied and edited cards for an answer of any V15 feature, counted with no text; the rest count as dismissed. */
    @Transactional
    public void recordOutcome(Long requestId, int applied, int edited) {
        feedback.recordOutcome(requestId, new com.visionmapping.dto.request.AiPlanOutcomeRequest(applied, edited));
    }

    /** AH-FR-07, AH-NFR-04: drops any card for a field not allowed here, with a value outside the list, or empty. */
    static List<PageChange> checked(List<AiDrafts.PageChangeDraft> drafts, Map<String, FieldRule> writable) {
        List<PageChange> out = new ArrayList<>();
        Map<String, Integer> perField = new HashMap<>();
        for (AiDrafts.PageChangeDraft draft : Optional.ofNullable(drafts).orElse(List.of())) {
            if (draft == null || draft.field() == null || draft.value() == null || out.size() >= MAX_CHANGES) {
                continue;
            }
            String field = draft.field().trim();
            FieldRule rule = writable.get(field);
            String value = draft.value().trim();
            if (rule == null || value.isEmpty()) {
                continue;
            }
            if (!rule.allowed().isEmpty()) {
                value = value.toUpperCase(java.util.Locale.ROOT).replace(' ', '_');
                if (!rule.allowed().contains(value)) {
                    continue;
                }
            }
            // Two versions of one field at most (AH-FR-43 "two alternatives").
            if (perField.merge(field, 1, Integer::sum) > 2) {
                continue;
            }
            String reason = draft.reason() == null ? "" : clipTo(draft.reason().trim(), 300);
            out.add(new PageChange(field, clipTo(value, rule.maxLength()), reason));
        }
        return List.copyOf(out);
    }

    // ---------- per-page views (section B) ----------

    /** V15 Phase 2: the same view for the page helpers, so they read and check exactly what the assistant does. */
    View viewInTransaction(AppUser user, PageAssistantRequest request) {
        return java.util.Objects.requireNonNull(transactions.execute(status -> view(user, request)));
    }

    private View view(AppUser user, PageAssistantRequest request) {
        return switch (request.page()) {
            case "MODULES" -> modulesView(user, request);
            case "MODULE" -> moduleView(request);
            case "ASSESSMENT" -> assessmentView(user, request);
            case "OBSTACLES" -> obstaclesView(user, request);
            case "COMMUNICATION" -> communicationView(user, request);
            case "WORK_STYLE" -> workStyleView(user, request);
            default -> throw new IllegalArgumentException("Unknown page: " + request.page());
        };
    }

    private View modulesView(AppUser user, PageAssistantRequest request) {
        StringBuilder out = header("Modules list", request.area());
        List<LearningModuleSummaryResponse> modules = moduleService.listModules("en");
        out.append("The 15 modules, in program order (number, stage, title, status):\n");
        modules.forEach(module -> out.append("- ").append(module.number()).append(". ").append(module.stage()).append(" – ")
                .append(clip(module.title())).append(" (").append(module.status()).append(")\n"));
        appendChainScores(out, user, false);
        out.append("\nPlan counts: ").append(dreamRepository.findByUser_IdAndArchivedFalse(user.getId()).size()).append(" dreams, ")
                .append(goalRepository.findByUser_IdAndArchivedFalse(user.getId()).size()).append(" goals, ")
                .append(stepRepository.findByUser_IdAndArchivedFalse(user.getId()).size()).append(" steps, ")
                .append(partnerRepository.findByUser_IdAndArchivedFalse(user.getId()).size()).append(" partners.\n");
        return new View("MODULES", request.area(), out.toString(), List.of(SOURCE_PAGE, SOURCE_MODULES, SOURCE_ASSESSMENT),
                List.of(), Map.of(), "You are the page assistant on the Modules list. Help the participant choose and "
                + "understand modules. Use the program's own ideas in your own words; never quote any book.");
    }

    private View moduleView(PageAssistantRequest request) {
        if (request.itemId() == null) {
            throw new com.visionmapping.exception.BusinessRuleException("Open a module to use the assistant.");
        }
        // 404 for a module number that does not exist.
        LearningModuleDetailResponse module = moduleService.getModule(request.itemId().intValue(), "en");
        StringBuilder out = header("Module " + module.number() + ": " + clip(module.title()), request.area());
        out.append("Module idea: ").append(clipLong(module.guidance())).append('\n');
        out.append("Exercise: ").append(clip(module.exerciseInstructions())).append('\n');
        out.append("Status: ").append(module.status()).append('\n');
        if (module.requirementMessage() != null) {
            out.append("This module's work is done on another page (").append(module.linkPath()).append("). Still missing: ")
                    .append(clip(module.requirementMessage())).append('\n');
        }
        Map<String, String> labels = new LinkedHashMap<>();
        module.fields().forEach(field -> labels.put(field.key(), field.label()));
        List<PageIssue> issues = new ArrayList<>();
        Map<String, FieldRule> writable = new LinkedHashMap<>();
        if (!labels.isEmpty()) {
            out.append("\nThe exercise form on screen (unsaved):\n");
            int rows = 0;
            for (Map.Entry<String, String> entry : new TreeMap<>(request.formValues()).entrySet()) {
                Matcher matcher = MODULE_FIELD.matcher(entry.getKey());
                if (matcher.matches() && labels.containsKey(matcher.group(2))) {
                    int row = Integer.parseInt(matcher.group(1));
                    rows = Math.max(rows, row + 1);
                    out.append("- Row ").append(row + 1).append(", ").append(labels.get(matcher.group(2))).append(" [")
                            .append(entry.getKey()).append("]: ").append(blankOr(entry.getValue())).append('\n');
                }
            }
            int shownRows = Math.max(1, Math.min(rows, MAX_MODULE_ROWS));
            for (int row = 0; row < shownRows; row++) {
                for (LearningModuleDetailResponse.Field field : module.fields()) {
                    String key = "row" + row + "." + field.key();
                    writable.put(key, "rating".equals(field.type())
                            ? new FieldRule(Set.of("1", "2", "3", "4", "5"), 1) : FieldRule.text(MAX_VALUE));
                    if (isBlank(request.formValues().get(key))) {
                        issues.add(new PageIssue(key, field.label() + (shownRows > 1 ? " (row " + (row + 1) + ")" : "") + " is empty.",
                                "Fill it in before you save."));
                    }
                }
            }
        }
        return new View("MODULE", request.area(), out.toString(), List.of(SOURCE_PAGE, AiContextBuilder.MODULE, SOURCE_FORM),
                issues, writable, "You are the page assistant on a module page. Explain the module in plain words and help "
                + "the participant do the exercise in their own words. A draft never saves or completes the module. Never "
                + "invent facts about the participant's life. Use the program's own ideas in your own words; never quote any book.");
    }

    private View assessmentView(AppUser user, PageAssistantRequest request) {
        StringBuilder out = header("Assessment", request.area());
        out.append("The six statements, each scored 1 (rarely true) to 5 (almost always true):\n");
        CHAIN_LABELS.forEach((key, label) -> out.append("- ").append(label).append(" [").append(key).append("]\n"));
        appendChainScores(out, user, true);
        out.append("\nModules (number, title, status):\n");
        moduleService.listModules("en").forEach(module -> out.append("- ").append(module.number()).append(". ")
                .append(clip(module.title())).append(" (").append(module.status()).append(")\n"));
        return new View("ASSESSMENT", request.area(), out.toString(), List.of(SOURCE_PAGE, SOURCE_ASSESSMENT, SOURCE_MODULES),
                List.of(), Map.of(), "You are the page assistant on the Assessment page. AH-FR-24: never suggest, change or "
                + "comment on what score the participant should give. If they ask what to score, explain the statement in "
                + "plain words and say the score is their own judgement. AH-FR-30: describe scores and actions only; never "
                + "label the person (for example never say they are a fearful person).");
    }

    private View obstaclesView(AppUser user, PageAssistantRequest request) {
        Obstacle saved = request.itemId() == null ? null : lookup.obstacle(request.itemId());
        Map<String, String> form = readable(request.formValues(), OBSTACLE_READ);
        StringBuilder out = header("Obstacles", request.area());
        Set<String> sources = new LinkedHashSet<>(List.of(SOURCE_PAGE));
        boolean hasForm = saved != null || !form.isEmpty();
        if (hasForm) {
            sources.add(SOURCE_OBSTACLE);
            out.append("The obstacle open on screen (the participant's unsaved changes win over the saved values):\n");
            appendValue(out, "Title", form, "title", saved == null ? null : saved.getTitle());
            appendValue(out, "Description", form, "description", saved == null ? null : saved.getDescription());
            appendValue(out, "Type", form, "obstacleType", saved == null ? null : name(saved.getObstacleType()));
            appendValue(out, "Severity", form, "severity", saved == null ? null : name(saved.getSeverity()));
            appendValue(out, "Status", form, "status", saved == null ? null : name(saved.getStatus()));
            appendValue(out, "Root cause", form, "rootCause", saved == null ? null : saved.getRootCause());
            appendValue(out, "Solution", form, "solution", saved == null ? null : saved.getSolution());
            appendValue(out, "Creative alternatives", form, "creativeAlternatives", saved == null ? null : saved.getCreativeAlternatives());
            appendValue(out, "Conflict: what happened", form, "conflictIncident", saved == null ? null : saved.getConflictIncident());
            appendValue(out, "Conflict: the other person's view", form, "conflictOtherPerspective",
                    saved == null ? null : saved.getConflictOtherPerspective());
            appendValue(out, "Criticism: the useful point", form, "criticismSubstance", saved == null ? null : saved.getCriticismSubstance());
            if ((saved != null && saved.getRequiredPartner() != null) || !isBlank(request.formValues().get("requiredPartnerId"))) {
                // Section B: never the partner's name or contact details.
                out.append("- A required partner is set (name not shared).\n");
            }
            if (appendLinked(out, linkedId(form, "relatedDreamId", saved == null || saved.getRelatedDream() == null ? null : saved.getRelatedDream().getId()),
                    linkedId(form, "relatedGoalId", saved == null || saved.getRelatedGoal() == null ? null : saved.getRelatedGoal().getId()),
                    linkedId(form, "relatedStepId", saved == null || saved.getRelatedStep() == null ? null : saved.getRelatedStep().getId()),
                    linkedId(form, "relatedTaskId", saved == null || saved.getRelatedTask() == null ? null : saved.getRelatedTask().getId()))) {
                sources.add(SOURCE_LINKED);
            }
        }
        List<Obstacle> all = obstacleRepository.findByUser_IdAndArchivedFalse(user.getId());
        String type = pick(form, "obstacleType", saved == null ? null : name(saved.getObstacleType()));
        List<Obstacle> solved = all.stream()
                .filter(other -> saved == null || !other.getId().equals(saved.getId()))
                .filter(other -> other.getStatus() == ObstacleStatus.RESOLVED && other.getObstacleType() != null
                        && other.getObstacleType().name().equals(type))
                .sorted(Comparator.comparing(Obstacle::getId).reversed())
                .limit(MAX_SOLVED)
                .toList();
        if (hasForm && !solved.isEmpty()) {
            // AH-FR-38: how the participant solved this kind of obstacle before.
            sources.add(SOURCE_SOLVED);
            out.append("\nResolved obstacles of the same type (title, root cause, solution):\n");
            solved.forEach(other -> out.append("- ").append(clip(other.getTitle())).append("; root cause: ")
                    .append(blankOr(clip(other.getRootCause()))).append("; solution: ").append(blankOr(clip(other.getSolution())))
                    .append('\n'));
        }
        List<Obstacle> others = all.stream()
                .filter(other -> saved == null || !other.getId().equals(saved.getId()))
                .sorted(Comparator.comparing(Obstacle::getId).reversed())
                .limit(MAX_OTHER_OBSTACLES)
                .toList();
        if (!others.isEmpty()) {
            sources.add(AiContextBuilder.OBSTACLES);
            out.append("\nThe participant's other obstacles (type, status, solution):\n");
            others.forEach(other -> out.append("- ").append(other.getObstacleType()).append(", ").append(other.getStatus())
                    .append(", solution: ").append(blankOr(clip(other.getSolution()))).append('\n'));
        }
        List<PageIssue> issues = new ArrayList<>();
        Map<String, FieldRule> writable = new LinkedHashMap<>();
        if (hasForm) {
            if (isBlank(pick(form, "description", saved == null ? null : saved.getDescription()))) {
                issues.add(new PageIssue("description", "There is no description yet.", "Say in a sentence or two what is in the way."));
            }
            if (isBlank(pick(form, "rootCause", saved == null ? null : saved.getRootCause()))) {
                issues.add(new PageIssue("rootCause", "No root cause yet.", "Ask yourself why this happens, then why again."));
            }
            if (isBlank(pick(form, "solution", saved == null ? null : saved.getSolution()))) {
                issues.add(new PageIssue("solution", "No solution yet.", "Write one thing you could try first, however small."));
            }
            OBSTACLE_TEXT_FIELDS.forEach(field -> writable.put(field, FieldRule.text(MAX_VALUE)));
            writable.put("obstacleType", FieldRule.oneOf(ObstacleType.values()));
            writable.put("severity", FieldRule.oneOf(Severity.values()));
            writable.keySet().retainAll(OBSTACLE_WRITE);
        }
        return new View("OBSTACLES", request.area(), out.toString(), List.copyOf(sources), issues, writable,
                "You are the page assistant on the Obstacles page. Help the participant understand an obstacle, find its "
                + "root cause and think of solutions that fit its type. You may suggest a kind of partner, but you never know "
                + "the participant's partners' names. Never mark an obstacle resolved; the participant decides.");
    }

    private View communicationView(AppUser user, PageAssistantRequest request) {
        CommunicationMessage saved = request.itemId() == null ? null : lookup.communicationMessage(request.itemId());
        Map<String, String> form = readable(request.formValues(), MESSAGE_READ);
        StringBuilder out = header("Communication", request.area());
        Set<String> sources = new LinkedHashSet<>(List.of(SOURCE_PAGE));
        boolean hasForm = saved != null || !form.isEmpty();
        Partner partner = null;
        if (hasForm) {
            sources.add(SOURCE_MESSAGE);
            Long partnerId = parseId(form.get("partnerId"));
            partner = partnerId != null ? lookup.partner(partnerId) : saved == null ? null : saved.getPartner();
            out.append("The message open on screen (the participant's unsaved changes win over the saved values):\n");
            if (partner != null) {
                // AH-FR-53: name and role only, never email, phone or notes.
                out.append("- Partner: ").append(clip(partner.getName()))
                        .append(isBlank(partner.getRole()) ? "" : ", " + clip(partner.getRole())).append('\n');
            }
            for (String field : MESSAGE_TEXT_FIELDS) {
                appendValue(out, field, form, field, saved == null ? null : messageField(saved, field));
            }
            appendValue(out, "status", form, "status", saved == null ? null : name(saved.getStatus()));
            appendValue(out, "followUpDate", form, "followUpDate",
                    saved == null || saved.getFollowUpDate() == null ? null : saved.getFollowUpDate().toString());
            if (appendLinked(out, linkedId(form, "relatedDreamId", saved == null || saved.getRelatedDream() == null ? null : saved.getRelatedDream().getId()),
                    linkedId(form, "relatedGoalId", saved == null || saved.getRelatedGoal() == null ? null : saved.getRelatedGoal().getId()), null,
                    linkedId(form, "relatedTaskId", saved == null || saved.getRelatedTask() == null ? null : saved.getRelatedTask().getId()))) {
                sources.add(SOURCE_LINKED);
            }
            if (partner != null) {
                Long currentId = saved == null ? null : saved.getId();
                List<CommunicationMessage> earlier = messageRepository
                        .findTop10ByUser_IdAndPartner_IdAndArchivedFalseOrderByIdDesc(user.getId(), partner.getId()).stream()
                        .filter(message -> !message.getId().equals(currentId))
                        .toList();
                if (!earlier.isEmpty()) {
                    // AH-FR-67: subjects, dates and statuses only, never bodies.
                    sources.add(SOURCE_EARLIER);
                    out.append("\nEarlier messages to the same partner (subject, status, follow-up date):\n");
                    earlier.forEach(message -> out.append("- ").append(blankOr(clip(message.getSubject()))).append(", ")
                            .append(message.getStatus()).append(", follow-up ")
                            .append(message.getFollowUpDate() == null ? "none" : message.getFollowUpDate()).append('\n'));
                }
            }
        }
        List<PageIssue> issues = new ArrayList<>();
        Map<String, FieldRule> writable = new LinkedHashMap<>();
        if (hasForm) {
            // AH-FR-46: the rule checks that work without AI.
            if (isBlank(pick(form, "request", saved == null ? null : saved.getRequest()))) {
                issues.add(new PageIssue("request", "There is no request yet.", "Say exactly what you are asking for, by when and for how long."));
            }
            if (isBlank(pick(form, "benefitToPartner", saved == null ? null : saved.getBenefitToPartner()))) {
                issues.add(new PageIssue("benefitToPartner", "The benefit to the partner is empty.", "Say what they gain by helping."));
            }
            if (isBlank(pick(form, "callToAction", saved == null ? null : saved.getCallToAction()))) {
                issues.add(new PageIssue("callToAction", "There is no call to action.", "End with one clear next step, such as a short call this week."));
            }
            String body = pick(form, "messageBody", saved == null ? null : saved.getMessageBody());
            int words = body == null || body.isBlank() ? 0 : body.trim().split("\\s+").length;
            if (words > MESSAGE_WORD_LIMIT) {
                issues.add(new PageIssue("messageBody", "The message is over 250 words.", "Try to keep it short enough to read in a minute."));
            }
            MESSAGE_TEXT_FIELDS.forEach(field -> writable.put(field, FieldRule.text("messageBody".equals(field) ? MAX_BODY : MAX_VALUE)));
            writable.put("status", FieldRule.oneOf(CommunicationStatus.values()));
            writable.keySet().retainAll(MESSAGE_WRITE);
            String fieldArea = request.area().startsWith("field:") ? request.area().substring("field:".length()) : null;
            if (fieldArea != null && writable.containsKey(fieldArea)) {
                // AH-FR-43: help with one field changes only that field.
                writable.keySet().retainAll(Set.of(fieldArea));
            }
        }
        return new View("COMMUNICATION", request.area(), out.toString(), List.copyOf(sources), issues, writable,
                "You are the page assistant on the Communication page. Help the participant write a respectful, concise "
                + "request for support: what the partner can help with, why the work matters, the specific request and the "
                + "value to the partner. Never sound demanding or pushy. Never invent facts, results or quotes. The "
                + "participant sends every message themselves; never change a status or follow-up date unless asked.");
    }

    private View workStyleView(AppUser user, PageAssistantRequest request) {
        StringBuilder out = header("Work Style", request.area());
        List<String> sources = new ArrayList<>(List.of(SOURCE_PAGE));
        if (user.getWorkStyleDominant() != null) {
            sources.add(SOURCE_WORK_STYLE);
            out.append("The participant's own result: dominant ").append(user.getWorkStyleDominant())
                    .append(user.getWorkStyleSecondary() == null ? "" : ", secondary " + user.getWorkStyleSecondary()).append('\n');
            out.append("Pole counts out of 8 per axis: fast ").append(user.getWorkStylePaceFastScore())
                    .append(", deliberate ").append(user.getWorkStylePaceDeliberateScore())
                    .append("; task ").append(user.getWorkStyleFocusTaskScore())
                    .append(", people ").append(user.getWorkStyleFocusPeopleScore()).append('\n');
        } else {
            out.append("The participant has not taken the work-style quiz yet.\n");
        }
        // Section B: counts only, never partner names.
        List<Partner> partners = partnerRepository.findByUser_IdAndArchivedFalse(user.getId());
        Map<String, Long> byStyle = partners.stream().collect(Collectors.groupingBy(
                partner -> partner.getWorkStyleType() == null ? "no style set" : partner.getWorkStyleType().name(),
                TreeMap::new, Collectors.counting()));
        Map<String, Long> bySupport = partners.stream().collect(Collectors.groupingBy(
                partner -> partner.getSupportType() == null ? "not set" : partner.getSupportType().name(), TreeMap::new, Collectors.counting()));
        out.append("Partners by style: ").append(byStyle.isEmpty() ? "none" : byStyle).append('\n');
        out.append("Partners by support type: ").append(bySupport.isEmpty() ? "none" : bySupport).append('\n');
        out.append("Steps with no target date: ").append(stepRepository.countByUser_IdAndArchivedFalseAndTargetDateIsNull(user.getId())).append('\n');
        // V15 Phase 5 (section B): one partner, when the participant asks about them: the style estimate, role and support type only.
        if (request.area().startsWith("partner:")) {
            Long partnerId = parseId(request.area().substring("partner:".length()));
            if (partnerId != null) {
                Partner partner = lookup.partner(partnerId);
                out.append("The partner being asked about (name not shared): the participant estimates their style as ")
                        .append(partner.getWorkStyleType() == null ? "not set yet" : partner.getWorkStyleType().name())
                        .append(isBlank(partner.getRole()) ? "" : ", role " + clip(partner.getRole()))
                        .append(partner.getSupportType() == null ? "" : ", support type " + partner.getSupportType()).append(".\n");
            }
        }
        return new View("WORK_STYLE", request.area(), out.toString(), sources, List.of(), Map.of(),
                "You are the page assistant on the Work Style page. AH-FR-69: while the participant is answering, never "
                + "choose an answer, say which answer is better, or predict the result; explain both choices neutrally and "
                + "say to pick the one closer to how they usually work, not how they wish to work. AH-FR-77: styles are "
                + "self-reported tendencies, not a measurement or a fixed trait; never call one style better, and never rank, "
                + "judge or exclude a person by style. AH-FR-79: a partner's style is only the participant's estimate.");
    }

    // ---------- helpers ----------

    private StringBuilder header(String pageName, String area) {
        return new StringBuilder("Today is ").append(LocalDate.now(clock)).append(". The participant is on the ")
                .append(pageName).append(" page and asked for help with: ").append(area).append(".\n");
    }

    private void appendChainScores(StringBuilder out, AppUser user, boolean everyTake) {
        List<ChainAssessment> takes = assessmentRepository.findByUser_IdOrderByTakenOnAsc(user.getId());
        if (takes.isEmpty()) {
            out.append("\nNo assessment taken yet.\n");
            return;
        }
        List<ChainAssessment> shown = everyTake || takes.size() <= 2 ? takes : List.of(takes.get(0), takes.get(takes.size() - 1));
        out.append("\nAssessment takes (").append(everyTake ? "every take" : "first and latest").append("):\n");
        for (ChainAssessment take : shown) {
            out.append("- ").append(take.getTakenOn()).append(": settling ").append(take.getSettlingScore())
                    .append(", fear ").append(take.getFearScore()).append(", criticism ").append(take.getCriticismScore())
                    .append(", vision ").append(take.getVisionScore()).append(", knowledge ").append(take.getKnowledgeScore())
                    .append(", resources ").append(take.getResourcesScore()).append('\n');
        }
    }

    /** Adds the titles and dates of the linked dream, goal, step and task. Each is looked up as the participant's own. */
    private boolean appendLinked(StringBuilder out, @Nullable Long dreamId, @Nullable Long goalId, @Nullable Long stepId,
                                 @Nullable Long taskId) {
        boolean any = false;
        if (dreamId != null) {
            Dream dream = lookup.dream(dreamId);
            out.append("- Linked dream: ").append(clip(dream.getTitle())).append(dateText(dream.getTargetDate())).append('\n');
            any = true;
        }
        if (goalId != null) {
            Goal goal = lookup.goal(goalId);
            out.append("- Linked goal: ").append(clip(goal.getTitle())).append(dateText(goal.getTargetDate()))
                    .append(", progress ").append(percentText(goal.getProgressPercent())).append("%\n");
            any = true;
        }
        if (stepId != null) {
            VisionStep step = lookup.step(stepId);
            out.append("- Linked step: ").append(clip(step.getTitle())).append(dateText(step.getTargetDate())).append('\n');
            any = true;
        }
        if (taskId != null) {
            TaskItem task = lookup.task(taskId);
            out.append("- Linked task: ").append(clip(task.getTitle())).append(dateText(task.getDueDate())).append(", ")
                    .append(task.getStatus()).append(", progress ").append(percentText(task.getProgressPercent())).append("%\n");
            any = true;
        }
        return any;
    }

    /** A progress value as plain text, such as 40 rather than 40.00. */
    private static String percentText(@Nullable Number value) {
        if (value == null) {
            return "0";
        }
        return value instanceof BigDecimal decimal ? decimal.stripTrailingZeros().toPlainString() : value.toString();
    }

    private static String dateText(@Nullable LocalDate date) {
        return date == null ? "" : ", date " + date;
    }

    /** The unsaved form's id wins; an empty string clears the saved link. */
    @Nullable
    private static Long linkedId(Map<String, String> form, String key, @Nullable Long savedId) {
        return form.containsKey(key) ? parseId(form.get(key)) : savedId;
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

    private static Map<String, String> readable(Map<String, String> form, Set<String> allowed) {
        Map<String, String> out = new LinkedHashMap<>();
        form.forEach((key, value) -> {
            if (allowed.contains(key) && value != null) {
                out.put(key, value);
            }
        });
        return out;
    }

    private static void appendValue(StringBuilder out, String label, Map<String, String> form, String key, @Nullable String saved) {
        String value = pick(form, key, saved);
        out.append("- ").append(label).append(" [").append(key).append("]: ").append(blankOr(clipLong(value))).append('\n');
    }

    @Nullable
    private static String pick(Map<String, String> form, String key, @Nullable String saved) {
        return form.containsKey(key) ? form.get(key) : saved;
    }

    /** The saved value of one message text field; shared with the Communication helpers. */
    @Nullable
    static String messageField(CommunicationMessage message, String field) {
        return switch (field) {
            case "audience" -> message.getAudience();
            case "purpose" -> message.getPurpose();
            case "subject" -> message.getSubject();
            case "hook" -> message.getHook();
            case "wordPicture" -> message.getWordPicture();
            case "problem" -> message.getProblem();
            case "request" -> message.getRequest();
            case "objectionsAndAnswers" -> message.getObjectionsAndAnswers();
            case "socialProof" -> message.getSocialProof();
            case "valueComparison" -> message.getValueComparison();
            case "benefitToPartner" -> message.getBenefitToPartner();
            case "expectedOutcome" -> message.getExpectedOutcome();
            case "callToAction" -> message.getCallToAction();
            case "messageBody" -> message.getMessageBody();
            default -> null;
        };
    }

    @Nullable
    private static String name(@Nullable Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    private static String blankOr(@Nullable String value) {
        return isBlank(value) ? "(empty)" : value;
    }

    private static String clipTo(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

    // ---------- admin metrics and baselines (AH-FR-56, Phase 1 baselines) ----------

    /** AH-FR-56: per V15 feature over the last days, and the AHBO-01, 03 and 04 baselines. Admins only. */
    @Transactional(readOnly = true)
    public PageMetrics metrics(int days) {
        Roles.requireAdmin(lookup.currentUser(), "Only an administrator can see AI page assistant metrics.");
        int period = Math.max(1, Math.min(days, METRIC_MAX_DAYS));
        Instant from = Instant.now(clock).minus(Duration.ofDays(period));
        List<PageFeatureMetrics> features = new ArrayList<>();
        for (Object[] row : usageLogRepository.planMetricsSince(from, FEATURES)) {
            long requestCount = number(row[1]);
            long suggested = number(row[2]);
            long applied = number(row[3]);
            long edited = number(row[4]);
            long ratings = number(row[5]);
            long thumbsUp = number(row[6]);
            BigDecimal cost = row[7] instanceof BigDecimal decimal ? decimal : BigDecimal.valueOf(((Number) row[7]).doubleValue());
            features.add(new PageFeatureMetrics((String) row[0], requestCount, suggested, applied, edited,
                    Math.max(0, suggested - applied - edited), rate(applied + edited, suggested), ratings, rate(thumbsUp, ratings),
                    cost, requestCount == 0 ? BigDecimal.ZERO : cost.divide(BigDecimal.valueOf(requestCount), 6, RoundingMode.HALF_UP)));
        }
        features.sort(Comparator.comparing(PageFeatureMetrics::feature));
        return new PageMetrics(period, features, baselines(from));
    }

    private PageBaselines baselines(Instant from) {
        // AHBO-01: of the module completions in the period, how many were followed by another within 7 days.
        List<ModuleCompletion> completions = completionRepository.findByCompletedAtGreaterThanEqual(from).stream()
                .filter(completion -> completion.getCompletedAt() != null)
                .toList();
        Map<Long, List<Instant>> byUser = completions.stream().collect(Collectors.groupingBy(
                completion -> completion.getUser().getId(), Collectors.mapping(ModuleCompletion::getCompletedAt, Collectors.toList())));
        long followed = 0;
        for (List<Instant> times : byUser.values()) {
            List<Instant> sorted = times.stream().sorted().toList();
            for (int index = 0; index < sorted.size() - 1; index++) {
                if (Duration.between(sorted.get(index), sorted.get(index + 1)).compareTo(Duration.ofDays(7)) <= 0) {
                    followed++;
                }
            }
        }
        // AHBO-03: open obstacles with a root cause, a solution or a linked task.
        long open = obstacleRepository.countByArchivedFalseAndStatus(ObstacleStatus.OPEN);
        long withPlan = obstacleRepository.countWithPlan(ObstacleStatus.OPEN);
        // AHBO-04: drafts, sent messages and follow-ups past their date.
        long drafts = messageRepository.countByArchivedFalseAndStatus(CommunicationStatus.DRAFT);
        long sent = messageRepository.countByArchivedFalseAndStatus(CommunicationStatus.SENT);
        long overdue = messageRepository.countByArchivedFalseAndStatusInAndFollowUpDateBefore(
                List.of(CommunicationStatus.SENT, CommunicationStatus.FOLLOWED_UP), LocalDate.now(clock));
        return new PageBaselines(completions.size(), followed, rate(followed, completions.size()), open, withPlan,
                rate(withPlan, open), drafts, sent, overdue);
    }

    @Nullable
    private static Double rate(long part, long whole) {
        return whole == 0 ? null : Math.round(part * 1000.0 / whole) / 1000.0;
    }

    private static long number(@Nullable Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }
}
