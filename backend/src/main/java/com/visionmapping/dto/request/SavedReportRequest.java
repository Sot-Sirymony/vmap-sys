package com.visionmapping.dto.request;

import com.visionmapping.entity.enums.Priority;
import com.visionmapping.service.report.ReportRules.Health;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import com.visionmapping.util.Copies;
import java.util.List;

/**
 * V19 RM-FR-16, RM-FR-18, RM-FR-23: a saved report's name and what it covers.
 * On a draft every field may change; on a final report only the name. A blank
 * name on create becomes "{scope} – {period}".
 */
public record SavedReportRequest(
        @Size(max = 120) String name,
        Long areaId,
        Long dreamId,
        @Size(max = 20) String period,
        LocalDate from,
        LocalDate to,
        @Size(max = 4) List<Priority> priorities,
        @Size(max = 120) String owner,
        @Size(max = 5) List<Health> healths,
        Boolean includeCompleted,
        @Pattern(regexp = "en|km") String language,
        // V19 RM-FR-32: a final report on the same scope to compare with; null for the latest one.
        Long compareToReportId
) {

    public SavedReportRequest {
        priorities = Copies.list(priorities);
        healths = Copies.list(healths);
    }
}
