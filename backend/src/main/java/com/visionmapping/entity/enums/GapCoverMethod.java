package com.visionmapping.entity.enums;

/**
 * FR-82: the one way a high-relevance weakness is covered. PARTNER, DELEGATE,
 * RESOURCE and LEARN each create a linked record elsewhere; ACCEPT only keeps
 * a written reason.
 */
public enum GapCoverMethod {
    PARTNER,
    DELEGATE,
    RESOURCE,
    LEARN,
    ACCEPT
}
