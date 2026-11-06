package com.visionmapping.dto.request;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

public record StatusUpdateRequest(
        @NotBlank @Size(max = 40) String status,
        boolean manualOverride
) {
}
