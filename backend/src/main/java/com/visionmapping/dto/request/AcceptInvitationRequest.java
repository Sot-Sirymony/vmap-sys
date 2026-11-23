package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** V23 (UM-FR-24): the invitation token and the password the person chose. */
public record AcceptInvitationRequest(
        @NotBlank @Size(max = 100) String token,
        @NotBlank @Size(min = 8, max = 100) String password,
        Boolean rememberMe
) {
    public boolean remember() {
        return rememberMe == null || rememberMe;
    }
}
