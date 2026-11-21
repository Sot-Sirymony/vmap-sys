package com.visionmapping.dto.request;

import com.visionmapping.service.report.ReportSection;
import com.visionmapping.util.Copies;
import jakarta.validation.constraints.Size;
import java.util.List;

/** V19 RM-FR-39: the sections a report's files include; null or empty means all. */
public record SavedReportSectionsRequest(@Size(max = 8) List<ReportSection> sections) {

    public SavedReportSectionsRequest {
        sections = Copies.list(sections);
    }
}
