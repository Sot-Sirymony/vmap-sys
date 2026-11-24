package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** V31 v1 (DC-FR-12): the participant's own sentence about a life area, to word as a vision statement. */
public record VisionStatementRequest(
        @NotBlank @Size(max = 120) String areaName,
        @NotBlank @Size(max = 500) String sentence) {
}
