package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** sourceText is the English text as the app has it; text replaces it in that language. */
public record UiTextOverrideRequest(
        @NotBlank @Size(max = 10) String language,
        @NotBlank @Size(max = 1000) String sourceText,
        @NotBlank @Size(max = 1000) String text) {
}
