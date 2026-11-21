package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** V14 (GA-FR-20): the one-time sign-in code from the Google callback. */
public record GoogleExchangeRequest(@NotBlank @Size(max = 100) String code) {
}
