package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** V21 (TF-FR-12): asks for a new email code for a sign-in challenge. */
public record TwoFactorResendRequest(@NotBlank @Size(max = 100) String challengeToken) {
}
