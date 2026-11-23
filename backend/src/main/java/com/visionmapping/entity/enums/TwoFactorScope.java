package com.visionmapping.entity.enums;

/** V24 (SA-FR-01, D-24-4): who must use two-step sign-in. */
public enum TwoFactorScope {
    /** Roles with any administrative permission (V21 D-21-2, V23). */
    PRIVILEGED,
    /** Every account. */
    EVERYONE
}
