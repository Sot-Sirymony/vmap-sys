package com.visionmapping.dto.response;

import com.visionmapping.dto.request.PlanDraftContent;
import java.time.Instant;

/**
 * V11 Phase 2 (PB-FR-30): the participant's own unfinished Build my plan draft.
 * aiRequestsUsed and aiRequestLimit (PB-NFR-03): the session's AI requests so far, and its limit.
 */
public record PlanDraftResponse(Long dreamId, String stage, PlanDraftContent content, Instant updatedAt, Instant expiresAt,
                                int aiRequestsUsed, int aiRequestLimit) {
}
