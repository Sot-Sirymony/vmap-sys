package com.visionmapping.dto.response;

import com.visionmapping.entity.enums.EnergyDemand;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.WorkStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Builder;

@Builder
public record TaskItemResponse(
        Long id,
        String code,
        Long stepId,
        String title,
        String description,
        String owner,
        Priority priority,
        String letterRank,
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
        Integer sortOrder,
        Integer listOrder,
        boolean archived,
        Instant createdAt,
        Instant updatedAt,
        // V17 BL-FR-28: times this task has entered Blocked.
        int blockedCount,
        // V27 D-27-7: first move to In progress, and completedAt minus that in days (null until both exist).
        Instant startedAt,
        BigDecimal cycleTimeDays,
        // V27 SR-FR-09: the repeat rule this task was made from.
        Long repeatRuleId,
        // V29 AR-FR-06: why it was archived, or null.
        String archiveReason
) {
}
