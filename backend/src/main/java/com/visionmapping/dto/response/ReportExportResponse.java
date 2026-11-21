package com.visionmapping.dto.response;

import java.time.Instant;

/** V19 RM-FR-25: one download of a saved report. format is xlsx, pptx or pdf. */
public record ReportExportResponse(Long id, String format, String language, Instant createdAt) {
}
