package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * A status change from the status menu or the Tasks board. {@code S} is the
 * record's own status enum, so Jackson refuses an unknown value before the
 * service runs (BRD 13 EJ-FR-10, Effective Java Item 62). Each enum's
 * {@code fromLabel} still accepts "In Progress" and "in-progress".
 */
public record StatusUpdateRequest<S extends Enum<S>>(
        @NotNull S status,
        boolean manualOverride
) {
}
