package com.visionmapping.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum LifecycleStatus {
    ACTIVE,
    PAUSED,
    COMPLETED,
    ARCHIVED;

    /**
     * Reads a status from a request body. Accepts "Active", "active" or
     * "ACTIVE"; an empty value becomes null so {@code @NotNull} reports it.
     * An unknown value is refused with HTTP 400 listing the allowed values.
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static LifecycleStatus fromLabel(String label) {
        return EnumLabels.parseOrNull(LifecycleStatus.class, label);
    }
}
