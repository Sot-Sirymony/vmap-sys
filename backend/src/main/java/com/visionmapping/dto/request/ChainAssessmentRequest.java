package com.visionmapping.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ChainAssessmentRequest(
        @NotNull LocalDate takenOn,
        @NotNull @Min(1) @Max(5) Integer settlingScore,
        @NotNull @Min(1) @Max(5) Integer fearScore,
        @NotNull @Min(1) @Max(5) Integer criticismScore,
        // V17 BL-FR-30: the method's six chains. Knowledge and resources are no longer accepted (D-17-3).
        @NotNull @Min(1) @Max(5) Integer prioritiesScore,
        @NotNull @Min(1) @Max(5) Integer persistenceScore,
        @NotNull @Min(1) @Max(5) Integer visionScore
) {
}
