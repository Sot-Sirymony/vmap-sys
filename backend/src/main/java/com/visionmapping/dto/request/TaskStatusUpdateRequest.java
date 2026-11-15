package com.visionmapping.dto.request;

import com.visionmapping.entity.enums.WorkStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A task status change from the board, the status menu or the Vision Map.
 * V17 BL-FR-27: {@code changeNote} answers "What changed, or what will you do
 * differently?" when the task leaves Blocked; it is optional and saved as the
 * note of that change's progress-log entry.
 */
public record TaskStatusUpdateRequest(
        @NotNull WorkStatus status,
        boolean manualOverride,
        @Size(max = 1000) String changeNote
) {
}
