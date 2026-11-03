package com.visionmapping.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** V9 Phase 8 (AI-FR-18 to AI-FR-21): the coach co-pilot's summaries and drafts. */
public final class AiCoachResponses {

    private AiCoachResponses() {
    }

    /** AI-FR-18. mood is null when the participant does not share journals (D-8). */
    public record ParticipantSummary(Long id, LocalDate weekStart, Instant generatedAt, String progress, String mood,
                                     String risks, String suggestedAction, List<String> riskSignals, Boolean accurate,
                                     String ratingNote) {
    }

    /** AI-FR-21. Only participants who gave AI consent are included; the others are counted. */
    public record CohortSummary(Long id, LocalDate weekStart, Instant generatedAt, String overview, String commonBlockers,
                                String strugglingModules, String suggestedActions, Boolean accurate, String ratingNote) {
    }

    /** AI-FR-20: for the coach to edit and send through the normal comment form. */
    public record FeedbackDraft(String message) {
    }
}
