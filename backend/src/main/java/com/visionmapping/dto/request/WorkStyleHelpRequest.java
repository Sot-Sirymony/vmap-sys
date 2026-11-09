package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * V15 Phase 5 (AH-FR-70 to AH-FR-75): one Work Style helper. partnerId is the
 * partner for PARTNER; partnerNotes is what the participant types about how
 * that partner works, used for this request only and never stored (AH-FR-79).
 */
public record WorkStyleHelpRequest(
        @NotNull @Pattern(regexp = "EXPLAIN|TIPS|PARTNER|BALANCE") String mode,
        Long partnerId,
        @Size(max = 500) String partnerNotes) {
}
