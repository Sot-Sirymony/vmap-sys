package com.visionmapping.dto.response;

import com.visionmapping.entity.enums.DreamStatus;
import com.visionmapping.entity.enums.DreamType;
import com.visionmapping.entity.enums.Priority;
import java.time.Instant;
import java.time.LocalDate;

public record DreamResponse(
        Long id,
        String code,
        Long visionAreaId,
        String title,
        String description,
        String whyImportant,
        String successDefinition,
        DreamType dreamType,
        Priority priority,
        LocalDate targetDate,
        DreamStatus status,
        boolean moonshot,
        String moonshotVision,
        boolean archived,
        Instant createdAt,
        Instant updatedAt
) {
}
