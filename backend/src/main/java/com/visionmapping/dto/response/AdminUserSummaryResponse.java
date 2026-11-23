package com.visionmapping.dto.response;

import java.time.Instant;

/**
 * V23 (BRD 23.0.0, UM-FR-17): one row of the Users page. Account details only,
 * never plan data (D-23-3).
 *
 * @param twoFactor ON, DUE (must set it up by {@code twoFactorDueAt}) or OFF
 */
public record AdminUserSummaryResponse(
        Long id,
        String fullName,
        String email,
        String role,
        String roleName,
        String status,
        Long cohortId,
        String cohortName,
        String twoFactor,
        Instant twoFactorDueAt,
        Instant joinedAt,
        Instant lastSignInAt
) {
}
