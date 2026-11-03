package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** A blank coachEmail leaves the cohort without a coach. */
public record CohortRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 180) String coachEmail,
        LocalDate startDate,
        // V9 (AI-FR-24): left out keeps the current setting.
        Boolean aiEnabled) {
}
