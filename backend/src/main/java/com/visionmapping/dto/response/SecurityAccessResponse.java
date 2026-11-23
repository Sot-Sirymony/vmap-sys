package com.visionmapping.dto.response;

import java.time.Instant;
import org.springframework.lang.Nullable;

/**
 * V24 (SA-FR-02): the sign-in method settings and how many people each method
 * affects. Counts leave out the Admin role (D-24-5). {@code canChange} is true
 * for Admins only (D-24-2).
 */
public record SecurityAccessResponse(
        boolean passwordSignIn,
        boolean googleSignIn,
        String twoFactorScope,
        boolean googleAvailable,
        boolean canChange,
        Instant updatedAt,
        @Nullable String updatedBy,
        MethodUsage password,
        MethodUsage google) {

    /**
     * @param accounts     active non-Admin accounts that can use the method
     * @param onlyWay      of those, how many have no other way to sign in
     * @param openSessions open non-Admin sessions opened with the method
     */
    public record MethodUsage(long accounts, long onlyWay, long openSessions) {
    }
}
