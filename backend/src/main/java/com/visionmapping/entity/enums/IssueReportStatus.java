package com.visionmapping.entity.enums;

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
    WONT_FIX
}
