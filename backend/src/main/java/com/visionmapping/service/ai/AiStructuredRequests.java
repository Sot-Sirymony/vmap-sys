package com.visionmapping.service.ai;

import com.visionmapping.entity.AiUsageLog;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.enums.AiOutcome;
import com.visionmapping.exception.AiUnavailableException;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.repository.AiUsageLogRepository;
import com.visionmapping.service.AiSettingsService;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * One structured AI request for a feature such as the goal builder: the coaching
 * guidelines, the participant's records wrapped as data (AI-NFR-13), the task,
 * and a usage-log row for every outcome (AI-NFR-14). The caller has already
 * checked consent, budget and the daily limit.
 */
@Component
@RequiredArgsConstructor
public class AiStructuredRequests {

    private final AiSettingsService aiSettingsService;
    private final AiUsageLogRepository usageLogRepository;
    private final ClaudeClient claudeClient;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public <T> T ask(AppUser user, String apiKey, String feature, String language, String records, String task, Class<T> type) {
        return askTracked(user, apiKey, feature, language, records, task, type).value();
    }

    /**
     * V11 (PB-FR-34, PB-FR-35): the same request, also returning its usage-log
     * row, so the answer can carry a request id the participant rates and the
     * add step reports outcomes against.
     */
    public <T> Tracked<T> askTracked(AppUser user, String apiKey, String feature, String language, String records, String task,
                                     Class<T> type) {
        String guidelines = CoachingGuidelines.system(language, firstName(user.getFullName()), aiSettingsService.current().getCoachingGuidelines());
        // V31 v1 step 3 (BRD 31 section 4): plan building follows the Dream Coach's method and accuracy rules.
        boolean planBuilding = AiPlanFeedbackService.PLAN_FEATURES.contains(feature) && !PlanAssistantService.FEATURE.equals(feature);
        String system = planBuilding ? CoachingGuidelines.withDreamCoachRules(guidelines) : guidelines;
        Tracked<T> answer = askWith(system, user, apiKey, feature, records, task, type);
        // V31 v2 (EC-16): a plan answer in the wrong language is asked for once more, with a firmer instruction.
        if (planBuilding && AnswerLanguage.wrong(objectMapper, answer.value(), language)) {
            Tracked<T> retry = askWith(system, user, apiKey, feature, records, task + AnswerLanguage.retryInstruction(language), type);
            return AnswerLanguage.wrong(objectMapper, retry.value(), language) ? answer : retry;
        }
        return answer;
    }

    /** An answer and the usage-log row it was recorded in. */
    public record Tracked<T>(T value, AiUsageLog usage) {
    }

    /** Phase 8: the same request with the coach co-pilot's guidelines; user is the coach. */
    public <T> T askForCoach(AppUser coach, String apiKey, String feature, String language, String records, String task, Class<T> type) {
        return askWith(CoachingGuidelines.coPilot(language, firstName(coach.getFullName()), aiSettingsService.current().getCoachingGuidelines()),
                coach, apiKey, feature, records, task, type).value();
    }

    private <T> Tracked<T> askWith(String guidelines, AppUser user, String apiKey, String feature, String records, String task, Class<T> type) {
        String system = guidelines + "\n\n" + AiContextBuilder.asParticipantData(records, "the participant's own records.");
        com.visionmapping.entity.AiSettings settings = aiSettingsService.current();
        // V11 (PB-FR-39): plan building may use its own model; everything else uses the main one.
        // V11 Phase 7 (PB-NFR-11): the plan assistant's small changes stay on the main model.
        // V15 (AH-NFR-02): the page assistant and helpers use the simple-task model too, where one is set.
        boolean simpleTask = (AiPlanFeedbackService.PLAN_FEATURES.contains(feature) && !PlanAssistantService.FEATURE.equals(feature))
                || PageAssistantService.FEATURES.contains(feature);
        String model = settings.getPlanModel() != null && simpleTask ? settings.getPlanModel() : settings.getModel();
        // V32 D-32-3: Streamline suggestions and picks may use their own model; empty keeps the rule above.
        if (settings.getStreamlineModel() != null && StreamlineAiService.MODEL_FEATURES.contains(feature)) {
            model = settings.getStreamlineModel();
        }
        ClaudeClient.Structured<T> answer;
        try {
            answer = claudeClient.structured(apiKey, model, system, task, type);
        } catch (AiUnavailableException exception) {
            usageLogRepository.save(AiUsage.entry(user, feature, model, 0, 0, AiOutcome.ERROR));
            throw exception;
        }
        AiUsageLog usage = usageLogRepository.save(AiUsage.entry(user, feature, answer.model(), answer.inputTokens(),
                answer.outputTokens(), answer.refused() ? AiOutcome.REFUSED : AiOutcome.OK));
        if (answer.refused() || answer.value() == null) {
            throw new BusinessRuleException("The Dream Coach could not help with this one. Try rewording it, or ask your coach.");
        }
        return new Tracked<>(answer.value(), usage);
    }

    static String firstName(String fullName) {
        String trimmed = Objects.requireNonNullElse(fullName, "").trim();
        int space = trimmed.indexOf(' ');
        return space < 0 ? trimmed : trimmed.substring(0, space);
    }
}
