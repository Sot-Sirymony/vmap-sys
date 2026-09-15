package com.visionmapping.dto.response;

import com.visionmapping.entity.enums.GratitudeCategory;
import java.time.Instant;

public record GratitudeEntryResponse(
        Long id,
        GratitudeCategory category,
        String description,
        Long relatedDreamId,
        Long relatedGoalId,
        boolean archived,
        Instant createdAt,
        Instant updatedAt
) {
}
