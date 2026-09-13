package com.visionmapping.dto.request;

import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.ScheduleMode;
import com.visionmapping.entity.enums.WorkStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record GoalRequest(
        @NotNull Long dreamId,
        @NotBlank @Size(max = 220) String title,
        @Size(max = 3000) String description,
        @Size(max = 3000) String successCriteria,
        @NotNull Priority priority,
        LocalDate targetDate,
        @NotNull WorkStatus status,
        boolean moonshot,
        @Size(max = 3000) String moonshotVision,
        @NotNull ScheduleMode scheduleMode
) {
}
