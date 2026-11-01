package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record MilestoneRequest(
        @NotNull Long dreamId,
        @NotBlank @Size(max = 220) String title,
        @NotNull LocalDate achievedOn,
        @Size(max = 1000) String note
) {
}
