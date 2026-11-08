package com.visionmapping.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * FR-38.4: the triage lifecycle of a report. OPEN → IN_REVIEW → PLANNED →
 * IN_PROGRESS → RESOLVED → CLOSED, with WONT_FIX as the terminal "declined"
 * state. Only an ADMIN moves a report between these (BR-32).
 */
public enum IssueReportStatus {
    OPEN,
    IN_REVIEW,
    PLANNED,
    IN_PROGRESS,
    RESOLVED,
    CLOSED,
    WONT_FIX;

    /**
     * Reads a status from a request body. Accepts "In Review", "in-review" or
     * "IN_REVIEW"; an empty value becomes null so {@code @NotNull} reports it.
     * An unknown value is refused with HTTP 400 listing the allowed values.
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static IssueReportStatus fromLabel(String label) {
        return EnumLabels.parseOrNull(IssueReportStatus.class, label);
    }
}
