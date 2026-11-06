package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** V11 (PB-FR-08, PB-FR-09): one clarifying question and the participant's answer. Sent to Claude as data. */
public record PlanClarification(
        @NotBlank @Size(max = 500) String question,
        @NotBlank @Size(max = 1000) String answer) {
}
