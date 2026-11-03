package com.visionmapping.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** V9 (v0.1 FR-11): a coach's rating of a participant's pitch. */
public record PitchRatingRequest(
        @NotNull @Min(1) @Max(5) Integer rating,
        @Size(max = 2000) String feedback) {
}
