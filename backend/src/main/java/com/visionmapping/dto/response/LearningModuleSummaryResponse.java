package com.visionmapping.dto.response;

import com.visionmapping.entity.enums.ModuleStage;
import com.visionmapping.entity.enums.ModuleStatus;
import java.time.Instant;

public record LearningModuleSummaryResponse(
        Integer number,
        Integer position,
        ModuleStage stage,
        String title,
        String summary,
        ModuleStatus status,
        Instant completedAt
) {
}
