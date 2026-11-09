package com.visionmapping.service.ai;

import static com.visionmapping.service.ai.AiContextBuilder.clip;

import com.visionmapping.dto.request.AiModuleHelpRequest;
import com.visionmapping.dto.response.AiModuleResponses.AssessmentExplanation;
import com.visionmapping.dto.response.AiModuleResponses.ModuleHelp;
import com.visionmapping.dto.response.LearningModuleDetailResponse;
import com.visionmapping.dto.response.LearningModuleDetailResponse.Field;
import com.visionmapping.dto.response.LearningModuleSummaryResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.ChainAssessment;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.repository.ChainAssessmentRepository;
import com.visionmapping.service.LearningModuleService;
import com.visionmapping.service.support.EntityLookup;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * V9 Phase 7: AI help inside the fifteen module exercises (AIB-01 to AIB-15)
 * and an explanation of the six-chain assessment (v0.1 FR-02 change). The
 * exercise stays the participant's own: drafts fill the form on screen, and
 * only saving it counts or unlocks the next module (FR-07 change, AC-09).
 */
@Service
@RequiredArgsConstructor
public class AiModuleService {

    static final int MAX_ROWS = 3;
    static final int MAX_VALUE = 2000;
    private static final String RATING = "rating";

    /** What Claude should focus on in each module, by module number (original wording, after the BRD's AIB table). */
    private static final Map<Integer, String> FOCUS = Map.ofEntries(
            Map.entry(1, "Spot limiting beliefs in the participant's own words and offer a positive replacement for each."),
            Map.entry(2, "Weigh the worst case against how likely it is, and suggest the smallest safe first test."),
            Map.entry(3, "Separate the useful point in a criticism from the emotion, and suggest a calm reply and one action."),
            Map.entry(4, "Ask guided questions about each life area, and help rank dreams with reasons."),
            Map.entry(5, "Find the knowledge that is missing and good ways to learn it; shape SMART goals and dated steps."),
            Map.entry(6, "For each missing resource, suggest the kinds of people or organisations that could supply it."),
            Map.entry(7, "Find work to delegate, share with a partner or automate, so more gets done in less time."),
            Map.entry(8, "Suggest a bolder version of a goal and what it would need."),
            Map.entry(9, "Describe the partner or mentor profiles that would fill each gap."),
            Map.entry(10, "Notice trends in attitude check-ins, offer a kinder reframe and gratitude prompts."),
            Map.entry(11, "Help write a pitch: a problem the listener cares about, the benefit to them, and one specific ask."),
            Map.entry(12, "Recommend the three habits that would most move the plan, each with a trigger and a tiny first version."),
            Map.entry(13, "Rank today's work by its impact on the plan and the time available."),
            Map.entry(14, "Ask why questions to find the personal reason, and draft a passion statement for a dream."),
            Map.entry(15, "Check readiness (plan complete, first step clear, start date set) and draft a commitment statement."));

    private final EntityLookup lookup;
    private final AiAccessService accessService;
    private final AiStructuredRequests requests;
    private final AiContextBuilder contextBuilder;
    private final LearningModuleService learningModuleService;
    private final ChainAssessmentRepository chainAssessmentRepository;
    private final TransactionTemplate transactions;
    private final com.visionmapping.service.ModuleJourneyService journey;
    private final PageAssistantService pageAssistant;
    private final AiPlanFeedbackService feedback;
    private final com.visionmapping.repository.ModuleCompletionRepository completionRepository;

    /**
     * V15 (AH-FR-13): one module to do next, chosen by Claude from the
     * unlocked, not completed modules, with a reason from the participant's own
     * data. A pick outside that list is never shown: the rule answer is used
     * instead (AH-FR-14, AHC-05).
     */
    public com.visionmapping.dto.response.ModuleJourneyResponses.ModuleNext next(String language) {
        AppUser user = lookup.currentUser();
        List<LearningModuleSummaryResponse> open = journey.available(language);
        com.visionmapping.dto.response.ModuleJourneyResponses.ModuleNext rule = journey.next(language);
        if (open.isEmpty()) {
            return rule;
        }
        String apiKey = accessService.requireAvailable(user, "MODULES");
        String records = pageAssistant.viewInTransaction(user,
                new com.visionmapping.dto.request.PageAssistantRequest("MODULES", "list", null, null)).records();
        StringBuilder data = new StringBuilder(records).append("\nModules the participant may open now (number. title):\n");
        open.forEach(module -> data.append("- ").append(module.number()).append(". ").append(clip(module.title())).append('\n'));
        data.append("The program's rule would suggest module ").append(rule.moduleNumber()).append(": ").append(rule.reason()).append('\n');
        String task = "Recommend exactly one module from the open list that would help this participant most now, and say why in two "
                + "sentences that name their own data, for example a low assessment score or a plan with no steps yet. Never invent data.";
        AiStructuredRequests.Tracked<AiDrafts.ModuleNextDraft> answer = requests.askTracked(user, apiKey, "MODULE_NEXT", language,
                data.toString(), task, AiDrafts.ModuleNextDraft.class);
        AiDrafts.ModuleNextDraft draft = answer.value();
        Optional<LearningModuleSummaryResponse> pick = open.stream()
                .filter(module -> draft.moduleNumber() != null && module.number().equals(draft.moduleNumber())).findFirst();
        Long requestId = feedback.suggested(answer.usage(), pick.isPresent() ? 1 : 0);
        if (pick.isEmpty() || draft.reason() == null || draft.reason().isBlank()) {
            return new com.visionmapping.dto.response.ModuleJourneyResponses.ModuleNext(rule.moduleNumber(), rule.title(), rule.reason(),
                    rule.rule(), "RULE", requestId);
        }
        return new com.visionmapping.dto.response.ModuleJourneyResponses.ModuleNext(pick.get().number(), pick.get().title(),
                limit(draft.reason(), 400), "AI", "AI", requestId);
    }

    public ModuleHelp help(Integer number, AiModuleHelpRequest request) {
        AppUser user = lookup.currentUser();
        String language = request.language() == null ? "en" : request.language();
        String mode = request.mode();
        // Throws for a locked or unknown module, the same as opening it.
        LearningModuleDetailResponse module = learningModuleService.getModule(number, language);
        List<Field> fields = module.fields() == null ? List.of() : module.fields();
        List<Map<String, String>> typed = cleanRows(request.rows(), fields);
        if ("DRAFT".equals(mode) && fields.isEmpty()) {
            throw new BusinessRuleException("This module is done elsewhere in the app, so there is no form to draft. Ask for help to start instead.");
        }
        if ("FEEDBACK".equals(mode) && typed.isEmpty()) {
            throw new BusinessRuleException("Write something in the exercise first, then ask for feedback.");
        }
        if (("REFINE".equals(mode) || "CHECK".equals(mode)) && fields.isEmpty()) {
            throw new BusinessRuleException("This module is done elsewhere in the app, so there is no answer to refine or check.");
        }
        if ("REFINE".equals(mode) && typed.isEmpty()) {
            throw new BusinessRuleException("Put a draft in the form first, then refine it.");
        }
        if ("REFINE".equals(mode) && request.refine() == null && (request.note() == null || request.note().isBlank())) {
            throw new BusinessRuleException("Choose how to refine the draft, or write a short note.");
        }
        String apiKey = accessService.requireAvailable(user, "MODULE");
        String records = transactions.execute(status -> contextBuilder.build(user,
                new AiContextBuilder.Focus("/modules/" + number, null, number), language).records());
        // V15 (AH-FR-76): the work style only when the participant allows it.
        StringBuilder data = new StringBuilder(records).append(WorkStyleContext.line(user)).append("\n\nThis module's focus: ")
                .append(FOCUS.getOrDefault(number, "Help the participant apply this module to their own dreams."));
        if (!fields.isEmpty()) {
            data.append("\nExercise fields (key: label):\n");
            fields.stream().filter(field -> !RATING.equals(field.type()))
                    .forEach(field -> data.append("- ").append(field.key()).append(": ").append(clip(field.label())).append('\n'));
        }
        if (!typed.isEmpty()) {
            data.append("\nWhat the participant has written so far, not yet saved:\n");
            for (int index = 0; index < typed.size(); index++) {
                data.append("- Row ").append(index + 1).append(": ").append(typed.get(index).entrySet().stream()
                        .map(entry -> entry.getKey() + " = " + clip(entry.getValue())).collect(Collectors.joining("; "))).append('\n');
            }
        }
        // AH-FR-16: answers given in place to the Help me start questions; never saved.
        List<AiModuleHelpRequest.QuestionAnswer> answers = Optional.ofNullable(request.answers()).orElse(List.of()).stream()
                .filter(item -> item != null && item.answer() != null && !item.answer().isBlank())
                .toList();
        if (!answers.isEmpty() && ("DRAFT".equals(mode) || "REFINE".equals(mode))) {
            data.append("\nThe participant's own answers to the starting questions:\n");
            answers.forEach(item -> data.append("- Q: ").append(clip(item.question())).append("\n  A: ")
                    .append(AiContextBuilder.clipLong(item.answer())).append('\n'));
        }
        List<Integer> keep = Optional.ofNullable(request.keepRows()).orElse(List.of());
        String task = switch (mode) {
            case "START" -> "Help the participant start this exercise. Give up to three short questions that would help them "
                    + "think it through, most useful first, and one short example answer about a made-up person. Leave rows, "
                    + "feedback and improvements empty.";
            case "DRAFT" -> "Draft answers for this exercise from the participant's own records, in their voice, for them to edit. "
                    + "Use only the field keys listed, at most " + MAX_ROWS + " rows. Never invent facts about their life; when the "
                    + "records do not say, keep the answer general and short. Build on their own answers to the starting questions when "
                    + "given. Leave questions, example, feedback and improvements empty.";
            case "REFINE" -> "Refine the draft in the form: " + refineInstruction(request.refine())
                    + (request.note() == null || request.note().isBlank() ? "" : " The participant asks: \""
                    + request.note().trim().replace('"', '\'') + "\".")
                    + (keep.isEmpty() ? "" : " Leave row(s) " + keep.stream().map(row -> String.valueOf(row + 1))
                    .collect(Collectors.joining(", ")) + " exactly as they are; the participant wrote them.")
                    + " Use only the field keys listed, at most " + MAX_ROWS + " rows, in their voice. Build on their own answers. "
                    + "Never invent facts about their life. Leave questions, example, feedback and improvements empty.";
            case "FEEDBACK" -> "Give honest, kind feedback on what the participant has written, against this module's focus, and up to "
                    + "three specific improvements. Leave questions, example and rows empty.";
            default -> "";
        };
        if ("EXPLAIN".equals(mode)) {
            return explain(user, apiKey, language, data.toString());
        }
        if ("CHECK".equals(mode)) {
            return check(user, apiKey, language, data.toString(), fields, request.rows());
        }
        AiDrafts.ModuleHelpDraft draft = requests.ask(user, apiKey, "MODULE_HELP", language, data.toString(), task,
                AiDrafts.ModuleHelpDraft.class);
        if ("REFINE".equals(mode)) {
            return new ModuleHelp(mode, List.of(), null, keepRows(draftRows(draft.rows(), fields), request.rows(), keep, fields), null,
                    List.of());
        }
        return switch (mode) {
            case "START" -> new ModuleHelp(mode, texts(draft.questions(), 3, 300), limit(draft.example(), 1000), List.of(), null, List.of());
            case "DRAFT" -> new ModuleHelp(mode, List.of(), null, draftRows(draft.rows(), fields), null, List.of());
            default -> new ModuleHelp(mode, List.of(), null, List.of(), limit(draft.feedback(), 1500), texts(draft.improvements(), 3, 300));
        };
    }

    /** v0.1 FR-02 change: the latest six-chain result in plain words, and the one module to focus on next. */
    public AssessmentExplanation explainAssessment(String language) {
        AppUser user = lookup.currentUser();
        List<ChainAssessment> taken = chainAssessmentRepository.findByUser_IdOrderByTakenOnAsc(user.getId());
        if (taken.isEmpty()) {
            throw new BusinessRuleException("Take the assessment first, then ask for an explanation.");
        }
        String apiKey = accessService.requireAvailable(user, "ASSESSMENT");
        ChainAssessment latest = taken.get(taken.size() - 1);
        List<LearningModuleSummaryResponse> modules = learningModuleService.listModules(language);
        StringBuilder data = new StringBuilder("Six-chain self-assessment of " + latest.getTakenOn()
                + ". Each statement scored 1 (rarely true) to 5 (almost always true); a low score is a chain holding them back.\n");
        data.append("- Settling for average (I expect more of myself than what is comfortable): ").append(latest.getSettlingScore()).append('\n');
        data.append("- Fear of failure (I try things even when they might not work): ").append(latest.getFearScore()).append('\n');
        data.append("- Avoiding criticism (what others may say does not stop me): ").append(latest.getCriticismScore()).append('\n');
        data.append("- Unclear vision (I can describe exactly what I want and when): ").append(latest.getVisionScore()).append('\n');
        data.append("- Not knowing how (when I do not know how, I find out): ").append(latest.getKnowledgeScore()).append('\n');
        data.append("- Lack of resources (when I lack something, I look for who could supply it): ").append(latest.getResourcesScore()).append('\n');
        if (taken.size() > 1) {
            // V15 (AH-FR-25): every take, so the trend is not only the first against the latest.
            data.append("Every take, oldest first, in the same order (settling, fear, criticism, vision, knowing how, resources):\n");
            taken.forEach(take -> data.append("- ").append(take.getTakenOn()).append(": ").append(take.getSettlingScore()).append(", ")
                    .append(take.getFearScore()).append(", ").append(take.getCriticismScore()).append(", ").append(take.getVisionScore())
                    .append(", ").append(take.getKnowledgeScore()).append(", ").append(take.getResourcesScore()).append('\n'));
        }
        List<com.visionmapping.service.ModuleJourneyService.Chain> low = com.visionmapping.service.ModuleJourneyService.CHAINS.stream()
                .filter(chain -> chain.score().applyAsInt(latest) <= 2).toList();
        if (!low.isEmpty()) {
            data.append("Barriers scored 1 or 2 (key: label): ").append(low.stream().map(chain -> chain.key() + ": " + chain.label())
                    .collect(Collectors.joining("; "))).append('\n');
        }
        data.append("\nThe program's modules:\n");
        modules.forEach(module -> data.append("- ").append(module.number()).append(". ").append(clip(module.title()))
                .append(" (").append(module.status()).append(")\n"));
        String task = "Explain this result in plain, encouraging words: what is strong, what is holding them back, and any change "
                + "since their first assessment. Then name the one module that would help most now, preferring one that is not "
                + "completed, and say why. Give one sentence for each barrier scored 1 or 2, by its key, and one or two sentences on "
                + "the trend across every take. " + NO_LABELS;
        AiDrafts.AssessmentExplanationDraft draft = requests.ask(user, apiKey, "ASSESSMENT_EXPLAIN", language, data.toString(), task,
                AiDrafts.AssessmentExplanationDraft.class);
        Optional<LearningModuleSummaryResponse> focus = modules.stream()
                .filter(module -> module.number() == draft.focusModule()).findFirst();
        List<com.visionmapping.dto.response.AiModuleResponses.ChainNote> notes = new ArrayList<>();
        for (AiDrafts.ChainNoteDraft note : Optional.ofNullable(draft.chainNotes()).orElse(List.of())) {
            low.stream().filter(chain -> note != null && chain.key().equals(note.key()) && note.note() != null && !note.note().isBlank())
                    .filter(chain -> notes.stream().noneMatch(existing -> existing.key().equals(chain.key())))
                    .findFirst()
                    .ifPresent(chain -> notes.add(new com.visionmapping.dto.response.AiModuleResponses.ChainNote(chain.key(), chain.label(),
                            chain.score().applyAsInt(latest), limit(note.note(), 300))));
        }
        return new AssessmentExplanation(limit(draft.explanation(), 2000), focus.map(LearningModuleSummaryResponse::number).orElse(null),
                focus.map(LearningModuleSummaryResponse::title).orElse(null), limit(draft.reason(), 300), notes,
                taken.size() > 1 ? limit(draft.trend(), 600) : null);
    }

    /** AH-FR-30: assessment help describes scores and actions, never the person. */
    private static final String NO_LABELS = "Describe scores and actions only; never label the person (never say, for example, "
            + "that they are a fearful person).";

    /** AH-FR-26: the app action each barrier's practice is tied to. The program decides it, not Claude. */
    private static final Map<String, String> PRACTICE_ACTIONS = Map.of(
            "settlingScore", "on the Dreams page, write a bolder version of one dream",
            "fearScore", "in the Journal, log one fear and plan the smallest safe test",
            "criticismScore", "on the Obstacles page, take one criticism and write down the useful point in it",
            "visionScore", "on the Dreams page, rewrite your top dream's success definition so you would know when it is reached",
            "knowledgeScore", "on the Goals page, add one step to learn what you do not know yet",
            "resourcesScore", "on the Partners page, add one partner who could supply something your plan is missing");

    /**
     * V15 (AH-FR-26): one small practice for the next seven days for each
     * barrier scored 3 or less in the latest take. Each is tied to the
     * program's app action for that barrier; Claude only words it.
     */
    public com.visionmapping.dto.response.AiModuleResponses.AssessmentPractice practice(String language) {
        AppUser user = lookup.currentUser();
        List<ChainAssessment> taken = chainAssessmentRepository.findByUser_IdOrderByTakenOnAsc(user.getId());
        if (taken.isEmpty()) {
            throw new BusinessRuleException("Take the assessment first, then ask for a practice.");
        }
        ChainAssessment latest = taken.get(taken.size() - 1);
        List<com.visionmapping.service.ModuleJourneyService.Chain> weak = com.visionmapping.service.ModuleJourneyService.CHAINS.stream()
                .filter(chain -> chain.score().applyAsInt(latest) <= 3).toList();
        if (weak.isEmpty()) {
            return new com.visionmapping.dto.response.AiModuleResponses.AssessmentPractice(null, List.of());
        }
        String apiKey = accessService.requireAvailable(user, "ASSESSMENT");
        StringBuilder data = new StringBuilder("The participant's latest six-chain self-assessment, scored 1 (rarely true) to 5 "
                + "(almost always true). Barriers scored 3 or less, each with the app action its practice must use:\n");
        weak.forEach(chain -> data.append("- ").append(chain.key()).append(" (").append(chain.label()).append(", ")
                .append(chain.score().applyAsInt(latest)).append(" of 5): ").append(PRACTICE_ACTIONS.get(chain.key())).append('\n'));
        data.append(WorkStyleContext.line(user));
        String task = "For each barrier listed, give one small practice for the next seven days that uses its app action, in plain, "
                + "encouraging words, and a short habit name for it. " + NO_LABELS;
        AiStructuredRequests.Tracked<AiDrafts.PracticeDrafts> answer = requests.askTracked(user, apiKey, "ASSESSMENT_PRACTICE", language,
                data.toString(), task, AiDrafts.PracticeDrafts.class);
        List<com.visionmapping.dto.response.AiModuleResponses.ChainPractice> practices = new ArrayList<>();
        for (com.visionmapping.service.ModuleJourneyService.Chain chain : weak) {
            Optional.ofNullable(answer.value().practices()).orElse(List.of()).stream()
                    .filter(item -> item != null && chain.key().equals(item.chainKey()) && item.practice() != null && !item.practice().isBlank())
                    .findFirst()
                    .ifPresent(item -> practices.add(new com.visionmapping.dto.response.AiModuleResponses.ChainPractice(chain.key(),
                            chain.label(), chain.score().applyAsInt(latest), limit(item.practice(), 400),
                            Optional.ofNullable(limit(item.habit(), 60)).orElse(""))));
        }
        return new com.visionmapping.dto.response.AiModuleResponses.AssessmentPractice(feedback.suggested(answer.usage(), practices.size()),
                practices);
    }

    /**
     * V15 (AH-FR-29): after a retake, a short, encouraging comparison with the
     * previous take that links each change to modules completed in between.
     */
    public com.visionmapping.dto.response.AiModuleResponses.WhatChanged whatChanged(String language) {
        AppUser user = lookup.currentUser();
        List<ChainAssessment> taken = chainAssessmentRepository.findByUser_IdOrderByTakenOnAsc(user.getId());
        if (taken.size() < 2) {
            throw new BusinessRuleException("Take the assessment again first, then compare.");
        }
        ChainAssessment previous = taken.get(taken.size() - 2);
        ChainAssessment latest = taken.get(taken.size() - 1);
        Map<Integer, String> titles = learningModuleService.listModules(language).stream()
                .collect(Collectors.toMap(LearningModuleSummaryResponse::number, LearningModuleSummaryResponse::title));
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        List<String> between = completionRepository.findByUser_Id(user.getId()).stream()
                .filter(completion -> completion.getCompletedAt() != null)
                .filter(completion -> {
                    java.time.LocalDate day = completion.getCompletedAt().atZone(zone).toLocalDate();
                    return day.isAfter(previous.getTakenOn()) && !day.isAfter(latest.getTakenOn());
                })
                .sorted(java.util.Comparator.comparing(com.visionmapping.entity.ModuleCompletion::getCompletedAt))
                .map(completion -> titles.getOrDefault(completion.getModuleNumber(), "Module " + completion.getModuleNumber()))
                .toList();
        String apiKey = accessService.requireAvailable(user, "ASSESSMENT");
        StringBuilder data = new StringBuilder("Two takes of the six-chain self-assessment, 1 (rarely true) to 5 (almost always true):\n");
        for (com.visionmapping.service.ModuleJourneyService.Chain chain : com.visionmapping.service.ModuleJourneyService.CHAINS) {
            data.append("- ").append(chain.label()).append(": ").append(chain.score().applyAsInt(previous)).append(" on ")
                    .append(previous.getTakenOn()).append(", ").append(chain.score().applyAsInt(latest)).append(" on ")
                    .append(latest.getTakenOn()).append('\n');
        }
        data.append("Modules completed in between: ").append(between.isEmpty() ? "none" : String.join("; ", between)).append('\n');
        String task = "Compare the two takes in three or four short, encouraging sentences. Link a change to a module completed in "
                + "between only when it fits that module. Be honest about scores that went down. " + NO_LABELS;
        AiStructuredRequests.Tracked<AiDrafts.WhatChangedDraft> answer = requests.askTracked(user, apiKey, "ASSESSMENT_EXPLAIN", language,
                data.toString(), task, AiDrafts.WhatChangedDraft.class);
        return new com.visionmapping.dto.response.AiModuleResponses.WhatChanged(feedback.suggested(answer.usage(), 0),
                Optional.ofNullable(limit(answer.value().summary(), 1200)).orElse(""), between);
    }

    private static String refineInstruction(@Nullable String refine) {
        if (refine == null) {
            return "follow the participant's note.";
        }
        return switch (refine) {
            case "SHORTER" -> "make it shorter, keeping the key point of each answer.";
            case "SPECIFIC" -> "make it more specific: names of real steps, dates and numbers the participant gave, never invented ones.";
            case "MY_WORDS" -> "make it sound more like the participant, using the words from their own answers and records.";
            default -> "write another version that takes a different angle on the same answers.";
        };
    }

    /** AH-FR-18: the module in plain words, why it matters, and one example about a made-up person. */
    private ModuleHelp explain(AppUser user, String apiKey, String language, String data) {
        String task = "Explain this module's idea in plain words for someone new to it, why it matters in the program's method, and "
                + "give one short example about a made-up person, never the participant. Use the program's own ideas in your own "
                + "words; never quote any book.";
        AiDrafts.ModuleExplainDraft draft = requests.ask(user, apiKey, "MODULE_HELP", language, data, task, AiDrafts.ModuleExplainDraft.class);
        return new ModuleHelp("EXPLAIN", List.of(), limit(draft.example(), 1000), List.of(), null, List.of(),
                limit(draft.explanation(), 1500), limit(draft.whyItMatters(), 800), List.of());
    }

    /** AH-FR-20: fields left empty or too short, each with a tip; only the module's own text fields and rows on screen. */
    private ModuleHelp check(AppUser user, String apiKey, String language, String data, List<Field> fields,
                             @Nullable List<Map<String, String>> rows) {
        int rowCount = Math.max(1, Math.min(MAX_ROWS, rows == null ? 0 : rows.size()));
        String task = "Before the participant saves, list each field that is empty or too short to be useful, with one line on how "
                + "to make that answer more useful. Rows count from 1; there are " + rowCount + ". Do not rewrite their answers.";
        AiDrafts.ModuleCheckDraft draft = requests.ask(user, apiKey, "MODULE_HELP", language, data, task, AiDrafts.ModuleCheckDraft.class);
        List<com.visionmapping.dto.response.AiModuleResponses.ModuleCheck> checks = new ArrayList<>();
        for (AiDrafts.ModuleCheckItemDraft item : Optional.ofNullable(draft.items()).orElse(List.of())) {
            boolean knownField = item != null && fields.stream().anyMatch(field -> field.key().equals(item.field()) && !RATING.equals(field.type()));
            if (!knownField || item.row() == null || item.row() < 1 || item.row() > rowCount || item.tip() == null || item.tip().isBlank()
                    || checks.size() >= MAX_ROWS * fields.size()) {
                continue;
            }
            checks.add(new com.visionmapping.dto.response.AiModuleResponses.ModuleCheck(item.row() - 1, item.field(), limit(item.tip(), 300)));
        }
        return new ModuleHelp("CHECK", List.of(), null, List.of(), null, List.of(), null, null, checks);
    }

    /** AH-FR-17: rows the participant edited come back exactly as they wrote them. */
    static List<Map<String, String>> keepRows(List<Map<String, String>> drafted, @Nullable List<Map<String, String>> typed,
                                              List<Integer> keep, List<Field> fields) {
        List<Map<String, String>> result = new ArrayList<>(drafted);
        List<Map<String, String>> original = typed == null ? List.of() : typed;
        for (Integer index : keep) {
            if (index == null || index < 0 || index >= MAX_ROWS || index >= original.size()) {
                continue;
            }
            Map<String, String> own = new LinkedHashMap<>();
            fields.forEach(field -> own.put(field.key(), original.get(index) == null ? ""
                    : Optional.ofNullable(original.get(index).get(field.key())).orElse("")));
            while (result.size() <= index) {
                Map<String, String> empty = new LinkedHashMap<>();
                fields.forEach(field -> empty.put(field.key(), ""));
                result.add(empty);
            }
            result.set(index, own);
        }
        return result.size() > MAX_ROWS ? List.copyOf(result.subList(0, MAX_ROWS)) : List.copyOf(result);
    }

    /** Keeps only the module's own text fields, so nothing else reaches Claude. */
    private static List<Map<String, String>> cleanRows(List<Map<String, String>> rows, List<Field> fields) {
        List<Map<String, String>> cleaned = new ArrayList<>();
        for (Map<String, String> row : Optional.ofNullable(rows).orElse(List.of())) {
            Map<String, String> kept = new LinkedHashMap<>();
            for (Field field : fields) {
                String value = row == null ? null : row.get(field.key());
                if (value != null && !value.isBlank() && !RATING.equals(field.type())) {
                    kept.put(field.key(), limit(value, MAX_VALUE));
                }
            }
            if (!kept.isEmpty()) {
                cleaned.add(kept);
            }
        }
        return cleaned;
    }

    /** Draft rows with every field present; rating fields stay blank for the participant to score. */
    private static List<Map<String, String>> draftRows(List<AiDrafts.RowDraft> rows, List<Field> fields) {
        List<Map<String, String>> drafted = new ArrayList<>();
        for (AiDrafts.RowDraft row : Optional.ofNullable(rows).orElse(List.of())) {
            if (drafted.size() >= MAX_ROWS || row == null || row.values() == null) {
                continue;
            }
            Map<String, String> values = new LinkedHashMap<>();
            fields.forEach(field -> values.put(field.key(), ""));
            boolean any = false;
            for (AiDrafts.FieldValue value : row.values()) {
                Field field = fields.stream().filter(candidate -> candidate.key().equals(value.key())).findFirst().orElse(null);
                if (field != null && !RATING.equals(field.type()) && value.value() != null && !value.value().isBlank()) {
                    values.put(field.key(), limit(value.value(), MAX_VALUE));
                    any = true;
                }
            }
            if (any) {
                drafted.add(values);
            }
        }
        return drafted;
    }

    private static List<String> texts(List<String> values, int max, int length) {
        return Optional.ofNullable(values).orElse(List.of()).stream()
                .filter(value -> value != null && !value.isBlank())
                .limit(max)
                .map(value -> limit(value, length))
                .toList();
    }

    @Nullable
    private static String limit(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max - 3) + "...";
    }
}
