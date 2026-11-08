package com.visionmapping.dto.request;

import com.visionmapping.entity.enums.IssueReportStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * FR-38.4: an ADMIN moving a report along the lifecycle, optionally recording a
 * resolution note. A bug moved to RESOLVED must carry a note (BR-32), enforced
 * in the service.
 */
public record IssueReportStatusUpdateRequest(
        @NotNull IssueReportStatus status,
        @Size(max = 3000) String resolutionNote
) {
}
