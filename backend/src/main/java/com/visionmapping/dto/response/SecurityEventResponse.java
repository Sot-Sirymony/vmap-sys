package com.visionmapping.dto.response;

import java.time.Instant;

/** V10 (SEC-09-05): one row of the security log, as an admin sees it. */
public record SecurityEventResponse(
        Long id,
        Instant occurredAt,
        String eventType,
        String outcome,
        Long actorId,
        String ipAddress,
        String targetType,
        Long targetId,
        String detail
) {
}
