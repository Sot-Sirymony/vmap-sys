package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * V19 RM-FR-16 to RM-FR-31: a saved report. status is DRAFT or FINAL. A
 * draft's period dates are set only for CUSTOM; a final report's always.
 * verified is null for a draft, and for a final report says whether its
 * snapshot matched its checksum (RM-NFR-04). report is the Summary (live for a
 * draft, frozen for a final report), left out of the list.
 */
public record SavedReportResponse(
        Long id,
        String code,
        String name,
        String status,
        boolean archived,
        String scopeKind,
        Long areaId,
        Long dreamId,
        String scopeLabel,
        String periodKind,
        LocalDate periodFrom,
        LocalDate periodTo,
        ManagementReportResponse.Filters filters,
        Commentary commentary,
        String language,
        // The chosen comparison, or null for automatic (the latest final report on the scope); fixed once final.
        Long compareToReportId,
        // V19 RM-FR-39: the sections the files include, remembered per report.
        List<String> sections,
        Instant finalizedAt,
        Instant createdAt,
        Instant updatedAt,
        Boolean verified,
        ManagementReportResponse report
) {

    public SavedReportResponse {
        sections = Copies.list(sections);
    }

    /** RM-FR-26: the four sections, "Nothing needed" and a note per goal id. */
    public record Commentary(String highlights, String risks, String decisionsNeeded, boolean nothingNeeded, String nextFocus,
                             Map<Long, String> goalNotes) {

        public Commentary {
            goalNotes = Copies.map(goalNotes);
        }
    }
}
