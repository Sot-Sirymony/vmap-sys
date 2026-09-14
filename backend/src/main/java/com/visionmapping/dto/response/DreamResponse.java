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
        // FR-57: optional intra-area label — a letter, not a strict order.
        String letterRank,
        LocalDate targetDate,
        DreamStatus status,
        boolean moonshot,
        String moonshotVision,
        // FR-56: optional link to an image representing the fulfilled dream.
        String imageUrl,
        ScheduleMode scheduleMode,
        // FR-51: computed, never persisted — true only when scheduleMode is
        // TOP_DOWN_FIXED and this dream's targetDate is earlier than its
        // latest active goal's targetDate.
        boolean scheduleOverrun,
        String scheduleOverrunDetail,
        Boolean decisionSkippedResearch,
        Boolean decisionAssumedNoChange,
        Boolean decisionTrustedUnverifiedClaim,
        Boolean decisionJudgedByAppearance,
        Boolean decisionUnderTimePressure,
        Boolean decisionNoOutsideInput,
        Boolean decisionChasedEasyReward,
        Boolean decisionDismissedDisagreeingAdvice,
        // FR-55.4: non-null once BR-44's gate has cleared for this dream;
        // the gate never re-fires afterward.
        Instant decisionGateClearedAt,
        boolean archived,
        Instant createdAt,
        Instant updatedAt
) {
}
