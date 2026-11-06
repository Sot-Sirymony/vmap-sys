package com.visionmapping.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email @Size(max = 180) String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        // V9 (D-7): optional; joins the cohort with this code as the account is created.
        @Size(max = 12) String joinCode,
        // V10 (SEC-07-04): keep the session for 30 days (default) or end it with the browser tab.
        Boolean rememberMe
) {
    public boolean remember() {
        return rememberMe == null || rememberMe;
    }
}
