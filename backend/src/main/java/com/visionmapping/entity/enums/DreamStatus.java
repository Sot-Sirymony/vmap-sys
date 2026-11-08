package com.visionmapping.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum DreamStatus {
    IDEA,
    ACTIVE,
    PAUSED,
    COMPLETED,
    ARCHIVED;

    /**
     * Reads a status from a request body. Accepts "Idea", "idea" or
     * "IDEA"; an empty value becomes null so {@code @NotNull} reports it.
     * An unknown value is refused with HTTP 400 listing the allowed values.
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static DreamStatus fromLabel(String label) {
        return EnumLabels.parseOrNull(DreamStatus.class, label);
    }
}
