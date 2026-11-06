package com.visionmapping.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * V11 Phase 4 (PB-FR-14): a complex step that so far exists only in the Build
 * my plan draft, so the task builder can break it down before it is saved.
 */
public record DraftStepTasksRequest(
        @Size(max = 220) String goalTitle,
        LocalDate goalTargetDate,
        @NotBlank @Size(max = 220) String stepTitle,
        @Size(max = 3000) String stepDescription,
        LocalDate stepTargetDate,
        @Valid PlanAskRequest ask) {
}
