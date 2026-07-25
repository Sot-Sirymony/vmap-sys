package com.visionmapping.dto.request;

import com.visionmapping.entity.enums.ReportType;
import com.visionmapping.entity.enums.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * FR-38.1: what the user submits when raising a report. Status and resolution
 * are not accepted here — the server always opens a report as OPEN, and only an
 * ADMIN sets status/resolution afterwards. severity is required only for a BUG,
 * enforced in the service (BR-32). contextRoute/appVersion are auto-captured by
 * the client on submit (FR-38.2).
 */
public record IssueReportRequest(
        @NotNull ReportType reportType,
        @NotBlank @Size(max = 220) String title,
        @Size(max = 3000) String description,
        Severity severity,
        @Size(max = 300) String contextRoute,
        @Size(max = 40) String appVersion
) {
}
