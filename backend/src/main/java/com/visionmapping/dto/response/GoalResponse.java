package com.visionmapping.dto.response;

import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.ScheduleMode;
import com.visionmapping.entity.enums.WorkStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record GoalResponse(
        Long id,
        String code,
        Long dreamId,
        String title,
        String description,
        String successCriteria,
        Priority priority,
        String letterRank,
        LocalDate targetDate,
        WorkStatus status,
        BigDecimal progressPercent,
        boolean manualProgressOverride,
        boolean moonshot,
        String moonshotVision,
        ScheduleMode scheduleMode,
        // FR-51: computed, never persisted — true only when scheduleMode is
        // TOP_DOWN_FIXED and this goal's targetDate is earlier than its
        // latest active step's targetDate.
        boolean scheduleOverrun,
        String scheduleOverrunDetail,
        boolean archived,
        Instant createdAt,
        Instant updatedAt
) {
}
