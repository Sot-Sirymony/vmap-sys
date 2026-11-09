package com.visionmapping.dto.response;

import java.util.List;
import java.time.LocalDate;

public record CohortResponse(
        Long id,
        String name,
        String joinCode,
        Long coachId,
        String coachName,
        String coachEmail,
        LocalDate startDate,
        boolean archived,
        long memberCount,
        boolean aiEnabled,
        // V15 (AH-FR-57).
        List<String> aiPagesOff) {

    public CohortResponse {
        aiPagesOff = com.visionmapping.util.Copies.list(aiPagesOff);
    }
}
