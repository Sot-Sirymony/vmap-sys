package com.visionmapping.dto.response;

import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.WorkStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record VisionStepResponse(
        Long id,
        String code,
        Long goalId,
        String title,
        String description,
        String owner,
        Integer sequenceNumber,
        boolean complex,
        Priority priority,
        LocalDate targetDate,
        WorkStatus status,
        BigDecimal progressPercent,
        boolean manualProgressOverride,
        boolean archived,
        Instant createdAt,
        Instant updatedAt,
        String resources,
        // AI-FR-11: times this step was given a new date while overdue.
        int overdueReschedules
) {
}
