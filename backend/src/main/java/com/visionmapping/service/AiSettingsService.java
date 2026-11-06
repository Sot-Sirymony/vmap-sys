package com.visionmapping.service;

import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityAudit;
import com.visionmapping.security.Roles;
import com.visionmapping.dto.request.AiApiKeyRequest;
import com.visionmapping.dto.request.AiSettingsRequest;
import com.visionmapping.dto.response.AiSettingsResponse;
import com.visionmapping.dto.response.AiSettingsResponse.ModelOption;
import com.visionmapping.entity.AiSettings;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.repository.AiSettingsRepository;
import com.visionmapping.repository.AiUsageLogRepository;
import com.visionmapping.service.support.ClaudeKeyChecker;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.service.support.SecretCipher;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V9 (AI-FR-24): AI settings an admin manages without code, including the
 * Claude API key. The key is encrypted at rest, never returned, and only the
 * backend uses it. A key saved here takes priority over the ANTHROPIC_API_KEY
 * server setting, so the key can be changed without a restart.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AiSettingsService {

    static final List<ModelOption> MODELS = List.of(
            new ModelOption("claude-opus-5-5", "Claude Opus 5.5 (recommended)"),
            new ModelOption("claude-sonnet-5-5", "Claude Sonnet 5.5"),
            new ModelOption("claude-haiku-5-5", "Claude Haiku 5.5"));

    private final EntityLookup lookup;
    private final AiSettingsRepository repository;
    private final SecretCipher cipher;
    private final ClaudeKeyChecker keyChecker;
    private final AiUsageLogRepository usageLogRepository;
    private final Clock clock;

    @Value("${app.ai.api-key:}")
    private String environmentApiKey;

    @Transactional(readOnly = true)
    public AiSettingsResponse get() {
        requireAdmin();
        return toResponse(settings());
    }

    @SecurityAudit(value = SecurityEventType.AI_SETTINGS_CHANGED)
    public AiSettingsResponse update(AiSettingsRequest request) {
        requireAdmin();
        if (MODELS.stream().noneMatch(option -> option.id().equals(request.model()))) {
            throw new BusinessRuleException("That model is not one of the choices.");
        }
        AiSettings settings = settings();
        if (request.enabled() && apiKey(settings) == null) {
            throw new BusinessRuleException("Add a working API key before you turn AI on.");
        }
        if (!request.model().equals(settings.getModel())) {
            clearTest(settings);
        }
        settings.setEnabled(request.enabled());
        settings.setModel(request.model());
        settings.setMonthlyBudgetUsd(request.monthlyBudgetUsd());
        if (request.dailyMessageLimit() != null) {
            settings.setDailyMessageLimit(request.dailyMessageLimit());
        }
        if (request.coachingGuidelines() != null) {
            settings.setCoachingGuidelines(request.coachingGuidelines().isBlank() ? null : request.coachingGuidelines().trim());
        }
        if (request.aiReminders() != null) {
            settings.setAiReminders(request.aiReminders());
        }
        if (request.planModel() != null) {
            String planModel = request.planModel().trim();
            if (!planModel.isEmpty() && MODELS.stream().noneMatch(option -> option.id().equals(planModel))) {
                throw new BusinessRuleException("That plan building model is not one of the choices.");
            }
            settings.setPlanModel(planModel.isEmpty() ? null : planModel);
        }
        if (request.helpLines() != null) {
            if (request.helpLines().isBlank()) {
                throw new BusinessRuleException("Help lines cannot be empty; they are shown to anyone in crisis.");
            }
            settings.setHelpLines(request.helpLines().trim());
        }
        return toResponse(settings);
    }

    @SecurityAudit(value = SecurityEventType.AI_KEY_CHANGED)
    public AiSettingsResponse saveApiKey(AiApiKeyRequest request) {
        requireAdmin();
        String key = request.apiKey().trim();
        if (!key.startsWith("sk-ant-") || key.chars().anyMatch(Character::isWhitespace)) {
            throw new BusinessRuleException("That does not look like a Claude API key. It starts with sk-ant-.");
        }
        AiSettings settings = settings();
        settings.setApiKeyEncrypted(cipher.encrypt(key));
        settings.setApiKeyLast4(key.substring(key.length() - 4));
        settings.setApiKeyUpdatedAt(Instant.now(clock));
        settings.setApiKeyUpdatedBy(lookup.currentUser());
        clearTest(settings);
        return toResponse(settings);
    }

    /** Removes the saved key; AI turns off unless the server setting supplies one. */
    @SecurityAudit(value = SecurityEventType.AI_KEY_REMOVED)
    public AiSettingsResponse removeApiKey() {
        requireAdmin();
        AiSettings settings = settings();
        settings.setApiKeyEncrypted(null);
        settings.setApiKeyLast4(null);
        settings.setApiKeyUpdatedAt(Instant.now(clock));
        settings.setApiKeyUpdatedBy(lookup.currentUser());
        clearTest(settings);
        if (apiKey(settings) == null) {
            settings.setEnabled(false);
        }
        return toResponse(settings);
    }

    public AiSettingsResponse test() {
        requireAdmin();
        AiSettings settings = settings();
        String key = apiKey(settings);
        ClaudeKeyChecker.Result result = key == null
                ? new ClaudeKeyChecker.Result(false, "There is no API key to test.")
                : keyChecker.check(key, settings.getModel());
        settings.setLastTestAt(Instant.now(clock));
        settings.setLastTestOk(result.ok());
        settings.setLastTestMessage(result.message());
        return toResponse(settings);
    }

    /** For the AI features: the settings without the admin check. Never expose the key from it. */
    @Transactional(readOnly = true)
    public AiSettings current() {
        return settings();
    }

    /** For the AI features: the key to use, or null when there is none. Never send it to a client. */
    @Transactional(readOnly = true)
    public String activeApiKey() {
        return apiKey(settings());
    }

    private String apiKey(AiSettings settings) {
        if (settings.getApiKeyEncrypted() != null) {
            return cipher.decrypt(settings.getApiKeyEncrypted());
        }
        return environmentApiKey == null || environmentApiKey.isBlank() ? null : environmentApiKey.trim();
    }

    private AiSettingsResponse toResponse(AiSettings settings) {
        boolean saved = settings.getApiKeyEncrypted() != null;
        boolean fromEnvironment = !saved && environmentApiKey != null && !environmentApiKey.isBlank();
        String source = saved ? "DATABASE" : fromEnvironment ? "ENVIRONMENT" : "NONE";
        String last4 = saved ? settings.getApiKeyLast4()
                : fromEnvironment ? environmentApiKey.trim().substring(Math.max(0, environmentApiKey.trim().length() - 4)) : null;
        return new AiSettingsResponse(settings.isEnabled(), settings.getModel(), MODELS, settings.getMonthlyBudgetUsd(),
                source, !saved || cipher.decrypt(settings.getApiKeyEncrypted()) != null, last4,
                settings.getApiKeyUpdatedAt(),
                settings.getApiKeyUpdatedBy() == null ? null : settings.getApiKeyUpdatedBy().getFullName(),
                settings.getLastTestAt(), settings.getLastTestOk(), settings.getLastTestMessage(),
                settings.getDailyMessageLimit(), settings.getHelpLines(),
                usageLogRepository.totalCostSince(monthStart()), usageLogRepository.countByCreatedAtGreaterThanEqual(monthStart()),
                settings.isAiReminders(), settings.getCoachingGuidelines(), settings.getPlanModel());
    }

    private Instant monthStart() {
        return LocalDate.now(clock).withDayOfMonth(1).atStartOfDay(clock.getZone()).toInstant();
    }

    private static void clearTest(AiSettings settings) {
        settings.setLastTestAt(null);
        settings.setLastTestOk(null);
        settings.setLastTestMessage(null);
    }

    private AiSettings settings() {
        return repository.findById(AiSettings.SINGLE_ROW_ID)
                .orElseThrow(() -> new IllegalStateException("The ai_settings row is missing; migration V54 adds it."));
    }

    private void requireAdmin() {
        Roles.requireAdmin(lookup.currentUser(), "Only an administrator can change AI settings.");
    }
}
