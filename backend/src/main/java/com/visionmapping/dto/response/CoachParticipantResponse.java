package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.time.Instant;
import java.util.List;

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
        ProgressMetricsResponse metrics,
        // AI-FR-19: why this participant may need attention; empty when nothing stands out.
        List<String> riskReasons,
        // AI-NFR-03: AI summaries and drafts about them need their own AI consent.
        boolean aiConsent) {
    public CoachParticipantResponse {
        riskReasons = Copies.list(riskReasons);
    }
}
