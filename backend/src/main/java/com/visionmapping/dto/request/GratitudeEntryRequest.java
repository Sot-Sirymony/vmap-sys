package com.visionmapping.dto.request;

import com.visionmapping.entity.enums.GratitudeCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GratitudeEntryRequest(
        @NotNull GratitudeCategory category,
        @NotBlank @Size(max = 2000) String description,
        Long relatedDreamId,
        Long relatedGoalId
) {
}
