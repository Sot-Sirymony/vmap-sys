package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

/** fieldLabels renames form fields by key; the set of fields itself cannot change here. */
public record ModuleContentRequest(
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 600) String summary,
        @NotBlank @Size(max = 3000) String guidance,
        @NotBlank @Size(max = 1000) String exerciseInstructions,
        Map<String, String> fieldLabels
) {
}
