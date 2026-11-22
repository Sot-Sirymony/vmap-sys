package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.time.Instant;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * V21 (TF-FR-01): the Security page's two-factor panel. {@code method} is TOTP
 * or EMAIL while it is on. {@code emailMethodAvailable} is false for admins and
 * coaches (D-21-1) and without mail (TF-FR-38). {@code canTurnOff} is false for
 * an admin or coach past their set-up-by date (TF-FR-08).
 */
public record TwoFactorStatusResponse(
        boolean enabled,
        @Nullable String method,
        @Nullable Instant enabledAt,
        long recoveryCodesLeft,
        @Nullable Instant dueAt,
        boolean emailMethodAvailable,
        boolean canTurnOff,
        List<TrustedDeviceResponse> trustedDevices
) {

    public TwoFactorStatusResponse {
        trustedDevices = Copies.list(trustedDevices);
    }
}
