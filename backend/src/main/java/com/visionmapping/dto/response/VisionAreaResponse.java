package com.visionmapping.dto.response;

import com.visionmapping.entity.enums.LifecycleStatus;
import com.visionmapping.entity.enums.Priority;
import java.time.Instant;

public record VisionAreaResponse(
        Long id,
        String code,
        String name,
        String description,
        String visionStatement,
        Priority priority,
        LifecycleStatus status,
        boolean archived,
        Instant createdAt,
        Instant updatedAt
) {
}
