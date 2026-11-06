package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * V11 (PB-FR-10): one clarifying answer the participant chose to save into
 * their dream. For TARGET_DATE the value is a date as YYYY-MM-DD.
 */
public record PlanClarifiedFieldRequest(
        @NotNull @Size(max = 20) @Pattern(regexp = "WHY_IMPORTANT|SUCCESS_DEFINITION|TARGET_DATE") String field,
        @NotBlank @Size(max = 3000) String value) {
}
