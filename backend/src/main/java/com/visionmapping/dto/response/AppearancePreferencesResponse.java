package com.visionmapping.dto.response;

import com.visionmapping.entity.enums.AccentColor;
import com.visionmapping.entity.enums.BackgroundTone;
import com.visionmapping.entity.enums.FontFamily;
import com.visionmapping.entity.enums.FontSize;
import com.visionmapping.entity.enums.ThemeMode;
import com.visionmapping.entity.enums.ThemePreset;
import com.visionmapping.entity.enums.UiDensity;

/**
 * FR-39.6: the user's stored appearance, returned both from
 * {@code /api/preferences/appearance} and inside {@link AuthResponse} — the
 * latter so the correct theme is applied on first paint, with no flash of the
 * default theme while a second request is in flight.
 *
 * <p>Every field is always present: the service resolves anything missing to
 * the documented default (BR-33), so the client never has to decide what a null
 * means.
 */
public record AppearancePreferencesResponse(
        ThemePreset themePreset,
        ThemeMode themeMode,
        AccentColor themeAccent,
        UiDensity uiDensity,
        FontSize fontSize,
        FontFamily fontFamily,
        BackgroundTone backgroundTone,
        boolean highContrast,
        boolean reduceMotion
) {
}
