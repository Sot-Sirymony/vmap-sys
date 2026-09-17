package com.visionmapping.dto.response;

import com.visionmapping.entity.enums.ReviewType;
import java.time.Instant;
import java.time.LocalDateTime;

public record ReviewResponse(
        Long id,
        ReviewType reviewType,
        LocalDateTime reviewDate,
        String letterRank,
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
        Boolean diligenceRightlyPlanned,
        Boolean diligenceRightlyPerformed,
        Boolean diligenceExpeditious,
        Boolean diligenceEfficient,
        Boolean diligenceQualityOutcome,
        // FR-53: computed — met-count / 10 * 100 when all ten are answered,
        // null when the checklist is skipped or (transiently) unanswered.
        Integer diligenceScorePercent,
        String diligenceNote,
        boolean archived,
        Instant createdAt,
        Instant updatedAt
) {
}
