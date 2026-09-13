package com.visionmapping.dto.request;

import com.visionmapping.entity.enums.DreamStatus;
import com.visionmapping.entity.enums.DreamType;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.ScheduleMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record DreamRequest(
        @NotNull Long visionAreaId,
        @NotBlank @Size(max = 220) String title,
        @Size(max = 3000) String description,
        @Size(max = 3000) String whyImportant,
        @Size(max = 3000) String successDefinition,
        @NotNull DreamType dreamType,
        @NotNull Priority priority,
        LocalDate targetDate,
        @NotNull DreamStatus status,
        boolean moonshot,
        @Size(max = 3000) String moonshotVision,
        @NotNull ScheduleMode scheduleMode,
        Boolean decisionSkippedResearch,
        Boolean decisionAssumedNoChange,
        Boolean decisionTrustedUnverifiedClaim,
        Boolean decisionJudgedByAppearance,
        Boolean decisionUnderTimePressure,
        Boolean decisionNoOutsideInput,
        Boolean decisionChasedEasyReward,
        Boolean decisionDismissedDisagreeingAdvice
) {
}
