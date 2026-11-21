package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.time.Instant;
import java.util.List;

/**
 * V19 RM-FR-47: what a reviewer sees through a share link, from the frozen
 * snapshot only. report is the Summary without the "needs attention" list or
 * the owners; its goals are empty unless GOAL_HEALTH is shared and its
 * comparison null unless CHANGES is. commentary is null unless COMMENTARY is
 * shared, and rows null unless DETAILS is. sharedBy is the participant's
 * first name.
 */
public record SharedReportResponse(String name, String code, String sharedBy, String language, Instant finalizedAt, Instant expiresAt,
                                   List<String> sections, ManagementReportResponse report, SavedReportResponse.Commentary commentary,
                                   List<ReportRowsResponse.Row> rows) {

    public SharedReportResponse {
        sections = Copies.list(sections);
        rows = Copies.list(rows);
    }
}
