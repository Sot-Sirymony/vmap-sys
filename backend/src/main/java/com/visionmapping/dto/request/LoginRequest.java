package com.visionmapping.dto.request;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email @Size(max = 180) String email,
        @NotBlank @Size(max = 100) String password,
        // V10 (SEC-07-04): keep the session for 30 days (default) or end it with the browser tab.
        Boolean rememberMe
) {
    public boolean remember() {
        return rememberMe == null || rememberMe;
    }
}
