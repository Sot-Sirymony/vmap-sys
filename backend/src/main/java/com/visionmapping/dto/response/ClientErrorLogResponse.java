package com.visionmapping.dto.response;

import java.time.Instant;

/** One failed browser request, as the owner and admins see it. userId is set only for admins. */
public record ClientErrorLogResponse(
        Long id,
        Instant occurredAt,
        Long userId,
        String page,
        String method,
        String path,
        int status,
        String message,
        String reference,
        String appVersion
) {
}
