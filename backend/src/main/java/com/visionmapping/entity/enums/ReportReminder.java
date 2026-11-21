package com.visionmapping.entity.enums;

/** V19 (RM-FR-49): how often to be reminded to prepare a report. */
public enum ReportReminder {
    NONE,
    /** On a chosen weekday. */
    WEEKLY,
    /** On the last working day (Monday to Friday) of the month. */
    MONTHLY
}
