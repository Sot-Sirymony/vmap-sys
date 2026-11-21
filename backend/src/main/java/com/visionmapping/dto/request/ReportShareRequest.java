package com.visionmapping.dto.request;

import com.visionmapping.service.report.ReportSection;
import com.visionmapping.util.Copies;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * V19 RM-FR-46, RM-FR-48: a share link's expiry (7, 30 or 90 days; 30 when
 * left out) and the sections the reviewer sees (Commentary, Goal health and
 * What changed when left out). The headline figures are always shown.
 */
public record ReportShareRequest(Integer expiresInDays, @Size(max = 8) List<ReportSection> sections) {

    public ReportShareRequest {
        sections = Copies.list(sections);
    }
}
