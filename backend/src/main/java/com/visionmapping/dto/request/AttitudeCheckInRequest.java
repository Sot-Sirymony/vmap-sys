package com.visionmapping.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AttitudeCheckInRequest(
        @NotNull @Min(1) @Max(5) Integer score,
        @Size(max = 1000) String note
) {
}
