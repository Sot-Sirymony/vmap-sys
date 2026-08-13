package com.visionmapping.entity.enums;

/**
 * FR-39.1: a named theme preset. A preset is a bundle of two knobs the user
 * could equally set by hand — {@link ThemeMode} and {@link AccentColor} — never
 * a hidden extra dimension. That keeps one source of truth for what actually
 * renders: applying a preset writes the same two values a manual pick would.
 *
 * <p>{@link #CUSTOM} is the state a preset falls into once the user adjusts an
 * individual control, so the UI never mislabels a hand-tuned look as a preset.
 * It is a result, not something a user selects.
 *
 * <p>The mode/accent pair each preset stands for lives in the frontend theme
 * layer (the single place colours are defined); the backend only stores which
 * label was last applied.
 */
public enum ThemePreset {
    /** System mode + Blue — the app's default since FR-18. */
    FLUENT_SYSTEM,
    FLUENT_LIGHT,
    FLUENT_DARK,
    OCEAN,
    FOREST,
    SLATE,
    MIDNIGHT,
    /** Light mode + Cobalt — the Stitch project's DESIGN.md style guide. */
    STITCH,
    CUSTOM
}
