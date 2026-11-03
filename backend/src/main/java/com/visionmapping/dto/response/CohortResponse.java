package com.visionmapping.dto.response;

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
        boolean aiEnabled) {
}
