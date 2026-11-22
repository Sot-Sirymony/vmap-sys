package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * V21 (TF-FR-12 to TF-FR-15, TF-FR-21): the second step of signing in: the
 * challenge token and either a 6-digit code (authenticator or email) or a
 * recovery code.
 */
public record TwoFactorVerifyRequest(
        @NotBlank @Size(max = 100) String challengeToken,
        @Size(max = 20) String code,
        @Size(max = 20) String recoveryCode,
        boolean rememberDevice
) {
}
