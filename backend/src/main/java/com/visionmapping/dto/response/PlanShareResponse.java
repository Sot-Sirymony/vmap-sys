package com.visionmapping.dto.response;

import java.time.Instant;

/**
 * One share as its owner sees it. token is set only in the answer that creates
 * the share; afterwards it is null, because only its hash is kept.
 */
public record PlanShareResponse(
        Long id,
        Long dreamId,
        String dreamTitle,
        Long partnerId,
        String partnerName,
        String message,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt,
        boolean open,
        Instant lastViewedAt,
        int viewCount,
        String token) {
}
