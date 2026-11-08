package com.visionmapping.service;

import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityAudit;
import com.visionmapping.security.Roles;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.dto.request.ModuleContentRequest;
import com.visionmapping.dto.response.LearningModuleDetailResponse.Field;
import com.visionmapping.dto.response.ModuleContentResponse;
import com.visionmapping.entity.LearningModule;
import com.visionmapping.entity.LearningModuleContent;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.repository.LearningModuleContentRepository;
import com.visionmapping.repository.LearningModuleRepository;
import com.visionmapping.service.support.EntityLookup;
import jakarta.persistence.EntityManager;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-78: editing module text and order without a code change. Modules are shared
 * by every account on this server, so this is the one V8 feature that needs the
 * ADMIN role.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class LearningModuleAdminService {

    private static final String DEFAULT_LANGUAGE = "en";
    private static final Set<String> LANGUAGES = Set.of(DEFAULT_LANGUAGE, "km");
    private static final int ORDER_SHIFT = 1000;
    private static final TypeReference<List<Field>> FIELDS = new TypeReference<>() { };

    private final EntityLookup lookup;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;
    private final LearningModuleRepository moduleRepository;
    private final LearningModuleContentRepository contentRepository;

    @Transactional(readOnly = true)
    public List<ModuleContentResponse> listContent(String language) {
        requireAdmin();
        requireLanguage(language);
        return moduleRepository.findAllByOrderByPositionAsc().stream()
                .map(module -> toResponse(module, language))
                .toList();
    }

    @SecurityAudit(value = SecurityEventType.MODULE_CONTENT_CHANGED, target = "module")
    public ModuleContentResponse updateContent(Integer number, String language, ModuleContentRequest request) {
        requireAdmin();
        requireLanguage(language);
        LearningModule module = moduleRepository.findById(number)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found: " + number));
        LearningModuleContent english = contentRepository.findByModuleNumberAndLanguage(number, DEFAULT_LANGUAGE).orElseThrow();
        LearningModuleContent content = contentRepository.findByModuleNumberAndLanguage(number, language)
                .orElseGet(() -> LearningModuleContent.builder()
                        .moduleNumber(number)
                        .language(language)
                        .fieldsJson(english.getFieldsJson())
                        .build());
        content.setTitle(request.title());
        content.setSummary(request.summary());
        content.setGuidance(request.guidance());
        content.setExerciseInstructions(request.exerciseInstructions());
        content.setFieldsJson(relabel(content.getFieldsJson(), request.fieldLabels()));
        contentRepository.saveAndFlush(content);
        return toResponse(module, language);
    }

    /**
     * Written with SQL in two passes because position is unique: moving modules
     * one at a time would collide partway through. Progress is unaffected, since
     * completions are stored against the module number.
     */
    @SecurityAudit(value = SecurityEventType.MODULE_CONTENT_CHANGED)
    public void reorder(List<Integer> orderedNumbers) {
        requireAdmin();
        Set<Integer> known = new HashSet<>(moduleRepository.findAll().stream().map(LearningModule::getNumber).toList());
        if (orderedNumbers.size() != known.size() || !known.equals(new HashSet<>(orderedNumbers))) {
            throw new BusinessRuleException("The order must include every module exactly once.");
        }
        entityManager.clear();
        jdbcTemplate.update("UPDATE learning_modules SET position = position + ?", ORDER_SHIFT);
        for (int index = 0; index < orderedNumbers.size(); index++) {
            jdbcTemplate.update("UPDATE learning_modules SET position = ? WHERE module_number = ?", index + 1, orderedNumbers.get(index));
        }
    }

    private ModuleContentResponse toResponse(LearningModule module, String language) {
        Optional<LearningModuleContent> translated = contentRepository.findByModuleNumberAndLanguage(module.getNumber(), language);
        LearningModuleContent text = translated
                .orElseGet(() -> contentRepository.findByModuleNumberAndLanguage(module.getNumber(), DEFAULT_LANGUAGE).orElseThrow());
        return new ModuleContentResponse(module.getNumber(), module.getPosition(), language, translated.isPresent(), text.getTitle(),
                text.getSummary(), text.getGuidance(), text.getExerciseInstructions(), parseFields(text.getFieldsJson()));
    }

    private String relabel(@Nullable String fieldsJson, @Nullable Map<String, String> labels) {
        if (fieldsJson == null || labels == null) {
            return fieldsJson;
        }
        List<Field> fields = parseFields(fieldsJson).stream()
                .map(field -> {
                    String label = labels.get(field.key());
                    return label == null || label.isBlank() ? field : new Field(field.key(), label, field.type());
                })
                .toList();
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize module fields", exception);
        }
    }

    private List<Field> parseFields(@Nullable String fieldsJson) {
        if (fieldsJson == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(fieldsJson, FIELDS);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read stored module fields", exception);
        }
    }

    private static void requireLanguage(String language) {
        if (!LANGUAGES.contains(language)) {
            throw new BusinessRuleException("That language is not supported.");
        }
    }

    private void requireAdmin() {
        Roles.requireAdmin(lookup.currentUser(), "Only an administrator can edit module content.");
    }
}
