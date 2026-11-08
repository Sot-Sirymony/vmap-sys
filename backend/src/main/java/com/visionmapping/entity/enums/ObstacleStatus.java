package com.visionmapping.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ObstacleStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    ACCEPTED;

    /**
     * Reads a status from a request body. Accepts "In Progress", "in-progress" or
     * "IN_PROGRESS"; an empty value becomes null so {@code @NotNull} reports it.
     * An unknown value is refused with HTTP 400 listing the allowed values.
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ObstacleStatus fromLabel(String label) {
        return EnumLabels.parseOrNull(ObstacleStatus.class, label);
    }
}
