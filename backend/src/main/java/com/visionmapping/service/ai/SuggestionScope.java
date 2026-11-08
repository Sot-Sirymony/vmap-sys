package com.visionmapping.service.ai;

/**
 * How bold the dream and goal builders should be: the usual set of
 * suggestions, or exactly one stretch (moonshot) idea (AI-FR-10). An enum
 * rather than a {@code boolean stretch} (BRD 13 EJ-FR-13, Effective Java Item 51).
 */
public enum SuggestionScope {
    REGULAR,
    STRETCH;

    public static SuggestionScope of(boolean stretch) {
        return stretch ? STRETCH : REGULAR;
    }

    public boolean isStretch() {
        return this == STRETCH;
    }
}
