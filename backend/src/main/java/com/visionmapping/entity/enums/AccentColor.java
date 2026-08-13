package com.visionmapping.entity.enums;

/**
 * FR-39.2: the curated accent choices, grown from the original five (FR-18.3)
 * to twelve. Curated rather than free-form on purpose — each option ships
 * pre-validated light *and* dark ramps in the frontend theme layer, so contrast
 * is never left to the user's judgment and no accent can produce illegible
 * text.
 *
 * <p>BR-14: an accent never touches the status or priority palettes. Choosing
 * Red as an accent must not change what a "Blocked" or "Critical" badge means.
 *
 * <p><b>This enum must list every accent the frontend offers.</b> That is not a
 * style note: an accent missing here is one the picker shows and the account
 * cannot store — the request is rejected at the boundary with a 400, and the
 * user watches their choice apply and then fail to save. FR-43 added Vermilion
 * and Violet to the theme without adding them here, and they were exactly that
 * broken for two releases. `accent-wire.test.ts` in the frontend now reads this
 * file and fails if the two lists diverge again.
 */
public enum AccentColor {
    BLUE,
    TEAL,
    PURPLE,
    GREEN,
    ORANGE,
    MAGENTA,
    RED,
    BRASS,
    STEEL,
    PINK,
    // FR-43: derived from the supplied primary and secondary ramps.
    VERMILION,
    VIOLET,
    // The royal blue from the Stitch project's DESIGN.md style guide.
    COBALT,
    // Ten more hues, spanning the wheel plus two neutrals-with-a-cast.
    INDIGO,
    SKY,
    EMERALD,
    OLIVE,
    AMBER,
    ROSE,
    FUCHSIA,
    GRAPHITE,
    COFFEE,
    NAVY
}
