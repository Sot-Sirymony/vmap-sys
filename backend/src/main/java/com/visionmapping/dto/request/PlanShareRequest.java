package com.visionmapping.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * partnerId only labels who the link is for. expiresInDays left out means the
 * default; the link can always be stopped sooner.
 */
public record PlanShareRequest(
        @NotNull Long dreamId,
        Long partnerId,
        @Size(max = 1000) String message,
        @Min(1) @Max(365) Integer expiresInDays) {
}
