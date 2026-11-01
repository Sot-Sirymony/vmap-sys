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
        @NotNull @Min(1) @Max(5) Integer visionScore,
        @NotNull @Min(1) @Max(5) Integer knowledgeScore,
        @NotNull @Min(1) @Max(5) Integer resourcesScore
) {
}
