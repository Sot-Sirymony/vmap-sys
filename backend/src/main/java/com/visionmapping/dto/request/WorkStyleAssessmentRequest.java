package com.visionmapping.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * FR-49.1: the frontend scores its own 16-item quiz (8 items per axis) into
 * these four raw counts and submits only the result — question wording and
 * per-item scoring stay a frontend concern, the same way the FR-16 diligence
 * checklist's questions live in the page, not the API.
 */
public record WorkStyleAssessmentRequest(
        @NotNull @Min(0) @Max(8) Integer paceFastScore,
        @NotNull @Min(0) @Max(8) Integer paceDeliberateScore,
        @NotNull @Min(0) @Max(8) Integer focusTaskScore,
        @NotNull @Min(0) @Max(8) Integer focusPeopleScore
) {
}
