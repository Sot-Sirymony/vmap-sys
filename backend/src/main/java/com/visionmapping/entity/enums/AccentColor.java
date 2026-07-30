package com.visionmapping.entity.enums;

/**
 * FR-39.2: the curated accent choices, grown from the original five (FR-18.3)
 * to ten. Curated rather than free-form on purpose — each option ships
 * pre-validated light *and* dark ramps in the frontend theme layer, so contrast
 * is never left to the user's judgment and no accent can produce illegible
 * text.
 *
 * <p>BR-14: an accent never touches the status or priority palettes. Choosing
 * Red as an accent must not change what a "Blocked" or "Critical" badge means.
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
    PINK
}
