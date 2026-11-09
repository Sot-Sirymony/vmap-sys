package com.visionmapping.dto.request;

import com.visionmapping.entity.enums.Relevance;
import com.visionmapping.entity.enums.SelfAssessmentSource;
import com.visionmapping.entity.enums.StrengthKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * FR-79 to FR-83: one strength or weakness. Evidence is required (BR-61). The
 * outside view is optional; outsideAgrees is ignored when no view is written.
 */
public record SelfAssessmentItemRequest(
        @NotNull Long dreamId,
        @NotNull StrengthKind kind,
        @NotBlank @Size(max = 300) String description,
        @NotBlank(message = "Give a real example as evidence.") @Size(max = 2000) String evidence,
        @NotNull Relevance relevance,
        SelfAssessmentSource source,
        @Size(max = 160) String outsideBy,
        @Size(max = 2000) String outsideView,
        Boolean outsideAgrees
) {
}
