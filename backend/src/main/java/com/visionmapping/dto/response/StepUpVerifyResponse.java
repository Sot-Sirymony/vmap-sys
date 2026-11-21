package com.visionmapping.dto.response;

import java.time.Instant;

/**
 * V20 (SV-FR-16): the code was right. windowUntil is when the session's verified
 * window ends for the settings actions, or null for an action that never uses it.
 */
public record StepUpVerifyResponse(boolean verified, Long challengeId, Instant windowUntil) {
}
