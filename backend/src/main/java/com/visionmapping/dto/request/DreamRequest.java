package com.visionmapping.dto.request;

import com.visionmapping.entity.enums.DreamStatus;
import com.visionmapping.entity.enums.DreamType;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.ScheduleMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
        // FR-57 / BR-46: optional, a single uppercase letter; ties allowed.
        @Pattern(regexp = "^[A-Z]$", message = "Letter rank must be exactly one uppercase letter (A-Z).") String letterRank,
        LocalDate targetDate,
        @NotNull DreamStatus status,
        boolean moonshot,
        @Size(max = 3000) String moonshotVision,
        // FR-56 / BR-45: optional link to an image, never an upload.
        @Pattern(regexp = "^https?://.+", message = "Image link must be a well-formed http:// or https:// URL.")
        @Size(max = 2048) String imageUrl,
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
