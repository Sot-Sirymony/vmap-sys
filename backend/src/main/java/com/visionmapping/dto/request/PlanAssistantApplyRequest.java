package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * V11 Phase 7 (PB-FR-46): one change card on the Plan page, as the participant
 * left it, to save into their plan. The server checks it again and saves it
 * through the normal services. edited says the participant changed the card
 * before saving; requestId is the answer it came from, for the counts (PB-FR-48).
 */
public record PlanAssistantApplyRequest(
        @NotNull @Pattern(regexp = "EDIT|ADD_STEP|ADD_TASK|ADD_RISK") String kind,
        @Size(max = 60) String itemRef,
        @Pattern(regexp = "TITLE|SUCCESS|WHY|DATE|OWNER") String field,
        @Size(max = 3000) String value,
        @Size(max = 60) String parentRef,
        @Size(max = 220) String title,
        @Size(max = 160) String owner,
        LocalDate date,
        @Size(max = 20) String obstacleType,
        @Size(max = 20) String severity,
        @Size(max = 3000) String solution,
        Long requestId,
        boolean edited) {
}
