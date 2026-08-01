package com.visionmapping.entity.enums;

/**
 * FR-42: the typeface the interface renders in.
 *
 * <p>{@link #SYSTEM} is the default and is not a web font at all — it is the
 * platform's own UI face (Segoe UI Variable on Windows, SF Pro on macOS,
 * Roboto on Android). It costs no download and renders instantly, which is why
 * it stays the default rather than being replaced by whichever face looks
 * nicest in a screenshot.
 *
 * <p>The other four are self-hosted and fetched only when a user actually
 * selects one (FR-42.2). The font files ship with the app rather than coming
 * from a third-party CDN, so the app makes no external request, keeps working
 * offline, and never exposes a user's IP to a font host.
 */
public enum FontFamily {
    SYSTEM,
    PUBLIC_SANS,
    INTER,
    DM_SANS,
    NUNITO_SANS
}
