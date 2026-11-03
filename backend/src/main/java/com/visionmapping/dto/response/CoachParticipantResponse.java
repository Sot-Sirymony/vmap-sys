package com.visionmapping.dto.response;

import java.time.Instant;

/** One row of the coach workspace (v0.1 FR-16, FR-17). atRisk means no activity for 7 days or more (AC-05). */
public record CoachParticipantResponse(
        Long userId,
        String fullName,
        String email,
        Long cohortId,
        String cohortName,
        Instant lastActiveAt,
        long daysInactive,
        boolean atRisk,
        ProgressMetricsResponse metrics) {
}
