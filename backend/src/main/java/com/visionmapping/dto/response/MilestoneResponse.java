package com.visionmapping.dto.response;

import java.time.LocalDate;

public record MilestoneResponse(Long id, Long dreamId, String title, LocalDate achievedOn, String note) {
}
