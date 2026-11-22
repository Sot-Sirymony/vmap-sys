package com.visionmapping.dto.response;

import java.time.Instant;

/** V21 (TF-FR-23): a remembered browser as the Security page lists it; never its token. */
public record TrustedDeviceResponse(Long id, String label, Instant createdAt, Instant lastUsedAt, Instant expiresAt) {
}
