package com.visionmapping.dto.response;

import java.time.Instant;
import java.time.LocalDate;

/** V19 RM-FR-50: one final report a participant shared with their coach, as the coach's list shows it. */
public record CoachSharedReportResponse(Long reportId, String code, String name, String scopeLabel, String periodKind,
                                        LocalDate periodFrom, LocalDate periodTo, Instant finalizedAt, Instant sharedAt) {
}
