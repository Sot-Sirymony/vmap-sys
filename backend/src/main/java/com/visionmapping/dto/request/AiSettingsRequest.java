package com.visionmapping.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** V9 (AI-FR-24): the AI settings other than the key. A field left out keeps its value. */
public record AiSettingsRequest(
        boolean enabled,
        @NotBlank @Size(max = 100) String model,
        @NotNull @DecimalMin("0.00") @DecimalMax("1000.00") BigDecimal monthlyBudgetUsd,
        @Min(1) @Max(500) Integer dailyMessageLimit,
        @Size(max = 2000) String helpLines,
        Boolean aiReminders,
        // AI-FR-24: left out keeps the current text; blank removes it.
        @Size(max = 4000) String coachingGuidelines) {
}
