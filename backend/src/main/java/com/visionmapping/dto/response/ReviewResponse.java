package com.visionmapping.dto.response;

import com.visionmapping.entity.enums.ReviewType;
import java.time.Instant;
import java.time.LocalDateTime;

public record ReviewResponse(
        Long id,
        ReviewType reviewType,
        LocalDateTime reviewDate,
        Long relatedVisionAreaId,
        Long relatedDreamId,
        String summary,
        String completedTasks,
        String delayedTasks,
        String blockedTasks,
        String lessonsLearned,
        String nextActions,
        Boolean diligenceClearVision,
        Boolean diligenceWorkedPlan,
        Boolean diligenceUsedLeverage,
        Boolean diligencePriorityFirst,
        Boolean diligenceSmarterRoute,
        String diligenceNote,
        boolean archived,
        Instant createdAt,
        Instant updatedAt
) {
}
