package com.visionmapping.dto.request;

import com.visionmapping.entity.enums.PartnerSupportType;
import com.visionmapping.entity.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * V17 BL-FR-19, BL-FR-20: a "find help" step made from a step's gap or from an
 * obstacle, with an optional To Contact partner.
 *
 * <p>{@code goalId} is needed only when the source does not fix the goal: an
 * obstacle that is not on a step yet. From a step, the new step always joins
 * that step's goal.
 */
public record GapStepRequest(
        Long goalId,
        @NotBlank @Size(max = 220) String title,
        @Size(max = 3000) String description,
        @NotBlank @Size(max = 160) String owner,
        @NotNull Priority priority,
        @NotNull LocalDate targetDate,
        @Pattern(regexp = VisionStepRequest.GAP_KIND_PATTERN) String gapKind,
        boolean createPartner,
        @Size(max = 180) String partnerName,
        PartnerSupportType partnerSupportType
) {
}
