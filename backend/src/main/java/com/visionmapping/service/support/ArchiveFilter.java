package com.visionmapping.service.support;

/**
 * Which records a list shows: the participant's live records only, or the
 * archived ones too. An enum rather than a {@code boolean includeArchived}
 * passed through every layer, so a call reads as what it does
 * (BRD 13 EJ-FR-13, Effective Java Item 51). Controllers keep their
 * {@code includeArchived} query parameter and convert it once with {@link #of}.
 */
public enum ArchiveFilter {
    ACTIVE_ONLY,
    INCLUDE_ARCHIVED;

    public static ArchiveFilter of(boolean includeArchived) {
        return includeArchived ? INCLUDE_ARCHIVED : ACTIVE_ONLY;
    }

    /** For repository queries that take the flag as a query parameter. */
    public boolean includesArchived() {
        return this == INCLUDE_ARCHIVED;
    }
}
