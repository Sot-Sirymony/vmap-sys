package com.visionmapping.dto.response;

import java.time.LocalDate;

/** The participant's own cohort; cohortId is null when they have not joined one. */
public record MyCohortResponse(
        Long cohortId,
        String cohortName,
        String coachName,
        LocalDate startDate,
        boolean shareJournalsWithCoach) {
}
