package com.visionmapping.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum CommunicationStatus {
    DRAFT,
    SENT,
    FOLLOWED_UP,
    REPLIED,
    CLOSED;

    /**
     * Reads a status from a request body. Accepts "Followed Up", "followed-up" or
     * "FOLLOWED_UP"; an empty value becomes null so {@code @NotNull} reports it.
     * An unknown value is refused with HTTP 400 listing the allowed values.
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static CommunicationStatus fromLabel(String label) {
        return EnumLabels.parseOrNull(CommunicationStatus.class, label);
    }
}
