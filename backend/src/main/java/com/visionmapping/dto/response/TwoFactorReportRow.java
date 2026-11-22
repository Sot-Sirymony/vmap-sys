package com.visionmapping.dto.response;

import java.time.Instant;
import org.springframework.lang.Nullable;

/**
 * V21 (TF-FR-36): one person in the admin two-step sign-in report: admins,
 * coaches, and anyone else who has it on.
 */
public record TwoFactorReportRow(
        Long userId,
        String fullName,
        String email,
        String role,
        boolean enabled,
        @Nullable String method,
        @Nullable Instant dueAt,
        boolean overdue
) {
}
