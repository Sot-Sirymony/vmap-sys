package com.visionmapping.dto.response;

import com.visionmapping.entity.enums.FearLikelihood;
import java.time.Instant;

public record FearEntryResponse(
        Long id,
        String fear,
        String worstCase,
        FearLikelihood likelihood,
        String firstTest,
        Long relatedDreamId,
        boolean archived,
        Instant createdAt,
        Instant updatedAt
) {
}
