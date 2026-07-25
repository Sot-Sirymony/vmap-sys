package com.visionmapping.dto.response;

import com.visionmapping.entity.enums.EnergyDemand;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.WorkStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TaskItemResponse(
        Long id,
        String code,
        Long stepId,
        String title,
        String description,
        String owner,
        Priority priority,
        LocalDate startDate,
        LocalDate dueDate,
        WorkStatus status,
        BigDecimal progressPercent,
        BigDecimal estimatedHours,
        BigDecimal actualHours,
        String blockerReason,
        String nextAction,
        EnergyDemand energyDemand,
        Instant completedAt,
        boolean archived,
        Instant createdAt,
        Instant updatedAt
) {
}
