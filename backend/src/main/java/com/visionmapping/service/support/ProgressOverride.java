package com.visionmapping.service.support;

/**
 * Whether a goal or step status change is a manual override. {@code MANUAL}
 * lets a goal be marked Completed before all its steps are (CLAUDE.md Core
 * Business Rule 8) and keeps the progress the participant set from being
 * recalculated. An enum rather than a {@code boolean manualOverride}
 * (BRD 13 EJ-FR-13, Effective Java Item 51).
 */
public enum ProgressOverride {
    NONE,
    MANUAL;

    public static ProgressOverride of(boolean manualOverride) {
        return manualOverride ? MANUAL : NONE;
    }

    public boolean isManual() {
        return this == MANUAL;
    }
}
