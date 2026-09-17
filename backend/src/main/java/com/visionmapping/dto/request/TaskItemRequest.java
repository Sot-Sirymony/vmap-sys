package com.visionmapping.dto.request;

import com.visionmapping.entity.enums.EnergyDemand;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.WorkStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TaskItemRequest(
        @NotNull Long stepId,
        @NotBlank @Size(max = 220) String title,
        @Size(max = 3000) String description,
        @NotBlank @Size(max = 160) String owner,
        @NotNull Priority priority,
        // Optional, a single uppercase letter; ties allowed — see Dream.letterRank (FR-57).
        @Pattern(regexp = "^[A-Z]$", message = "Letter rank must be exactly one uppercase letter (A-Z).") String letterRank,
        LocalDate startDate,
        @NotNull LocalDate dueDate,
        @NotNull WorkStatus status,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal progressPercent,
        BigDecimal estimatedHours,
        BigDecimal actualHours,
        @Size(max = 2000) String blockerReason,
        @Size(max = 2000) String nextAction,
        // FR-34.1: optional; null is accepted and read as NEUTRAL (BR-27).
        EnergyDemand energyDemand
) {
}
