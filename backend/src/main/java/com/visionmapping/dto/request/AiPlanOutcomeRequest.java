package com.visionmapping.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * V11 (PB-FR-35): how many items of one AI answer the participant added
 * unchanged and how many after editing, for suggestions saved through the
 * normal create endpoints (vision areas and dreams). Counts only, no text.
 */
public record AiPlanOutcomeRequest(
        @Min(0) @Max(20) int added,
        @Min(0) @Max(20) int edited) {
}
