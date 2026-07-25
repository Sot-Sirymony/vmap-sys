package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * FR-38.4: an ADMIN moving a report along the lifecycle, optionally recording a
 * resolution note. A bug moved to RESOLVED must carry a note (BR-32), enforced
 * in the service.
 */
public record IssueReportStatusUpdateRequest(
        @NotBlank String status,
        @Size(max = 3000) String resolutionNote
) {
}
