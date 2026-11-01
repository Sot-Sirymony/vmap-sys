package com.visionmapping.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.dto.request.ModuleCompletionRequest;
import com.visionmapping.dto.response.LearningModuleDetailResponse;
import com.visionmapping.dto.response.LearningModuleDetailResponse.Field;
import com.visionmapping.dto.response.LearningModuleSummaryResponse;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.LearningModule;
import com.visionmapping.entity.LearningModuleContent;
import com.visionmapping.entity.ModuleCompletion;
import com.visionmapping.entity.enums.DreamStatus;
import com.visionmapping.entity.enums.ModuleCompletionRule;
import com.visionmapping.entity.enums.ModuleStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.repository.AttitudeCheckInRepository;
import com.visionmapping.repository.DreamRepository;
import com.visionmapping.repository.FearEntryRepository;
import com.visionmapping.repository.GoalRepository;
import com.visionmapping.repository.HabitRepository;
import com.visionmapping.repository.LearningModuleContentRepository;
import com.visionmapping.repository.LearningModuleRepository;
import com.visionmapping.repository.ModuleCompletionRepository;
import com.visionmapping.repository.PartnerRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.service.support.EntityLookup;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-72: the fifteen modules, unlocked strictly in position order. A lock only
 * ever holds back later modules, never any other feature. Where the app already
 * records an exercise's output, the user's real data proves it instead of a
 * second copy being typed in.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class LearningModuleService {

    private static final String DEFAULT_LANGUAGE = "en";
    private static final int MAX_VALUE_LENGTH = 2000;
    private static final int MAX_ROWS_JSON_LENGTH = 20000;
    private static final String RATING_TYPE = "rating";
    private static final Set<String> RATINGS = Set.of("1", "2", "3", "4", "5");
    private static final TypeReference<List<Field>> FIELDS = new TypeReference<>() { };
    private static final TypeReference<List<Map<String, String>>> ROWS = new TypeReference<>() { };

    private final EntityLookup lookup;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final LearningModuleRepository moduleRepository;
    private final LearningModuleContentRepository contentRepository;
    private final ModuleCompletionRepository completionRepository;
    private final DreamRepository dreamRepository;
    private final GoalRepository goalRepository;
    private final VisionStepRepository visionStepRepository;
    private final PartnerRepository partnerRepository;
    private final FearEntryRepository fearEntryRepository;
    private final AttitudeCheckInRepository attitudeCheckInRepository;
    private final HabitRepository habitRepository;
    private final PlanStatusService planStatusService;

    @Transactional(readOnly = true)
    public List<LearningModuleSummaryResponse> listModules(String language) {
        Map<Integer, LearningModuleContent> content = contentByModule(language);
        Map<Integer, ModuleCompletion> completions = completionsByModule();
        List<LearningModuleSummaryResponse> modules = new ArrayList<>();
        boolean earlierAllComplete = true;
        for (LearningModule module : moduleRepository.findAllByOrderByPositionAsc()) {
            ModuleCompletion completion = completions.get(module.getNumber());
            LearningModuleContent text = content.get(module.getNumber());
            modules.add(new LearningModuleSummaryResponse(module.getNumber(), module.getPosition(), module.getStage(),
                    text.getTitle(), text.getSummary(), status(completion, earlierAllComplete),
                    completion == null ? null : completion.getCompletedAt()));
            earlierAllComplete = earlierAllComplete && completion != null;
        }
        return modules;
    }

    @Transactional(readOnly = true)
    public LearningModuleDetailResponse getModule(Integer number, String language) {
        LearningModule module = module(number);
        requireUnlocked(module);
        return toDetail(module, language);
    }

    public LearningModuleDetailResponse completeModule(Integer number, ModuleCompletionRequest request, String language) {
        LearningModule module = module(number);
        requireUnlocked(module);
        String rowsJson = null;
        if (module.getCompletionRule() == ModuleCompletionRule.FORM) {
            rowsJson = validatedRowsJson(fields(contentByModule(language).get(number)), request.rows());
        } else {
            String missing = missingRequirement(module.getCompletionRule());
            if (missing != null) {
                throw new BusinessRuleException(missing);
            }
        }
        ModuleCompletion completion = completionsByModule().get(number);
        if (completion == null) {
            completion = ModuleCompletion.builder()
                    .user(lookup.currentUser())
                    .moduleNumber(number)
                    .completedAt(Instant.now(clock))
                    .build();
        }
        completion.setRowsJson(rowsJson);
        completionRepository.saveAndFlush(completion);
        return toDetail(module, language);
    }

    private LearningModuleDetailResponse toDetail(LearningModule module, String language) {
        LearningModuleContent text = contentByModule(language).get(module.getNumber());
        ModuleCompletion completion = completionsByModule().get(module.getNumber());
        return new LearningModuleDetailResponse(module.getNumber(), module.getPosition(), module.getStage(), text.getTitle(),
                text.getSummary(), text.getGuidance(), text.getExerciseInstructions(), module.getLinkPath(), fields(text),
                completion == null || completion.getRowsJson() == null ? List.of() : parse(completion.getRowsJson(), ROWS),
                missingRequirement(module.getCompletionRule()),
                status(completion, true),
                completion == null ? null : completion.getCompletedAt());
    }

    private static ModuleStatus status(ModuleCompletion completion, boolean earlierAllComplete) {
        if (completion != null) {
            return ModuleStatus.COMPLETED;
        }
        return earlierAllComplete ? ModuleStatus.AVAILABLE : ModuleStatus.LOCKED;
    }

    /** Null when the rule is met, or when a form proves the exercise rather than a rule. */
    private String missingRequirement(ModuleCompletionRule rule) {
        Long userId = lookup.userId();
        return switch (rule) {
            case FORM -> null;
            case DREAM_RANKED -> dreamRepository.existsByUser_IdAndArchivedFalseAndLetterRankIsNotNull(userId)
                    ? null : "Give at least one dream a letter rank first.";
            case FEAR_LOGGED -> fearEntryRepository.existsByUser_IdAndArchivedFalse(userId)
                    ? null : "Log at least one fear in your Journal first.";
            case PLAN_READY -> planStatusService.anyPlanReady()
                    ? null : "Finish the statement, goals, steps and dates for one dream on the Plan page first.";
            case PASSION_WRITTEN -> missingPassion(userId);
            case COMMITMENT_SIGNED -> dreamRepository.existsByUser_IdAndArchivedFalseAndCommittedAtIsNotNull(userId)
                    ? null : "Sign a commitment on one of your dreams first.";
            case GOAL_STRETCHED -> goalRepository.existsByUser_IdAndArchivedFalseAndMoonshotTrue(userId)
                    ? null : "Mark one of your goals as a moonshot first.";
            case PARTNER_ADDED -> partnerRepository.existsByUser_IdAndArchivedFalse(userId)
                    ? null : "Add at least one partner or mentor first.";
            case ATTITUDE_LOGGED -> attitudeCheckInRepository.existsByUser_Id(userId)
                    ? null : "Save an attitude check-in on the Today page first.";
            case HABIT_ADDED -> habitRepository.countByUser_IdAndArchivedFalse(userId) > 0
                    ? null : "Add at least one habit on the Today page first.";
            case STEP_DATED -> visionStepRepository.existsByUser_IdAndArchivedFalseAndTargetDateIsNotNull(userId)
                    ? null : "Give at least one step a date first.";
        };
    }

    private String missingPassion(Long userId) {
        List<Dream> active = dreamRepository.findByUser_IdAndArchivedFalse(userId).stream()
                .filter(dream -> dream.getStatus() == DreamStatus.ACTIVE)
                .toList();
        if (active.isEmpty()) {
            return "Make at least one dream Active and write why it matters first.";
        }
        List<String> unwritten = active.stream()
                .filter(dream -> dream.getWhyImportant() == null || dream.getWhyImportant().isBlank())
                .map(Dream::getTitle)
                .toList();
        return unwritten.isEmpty() ? null : "Write why it matters for: " + String.join(", ", unwritten) + ".";
    }

    private String validatedRowsJson(List<Field> fields, List<Map<String, String>> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new BusinessRuleException("Add at least one row to save this exercise.");
        }
        Set<String> keys = fields.stream().map(Field::key).collect(Collectors.toSet());
        for (Map<String, String> row : rows) {
            if (!row.keySet().equals(keys)) {
                throw new BusinessRuleException("Fill in every field in each row.");
            }
            for (Field field : fields) {
                String value = row.get(field.key());
                if (value == null || value.isBlank()) {
                    throw new BusinessRuleException("Fill in every field in each row.");
                }
                if (value.length() > MAX_VALUE_LENGTH) {
                    throw new BusinessRuleException("%s is too long.".formatted(field.label()));
                }
                if (RATING_TYPE.equals(field.type()) && !RATINGS.contains(value)) {
                    throw new BusinessRuleException("%s must be from 1 to 5.".formatted(field.label()));
                }
            }
        }
        try {
            String json = objectMapper.writeValueAsString(rows);
            if (json.length() > MAX_ROWS_JSON_LENGTH) {
                throw new BusinessRuleException("This exercise is too long to save. Remove a row or shorten it.");
            }
            return json;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize exercise rows", exception);
        }
    }

    private void requireUnlocked(LearningModule module) {
        Set<Integer> completed = completionsByModule().keySet();
        boolean locked = moduleRepository.findAllByOrderByPositionAsc().stream()
                .filter(earlier -> earlier.getPosition() < module.getPosition())
                .anyMatch(earlier -> !completed.contains(earlier.getNumber()));
        if (locked) {
            throw new BusinessRuleException("Finish the earlier modules to unlock this one.");
        }
    }

    private LearningModule module(Integer number) {
        return moduleRepository.findById(number)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found: " + number));
    }

    /** A language with no row for a module shows that module in English rather than nothing. */
    private Map<Integer, LearningModuleContent> contentByModule(String language) {
        Map<Integer, LearningModuleContent> content = new HashMap<>(contentRepository.findByLanguage(DEFAULT_LANGUAGE).stream()
                .collect(Collectors.toMap(LearningModuleContent::getModuleNumber, Function.identity())));
        if (!DEFAULT_LANGUAGE.equals(language)) {
            contentRepository.findByLanguage(language).forEach(text -> content.put(text.getModuleNumber(), text));
        }
        return content;
    }

    private Map<Integer, ModuleCompletion> completionsByModule() {
        return completionRepository.findByUser_Id(lookup.userId()).stream()
                .collect(Collectors.toMap(ModuleCompletion::getModuleNumber, Function.identity()));
    }

    private List<Field> fields(LearningModuleContent text) {
        return text.getFieldsJson() == null ? List.of() : parse(text.getFieldsJson(), FIELDS);
    }

    private <T> T parse(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read stored module JSON", exception);
        }
    }
}
