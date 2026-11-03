package com.visionmapping.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** AI-FR-06: 1 is thumbs up, -1 thumbs down, 0 clears the rating. */
public record AiRatingRequest(
        @NotNull @Min(-1) @Max(1) Integer rating,
        @Size(max = 1000) String comment) {
}
