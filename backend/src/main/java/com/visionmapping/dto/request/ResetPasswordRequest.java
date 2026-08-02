package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Redeeming a reset link. The token stands in for the old password: it is proof
 * of access to the account's inbox, which is the only credential someone who has
 * forgotten their password still has.
 *
 * No email field. The token identifies the account by itself, and trusting a
 * caller-supplied address alongside it would let one be paired with the other's
 * token.
 */
public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 100) String newPassword
) {
}
