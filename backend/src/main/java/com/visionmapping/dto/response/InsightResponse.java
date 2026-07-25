package com.visionmapping.dto.response;

import java.time.LocalDate;

/**
 * FR-36.1: one captured lesson, aggregated read-only from an existing record.
 * `source` is REVIEW or OBSTACLE and `sourceId` points back at that record so
 * the UI can link to it for editing (FR-36.3); `kind` says which field the
 * text came from (LESSON_LEARNED / ROOT_CAUSE / CREATIVE_ALTERNATIVES).
 */
public record InsightResponse(
        String source,
        String kind,
        Long sourceId,
        String sourceTitle,
        String content,
        LocalDate date
) {
}
