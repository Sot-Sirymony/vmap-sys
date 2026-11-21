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
 *
 * V14 (D-14-3): an account created with Google sets its first password here with
 * only the "Verify it's you" email code, since it has no current password.
 */
public record ChangePasswordRequest(
        // V14 (D-14-3): left out by an account created with Google, which has no
        // password to confirm; the service requires it for every other account.
        @Size(max = 100) String currentPassword,
        @NotBlank @Size(min = 8, max = 100) String newPassword
) {
}
