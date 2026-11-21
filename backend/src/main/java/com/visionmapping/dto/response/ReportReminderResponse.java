package com.visionmapping.dto.response;

import java.time.LocalDate;

/**
 * V19 (RM-FR-49): the in-app report reminder. due is true from the reminder
 * day for a week, until a report is finalised on or after it. latestFinal is
 * the report to start the next one from (New report from this), or null.
 */
public record ReportReminderResponse(boolean due, String cadence, LocalDate dueOn, Long latestFinalId, String latestFinalName) {
}
