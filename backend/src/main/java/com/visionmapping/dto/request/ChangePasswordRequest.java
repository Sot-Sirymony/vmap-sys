package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Changing a password for the signed-in user.
 *
 * The current password is required even though the request is already
 * authenticated: a JWT proves the session was opened at some point, not that the
 * person holding it right now is the account owner. Re-entering the password is
 * what makes an unattended browser or a stolen token insufficient to lock the
 * real owner out of their account.
 *
 * `currentPassword` carries no {@code @Size} bound on purpose. It is checked
 * against the stored hash, never stored, and a length rule here would only
 * report that an existing password is the "wrong shape" — including for accounts
 * created before any such rule existed.
 */
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8, max = 100) String newPassword
) {
}
