package com.visionmapping.dto.request;

import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.WorkStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record VisionStepRequest(
        @NotNull Long goalId,
        @NotBlank @Size(max = 220) String title,
        @Size(max = 3000) String description,
        @NotBlank @Size(max = 160) String owner,
        @NotNull @PositiveOrZero Integer sequenceNumber,
        boolean complex,
        @NotNull Priority priority,
        @NotNull LocalDate targetDate,
        @NotNull WorkStatus status,
        // v0.1 FR-05: resources and people. Left out keeps what is saved; blank clears it.
        @Size(max = 1000) String resources,
        // V11 (PB-FR-12): effort in hours, and the step of the same goal this one starts after.
        // Left out keeps what is saved; startsAfterStepId 0 clears it.
        @DecimalMin("0.00") @DecimalMax("999.99") BigDecimal estimatedHours,
        Long startsAfterStepId,
        // V17 BL-FR-18: the kind of gap (an ObstacleType name). Left out keeps what is saved; blank clears it.
        @Pattern(regexp = GAP_KIND_PATTERN) String gapKind
) {

    public static final String GAP_KIND_PATTERN = "^(|KNOWLEDGE|SKILL|TIME|MONEY|MOTIVATION|PARTNER|SYSTEM|DECISION|OTHER)$";

    /** The request without a gap kind, for callers that never set one. */
    public VisionStepRequest(Long goalId, String title, String description, String owner, Integer sequenceNumber,
            boolean complex, Priority priority, LocalDate targetDate, WorkStatus status, String resources,
            BigDecimal estimatedHours, Long startsAfterStepId) {
        this(goalId, title, description, owner, sequenceNumber, complex, priority, targetDate, status, resources,
                estimatedHours, startsAfterStepId, null);
    }
}
