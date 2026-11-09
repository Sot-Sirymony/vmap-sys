package com.visionmapping.dto.request;

import java.util.List;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** A blank coachEmail leaves the cohort without a coach. */
public record CohortRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 180) String coachEmail,
        LocalDate startDate,
        // V9 (AI-FR-24): left out keeps the current setting.
        Boolean aiEnabled,
        // V15 (AH-FR-57): pages where the page assistant and helpers are off for this cohort. Left out keeps them.
        @Size(max = 6) List<@Pattern(regexp = PageAssistantRequest.PAGES) String> aiPagesOff) {

    public CohortRequest {
        aiPagesOff = com.visionmapping.util.Copies.list(aiPagesOff);
    }
}
