package com.visionmapping.dto.request;

import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.entity.enums.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * V28 BA-FR-11, BA-FR-12: mark one open task or step as blocked, with what is
 * missing and why. Severity defaults to Medium when left out.
 */
public record MarkBlockedRequest(
        @NotNull @Pattern(regexp = "TASK|STEP") String itemType,
        @NotNull Long itemId,
        @NotNull ObstacleType obstacleType,
        @NotBlank @Size(max = 1000) String reason,
        Severity severity
) {
}
