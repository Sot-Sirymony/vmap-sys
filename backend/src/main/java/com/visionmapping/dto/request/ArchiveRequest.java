package com.visionmapping.dto.request;

import jakarta.validation.constraints.Size;

/**
 * V29 (AR-FR-04, BR-29-1): an optional reason for archiving a dream, goal,
 * step or task. removePartner applies to steps only (BL-FR-21).
 */
public record ArchiveRequest(
        @Size(max = 1000) String reason,
        Boolean removePartner
) {
}
