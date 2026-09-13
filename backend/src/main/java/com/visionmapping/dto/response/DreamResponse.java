package com.visionmapping.dto.response;

import com.visionmapping.entity.enums.DreamStatus;
import com.visionmapping.entity.enums.DreamType;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.ScheduleMode;
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
        ScheduleMode scheduleMode,
        // FR-51: computed, never persisted — true only when scheduleMode is
        // TOP_DOWN_FIXED and this dream's targetDate is earlier than its
        // latest active goal's targetDate.
        boolean scheduleOverrun,
        String scheduleOverrunDetail,
        boolean archived,
        Instant createdAt,
        Instant updatedAt
) {
}
