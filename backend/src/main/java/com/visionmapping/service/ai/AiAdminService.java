package com.visionmapping.service.ai;

import com.visionmapping.security.Roles;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.dto.request.AiApprovedResourceRequest;
import com.visionmapping.dto.request.AiModuleDraftRequest;
import com.visionmapping.dto.request.AiTranslateRequest;
import com.visionmapping.dto.response.AiAdminResponses.ModuleContentDraft;
import com.visionmapping.dto.response.AiAdminResponses.QuizItem;
import com.visionmapping.dto.response.AiAdminResponses.Translations;
import com.visionmapping.dto.response.AiApprovedResourceResponse;
import com.visionmapping.entity.AiApprovedResource;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.LearningModuleContent;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.repository.AiApprovedResourceRepository;
import com.visionmapping.repository.LearningModuleContentRepository;
import com.visionmapping.service.support.EntityLookup;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V9 Phase 9 for admins: the approved resource list (AI-FR-26), AI drafts of
 * module text, examples and quiz questions (AI-FR-22), and Khmer translation
 * drafts (AI-FR-23). Drafts are never published here; the admin reviews and
 * saves them in the normal editors (NFR-10).
 */
@Service
@RequiredArgsConstructor
public class AiAdminService {

    static final int MAX_TRANSLATE_CHARS = 20000;
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\w+}");
    private static final TypeReference<List<Map<String, String>>> FIELDS = new TypeReference<>() { };
    /** Keeps the app's words the same in every Khmer draft. */
    private static final String GLOSSARY = """
            Dream = ក្តីស្រមៃ; Goal = គោលដៅ; Step = ជំហាន; Task = កិច្ចការ; Module = ម៉ូឌុល; Coach = គ្រូណែនាំ; \
            Dream Coach = គ្រូបង្វឹកក្តីស្រមៃ; Partner = ដៃគូ; Vision Area = ផ្នែកចក្ខុវិស័យ; Plan = ផែនការ; \
            Journal = កំណត់ហេតុ; Cohort = ក្រុមសិក្សា""";

    private final EntityLookup lookup;
    private final AiAccessService accessService;
    private final AiStructuredRequests requests;
    private final AiApprovedResourceRepository resourceRepository;
    private final LearningModuleContentRepository contentRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<AiApprovedResourceResponse> resources() {
        requireAdmin();
        return resourceRepository.findAllByOrderByNameAsc().stream().map(AiAdminService::toResponse).toList();
    }

    @Transactional
    public AiApprovedResourceResponse createResource(AiApprovedResourceRequest request) {
        requireAdmin();
        AiApprovedResource resource = new AiApprovedResource();
        apply(resource, request);
        return toResponse(resourceRepository.save(resource));
    }

    @Transactional
    public AiApprovedResourceResponse updateResource(Long id, AiApprovedResourceRequest request) {
        requireAdmin();
        AiApprovedResource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found: " + id));
        apply(resource, request);
        return toResponse(resource);
    }

    @Transactional
    public void deleteResource(Long id) {
        requireAdmin();
        resourceRepository.delete(resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found: " + id)));
    }

    /** AI-FR-22: a better English version of one module, plus examples and quiz questions. */
    public ModuleContentDraft draftModule(Integer number, AiModuleDraftRequest request) {
        AppUser admin = requireAdmin();
        LearningModuleContent content = contentRepository.findByModuleNumberAndLanguage(number, "en")
                .orElseThrow(() -> new ResourceNotFoundException("Module not found: " + number));
        String apiKey = accessService.requireAvailableForStaff(null);
        StringBuilder records = new StringBuilder("The module as it reads now:\n")
                .append("Title: ").append(content.getTitle()).append('\n')
                .append("Summary: ").append(content.getSummary()).append('\n')
                .append("Guidance: ").append(content.getGuidance()).append('\n')
                .append("Exercise: ").append(content.getExerciseInstructions()).append('\n');
        fieldLabels(content).forEach(label -> records.append("Form field: ").append(label).append('\n'));
        String instruction = request.instruction() == null || request.instruction().isBlank()
                ? "Make it clearer and more practical." : request.instruction().trim();
        String task = "You are helping a program administrator improve this module's English text. The administrator asks: \""
                + instruction.replace('"', '\'') + "\". Keep the module's purpose and exercise, in plain, warm words for adults "
                + "who may read English as a second language. Write everything in your own original words: never quote or "
                + "closely paraphrase any book. Add two or three short examples about made-up people and three to five quiz "
                + "questions with answers.";
        AiDrafts.ModuleContentDraft draft = requests.askForCoach(admin, apiKey, "ADMIN_MODULE_DRAFT", "en",
                records.toString(), task, AiDrafts.ModuleContentDraft.class);
        List<QuizItem> quiz = Optional.ofNullable(draft.quiz()).orElse(List.of()).stream()
                .filter(item -> item != null && item.question() != null && !item.question().isBlank())
                .limit(5)
                .map(item -> new QuizItem(limit(item.question(), 300), limit(item.answer(), 600)))
                .toList();
        return new ModuleContentDraft(limit(draft.title(), 200), limit(draft.summary(), 1000), limit(draft.guidance(), 4000),
                limit(draft.exerciseInstructions(), 1000), limit(draft.examples(), 2000), quiz);
    }

    /** AI-FR-23: Khmer drafts of English texts. A draft that loses a {placeholder} is dropped. */
    public Translations translate(AiTranslateRequest request) {
        AppUser admin = requireAdmin();
        int total = request.texts().stream().mapToInt(text -> text == null ? 0 : text.length()).sum();
        if (total > MAX_TRANSLATE_CHARS) {
            throw new BusinessRuleException("That is too much text at once. Translate fewer items.");
        }
        String apiKey = accessService.requireAvailableForStaff(null);
        StringBuilder records = new StringBuilder("Texts to translate, numbered:\n");
        for (int index = 0; index < request.texts().size(); index++) {
            records.append(index + 1).append(". ").append(request.texts().get(index)).append('\n');
        }
        String task = "Translate each numbered English text into natural, clear Khmer for adults in a coaching program, as a "
                + "draft a Khmer speaker will review. Return one translation per text, in the same order. Keep anything in curly "
                + "braces, such as {count}, exactly as it is. Use these terms: " + GLOSSARY;
        AiDrafts.TranslationDraft draft = requests.askForCoach(admin, apiKey, "ADMIN_TRANSLATE", "km", records.toString(), task,
                AiDrafts.TranslationDraft.class);
        List<String> answers = Optional.ofNullable(draft.translations()).orElse(List.of());
        List<String> translations = new ArrayList<>();
        for (int index = 0; index < request.texts().size(); index++) {
            String source = request.texts().get(index);
            String answer = index < answers.size() ? answers.get(index) : null;
            boolean keeps = answer != null && !answer.isBlank() && placeholders(source).equals(placeholders(answer));
            translations.add(keeps ? answer.trim() : null);
        }
        return new Translations(translations);
    }

    private List<String> fieldLabels(LearningModuleContent content) {
        if (content.getFieldsJson() == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(content.getFieldsJson(), FIELDS).stream().map(field -> field.get("label")).toList();
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private static List<String> placeholders(String text) {
        List<String> found = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(text == null ? "" : text);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        found.sort(String::compareTo);
        return found;
    }

    private static void apply(AiApprovedResource resource, AiApprovedResourceRequest request) {
        resource.setKind(request.kind());
        resource.setName(request.name().trim());
        resource.setUrl(request.url() == null || request.url().isBlank() ? null : request.url().trim());
        resource.setDescription(request.description() == null || request.description().isBlank() ? null : request.description().trim());
        resource.setActive(request.active());
    }

    private static AiApprovedResourceResponse toResponse(AiApprovedResource resource) {
        return new AiApprovedResourceResponse(resource.getId(), resource.getKind(), resource.getName(), resource.getUrl(),
                resource.getDescription(), resource.isActive());
    }

    private AppUser requireAdmin() {
        return Roles.requireAdmin(lookup.currentUser(), "Only an administrator can change AI settings.");
    }

    private static String limit(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max - 3) + "...";
    }
}
