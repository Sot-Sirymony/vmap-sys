package com.visionmapping.dto.response;

import com.visionmapping.entity.enums.IssueReportStatus;
import com.visionmapping.entity.enums.ReportType;
import com.visionmapping.entity.enums.Severity;
import java.time.Instant;

/**
 * A report as returned to the client. reporterName/reporterEmail let the admin
 * triage view show who raised each item; for a user's own list they are simply
 * their own details.
 */
public record IssueReportResponse(
        Long id,
        String code,
        Long reporterId,
        String reporterName,
        String reporterEmail,
        ReportType reportType,
        String title,
        String description,
        Severity severity,
        String contextRoute,
        String appVersion,
        IssueReportStatus status,
        String resolutionNote,
        boolean archived,
        Instant createdAt,
        Instant updatedAt
) {
}
