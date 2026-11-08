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
        String apiKey = accessService.requireAvailable(user);
        String records = transactions.execute(status -> contextBuilder.build(user,
                new AiContextBuilder.Focus("/modules/" + number, null, number), language).records());
        StringBuilder data = new StringBuilder(records).append("\n\nThis module's focus: ")
                .append(FOCUS.getOrDefault(number, "Help the participant apply this module to their own dreams."));
        if (!fields.isEmpty()) {
            data.append("\nExercise fields (key: label):\n");
            fields.stream().filter(field -> !RATING.equals(field.type()))
                    .forEach(field -> data.append("- ").append(field.key()).append(": ").append(clip(field.label())).append('\n'));
        }
        if (!typed.isEmpty()) {
            data.append("\nWhat the participant has written so far, not yet saved:\n");
            typed.forEach(row -> data.append("- ").append(row.entrySet().stream()
                    .map(entry -> entry.getKey() + " = " + clip(entry.getValue())).collect(Collectors.joining("; "))).append('\n'));
        }
        String task = switch (mode) {
            case "START" -> "Help the participant start this exercise. Give up to three short questions that would help them "
                    + "think it through, most useful first, and one short example answer about a made-up person. Leave rows, "
                    + "feedback and improvements empty.";
            case "DRAFT" -> "Draft answers for this exercise from the participant's own records, in their voice, for them to edit. "
                    + "Use only the field keys listed, at most " + MAX_ROWS + " rows. Never invent facts about their life; when the "
                    + "records do not say, keep the answer general and short. Leave questions, example, feedback and improvements empty.";
            default -> "Give honest, kind feedback on what the participant has written, against this module's focus, and up to "
                    + "three specific improvements. Leave questions, example and rows empty.";
        };
        AiDrafts.ModuleHelpDraft draft = requests.ask(user, apiKey, "MODULE_HELP", language, data.toString(), task,
                AiDrafts.ModuleHelpDraft.class);
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
        String apiKey = accessService.requireAvailable(user);
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
            ChainAssessment first = taken.get(0);
            data.append("Their first assessment, on ").append(first.getTakenOn()).append(", scored ")
                    .append(first.getSettlingScore()).append(", ").append(first.getFearScore()).append(", ")
                    .append(first.getCriticismScore()).append(", ").append(first.getVisionScore()).append(", ")
                    .append(first.getKnowledgeScore()).append(", ").append(first.getResourcesScore()).append(" in the same order.\n");
        }
        data.append("\nThe program's modules:\n");
        modules.forEach(module -> data.append("- ").append(module.number()).append(". ").append(clip(module.title()))
                .append(" (").append(module.status()).append(")\n"));
        String task = "Explain this result in plain, encouraging words: what is strong, what is holding them back, and any change "
                + "since their first assessment. Then name the one module that would help most now, preferring one that is not "
                + "completed, and say why.";
        AiDrafts.AssessmentExplanationDraft draft = requests.ask(user, apiKey, "ASSESSMENT_EXPLAIN", language, data.toString(), task,
                AiDrafts.AssessmentExplanationDraft.class);
        Optional<LearningModuleSummaryResponse> focus = modules.stream()
                .filter(module -> module.number() == draft.focusModule()).findFirst();
        return new AssessmentExplanation(limit(draft.explanation(), 2000), focus.map(LearningModuleSummaryResponse::number).orElse(null),
                focus.map(LearningModuleSummaryResponse::title).orElse(null), limit(draft.reason(), 300));
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
