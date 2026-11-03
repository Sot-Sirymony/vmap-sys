package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** AI-FR-16: the pitch to practise and who the AI should play. */
public record AiPitchRequest(
        @NotBlank @Pattern(regexp = "PARTNER|BUYER|SPONSOR") String audience,
        @NotBlank @Size(max = 3000) String pitch,
        @Pattern(regexp = "en|km") String language) {
}
