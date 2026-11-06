package com.visionmapping.dto.response;

import com.visionmapping.dto.request.PlanDraftContent;
import java.time.Instant;

/** V11 Phase 2 (PB-FR-30): the participant's own unfinished Build my plan draft. */
public record PlanDraftResponse(Long dreamId, String stage, PlanDraftContent content, Instant updatedAt, Instant expiresAt) {
}
