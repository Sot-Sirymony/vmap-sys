package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** V14 (GA-FR-20): the one-time sign-in code from the Google callback. */
public record GoogleExchangeRequest(
        @NotBlank @Size(max = 100) String code,
        // V21 (TF-FR-22): the token of a browser remembered after an earlier second step.
        @Size(max = 100) String deviceToken
) {
}
