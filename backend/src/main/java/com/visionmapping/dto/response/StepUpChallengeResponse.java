package com.visionmapping.dto.response;

import java.time.Instant;

/**
 * V20 (SV-FR-10 to SV-FR-14, SV-FR-32): a code was sent. sentTo is masked.
 * delivery is EMAIL, LOG (local or test run, the code is in the server log) or
 * NONE (no mail server: no code, the action goes on with the password checks,
 * D-20-4), in which case challengeId, expiresAt and resendAfter are null.
 */
public record StepUpChallengeResponse(Long challengeId, String sentTo, Instant expiresAt, Instant resendAfter, String delivery) {
}
