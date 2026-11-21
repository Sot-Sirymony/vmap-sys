package com.visionmapping.dto.request;

import com.visionmapping.service.report.ReportSection;
import com.visionmapping.util.Copies;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * V19 RM-FR-50: the sections the coach sees (Commentary, Goal health and What
 * changed when left out). The headline figures are always shown.
 */
public record CoachShareRequest(@Size(max = 8) List<ReportSection> sections) {

    public CoachShareRequest {
        sections = Copies.list(sections);
    }
}
