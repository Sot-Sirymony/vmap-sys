package com.visionmapping.entity.enums;

/**
 * BRD 26 D-26-7: where an account's interface style came from, so a deliberate
 * choice is tracked on the account rather than per browser.
 */
public enum InterfaceStyleSource {
    /** Never picked in Appearance; the account holds the default of its time. */
    DEFAULT,

    /** Picked by the user in Appearance. */
    CHOSEN
}
