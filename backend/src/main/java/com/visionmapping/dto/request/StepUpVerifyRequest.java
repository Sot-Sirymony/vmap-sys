package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** V20 (SV-FR-13): the code the person typed; spaces and dashes are ignored. */
public record StepUpVerifyRequest(@NotBlank @Size(max = 20) String code) {
}
