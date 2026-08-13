package com.visionmapping.entity.enums;

/**
 * FR-40.1: the curated background tones. Each is a coordinated set of surface
 * values — page canvas, cards, popovers, sidebar — not a single colour
 * (FR-40.2), so the depth relationship between surfaces is preserved whichever
 * one is chosen.
 *
 * <p>Curated rather than a free colour picker, deliberately (BR-35). A
 * background is what every piece of text in the product sits on, so an arbitrary
 * value would turn "no appearance choice can make text unreadable" into a
 * runtime check the user is able to fail. The actual colour values live in the
 * frontend theme layer, the single place colours are defined.
 */
public enum BackgroundTone {
    /** The values the app shipped before FR-40 — the default, and a no-op for existing users. */
    NEUTRAL,
    WARM,
    COOL,
    /** A greyer canvas, giving more separation between the page and the cards on it. */
    SOFT,
    /** Derived from the user's accent at render time (FR-40.3); stores no values of its own. */
    TINTED,
    /** No canvas step — borders carry all the separation. */
    FLAT,
    // The ten washes: same coordinated-surface construction, every value
    // contrast-measured in the frontend theme layer before being offered.
    ROSE,
    MINT,
    LAVENDER,
    SAND,
    SAGE,
    ICE,
    LINEN,
    SLATE,
    PLUM,
    STONE
}
