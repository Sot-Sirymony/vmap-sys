package com.visionmapping.dto.request;

import com.visionmapping.entity.enums.InnerBlockerStatus;
import jakarta.validation.constraints.NotNull;

/** V34 (D-34-7, BR-34-5): change the status; stopHabit also archives the linked habit. */
public record InnerBlockerStatusRequest(@NotNull InnerBlockerStatus status, boolean stopHabit) {
}
