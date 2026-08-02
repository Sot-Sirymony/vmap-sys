package com.visionmapping.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Asking for a reset link. Only the address — the caller is signed out, so there
 * is nothing else it could prove at this point.
 */
public record ForgotPasswordRequest(
        @NotBlank @Email @Size(max = 180) String email
) {
}
