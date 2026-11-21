package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.time.Instant;
import java.util.List;

/**
 * V19 RM-FR-46: a share link to a final report. token is set only in the
 * answer to creating it, and never stored; open is false once stopped or past
 * its expiry.
 */
public record ReportShareResponse(Long id, List<String> sections, Instant createdAt, Instant expiresAt, Instant revokedAt,
                                  boolean open, int viewCount, Instant lastViewedAt, String token) {

    public ReportShareResponse {
        sections = Copies.list(sections);
    }
}
