package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotNull;

/** V15 (AH-FR-76): the "Let AI helpers use my work style" switch. */
public record WorkStyleAiUseRequest(@NotNull Boolean enabled) {
}
