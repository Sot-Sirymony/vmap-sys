package com.visionmapping.entity.enums;

/**
 * FR-48.1: the interface style — the shape and weight of the app's surfaces and
 * chrome, independent of its colours.
 *
 * <p>This is a separate dimension from mode, accent, background tone, and font,
 * and it composes with all of them: a style says how large the corners are, how
 * diffuse the shadows are, and which navigation chrome renders, never what
 * colour anything is. That separation is what keeps the count of hand-validated
 * colour combinations unchanged — a style adds no new hues to check.
 *
 * <p>BR-14 still holds: neither style touches the status or priority palettes.
 */
public enum InterfaceStyle {
    /**
     * The Fluent 2 treatment the app shipped with — 4px corners, thin neutral
     * strokes, tight shadows, a breadcrumb header. The default, and therefore a
     * no-op for every existing user.
     */
    CLASSIC,

    /**
     * The contemporary SaaS treatment: larger radii, soft diffused shadows,
     * gradient-washed cards, pill navigation, a sidebar search affordance and a
     * greeting header in place of breadcrumbs.
     */
    MODERN,

    /**
     * Frosted translucent panels: backdrop-blurred glass surfaces with a
     * specular top edge, capsule-leaning corners, deep floating shadows.
     * Suppressed by high contrast in the frontend — translucency is the one
     * shape treatment that can cost legibility.
     */
    LIQUID_GLASS
}
