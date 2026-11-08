package com.visionmapping.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum PartnerStatus {
    TO_CONTACT,
    CONTACTED,
    AGREED,
    ACTIVE,
    WAITING,
    DECLINED,
    COMPLETED;

    /**
     * Reads a status from a request body. Accepts "To Contact", "to-contact" or
     * "TO_CONTACT"; an empty value becomes null so {@code @NotNull} reports it.
     * An unknown value is refused with HTTP 400 listing the allowed values.
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static PartnerStatus fromLabel(String label) {
        return EnumLabels.parseOrNull(PartnerStatus.class, label);
    }
}
