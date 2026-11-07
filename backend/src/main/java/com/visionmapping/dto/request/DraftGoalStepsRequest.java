package com.visionmapping.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * V11 Phase 2: a goal that so far exists only in the Build my plan draft, so
 * the plan builder can suggest its steps before the goal is saved.
 */
public record DraftGoalStepsRequest(
        @NotBlank @Size(max = 220) String title,
        @Size(max = 3000) String description,
        @Size(max = 3000) String successCriteria,
        LocalDate targetDate,
        // V11 Phase 3: answers and refinement, as for a saved goal.
        @Valid PlanAskRequest ask) {
}
