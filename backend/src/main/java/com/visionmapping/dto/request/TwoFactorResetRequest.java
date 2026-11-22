package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** V21 (TF-FR-28): why an admin resets a person's two-step sign-in; kept in the security log. */
public record TwoFactorResetRequest(@NotBlank @Size(max = 200) String reason) {
}
