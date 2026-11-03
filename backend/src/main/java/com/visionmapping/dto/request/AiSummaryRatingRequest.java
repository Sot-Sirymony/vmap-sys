package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** AC-16: the coach's verdict on a summary. */
public record AiSummaryRatingRequest(@NotNull Boolean accurate, @Size(max = 1000) String note) {
}
