package com.visionmapping.dto.request;

import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.entity.enums.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ObstacleRequest(
        Long relatedDreamId,
        Long relatedGoalId,
        Long relatedStepId,
        Long relatedTaskId,
        @NotBlank @Size(max = 220) String title,
        @Size(max = 3000) String description,
        @NotNull ObstacleType obstacleType,
        @NotNull Severity severity,
        @Size(max = 3000) String solution,
        @Size(max = 3000) String rootCause,
        @Size(max = 3000) String creativeAlternatives,
        Long requiredPartnerId,
        @NotNull ObstacleStatus status
) {
}
