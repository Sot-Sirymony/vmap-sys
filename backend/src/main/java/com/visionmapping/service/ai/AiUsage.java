package com.visionmapping.service.ai;

import com.visionmapping.entity.AiUsageLog;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.enums.AiOutcome;
import java.math.BigDecimal;

/** AI-NFR-14: one usage-log row, priced from the model's rates. Never any conversation text. */
final class AiUsage {

    private AiUsage() {
    }

    static AiUsageLog entry(AppUser user, String feature, String model, long inputTokens, long outputTokens, AiOutcome outcome) {
        return AiUsageLog.builder()
                .user(user)
                .feature(feature)
                .model(model)
                .inputTokens((int) inputTokens)
                .outputTokens((int) outputTokens)
                .costUsd(model == null ? BigDecimal.ZERO : AiPricing.cost(model, inputTokens, outputTokens))
                .outcome(outcome)
                // V11 (PB-FR-36): the Build my plan session this request was counted in, if any.
                .planSessionKey(PlanSessionContext.current())
                .build();
    }
}
