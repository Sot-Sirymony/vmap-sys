package com.visionmapping.dto.response;

import java.time.Instant;

/** AI-NFR-09: when a participant may need a check-in. Never the message itself. */
public record AiSafetyAlertResponse(
        Long id,
        Long participantId,
        String participantName,
        String cohortName,
        Instant createdAt,
        Instant acknowledgedAt) {
}
