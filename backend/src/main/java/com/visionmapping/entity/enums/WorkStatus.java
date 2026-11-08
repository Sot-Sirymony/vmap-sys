package com.visionmapping.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum WorkStatus {
    NOT_STARTED,
    IN_PROGRESS,
    WAITING,
    BLOCKED,
    COMPLETED,
    PAUSED;

    /**
     * Reads a status from a request body. Accepts "Not Started", "not-started" or
     * "NOT_STARTED"; an empty value becomes null so {@code @NotNull} reports it.
     * An unknown value is refused with HTTP 400 listing the allowed values.
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static WorkStatus fromLabel(String label) {
        return EnumLabels.parseOrNull(WorkStatus.class, label);
    }
}
