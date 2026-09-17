package com.visionmapping.dto.request;

import com.visionmapping.entity.enums.LifecycleStatus;
import com.visionmapping.entity.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VisionAreaRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 2000) String description,
        @Size(max = 3000) String visionStatement,
        @NotNull Priority priority,
        // Optional, a single uppercase letter; ties allowed — see Dream.letterRank (FR-57).
        @Pattern(regexp = "^[A-Z]$", message = "Letter rank must be exactly one uppercase letter (A-Z).") String letterRank,
        @NotNull LifecycleStatus status
) {
}
