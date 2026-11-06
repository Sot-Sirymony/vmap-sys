package com.visionmapping.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** V11 Phase 2 (PB-FR-30): save the Build my plan draft and the stage the participant is on. */
public record PlanDraftRequest(
        @NotNull @Size(max = 20) @Pattern(regexp = "CLARIFY|GOALS|STEPS|TASKS|HELPERS|REVIEW") String stage,
        @NotNull @Valid PlanDraftContent content) {
}
