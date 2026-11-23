package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

/**
 * V24 (SA-FR-05): the new sign-in method settings.
 *
 * @param twoFactorScope PRIVILEGED or EVERYONE; empty keeps the current scope
 */
public record SecurityAccessUpdateRequest(
        @NotNull Boolean passwordSignIn,
        @NotNull Boolean googleSignIn,
        @Nullable @Pattern(regexp = "PRIVILEGED|EVERYONE") String twoFactorScope,
        @NotBlank @Size(min = 10, max = 300) String reason) {
}
