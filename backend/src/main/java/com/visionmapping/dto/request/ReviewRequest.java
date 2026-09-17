package com.visionmapping.dto.request;

import com.visionmapping.entity.enums.ReviewType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record ReviewRequest(
        @NotNull ReviewType reviewType,
        @NotNull LocalDateTime reviewDate,
        // Optional, a single uppercase letter; ties allowed — see Dream.letterRank (FR-57).
        @Pattern(regexp = "^[A-Z]$", message = "Letter rank must be exactly one uppercase letter (A-Z).") String letterRank,
        Long relatedVisionAreaId,
        Long relatedDreamId,
        @Size(max = 3000) String summary,
        @Size(max = 3000) String completedTasks,
        @Size(max = 3000) String delayedTasks,
        @Size(max = 3000) String blockedTasks,
        @Size(max = 3000) String lessonsLearned,
        @Size(max = 3000) String nextActions,
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
        @Size(max = 2000) String diligenceNote,
        // FR-59.3: optional — if answered, spawns a Person-agnostic (OTHER
        // category) GratitudeEntry; never persisted on the Review itself.
        @Size(max = 2000) String gratitudeNote
) {
}
