package com.visionmapping.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * V9 (AI-FR-24): what an admin sees of the AI settings. The key itself is
 * never included. keySource is DATABASE (entered here), ENVIRONMENT (the
 * ANTHROPIC_API_KEY server setting) or NONE; a saved key takes priority.
 * keyReadable is false when a saved key can no longer be decrypted.
 */
public record AiSettingsResponse(
        boolean enabled,
        String model,
        List<ModelOption> models,
        BigDecimal monthlyBudgetUsd,
        String keySource,
        boolean keyReadable,
        String apiKeyLast4,
        Instant apiKeyUpdatedAt,
        String apiKeyUpdatedBy,
        Instant lastTestAt,
        Boolean lastTestOk,
        String lastTestMessage,
        // AI-NFR-15 and AI-NFR-09.
        int dailyMessageLimit,
        String helpLines,
        BigDecimal spendThisMonthUsd,
        long requestsThisMonth) {

    public record ModelOption(String id, String label) {
    }
}
