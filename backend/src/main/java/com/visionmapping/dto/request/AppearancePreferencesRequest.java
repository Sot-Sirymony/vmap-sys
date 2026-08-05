package com.visionmapping.dto.request;

import com.visionmapping.entity.enums.AccentColor;
import com.visionmapping.entity.enums.BackgroundTone;
import com.visionmapping.entity.enums.FontFamily;
import com.visionmapping.entity.enums.FontSize;
import com.visionmapping.entity.enums.InterfaceStyle;
import com.visionmapping.entity.enums.ThemeMode;
import com.visionmapping.entity.enums.ThemePreset;
import com.visionmapping.entity.enums.UiDensity;

/**
 * FR-39.6: the appearance choices a user saves to their account.
 *
 * <p>Every field is optional. The Appearance UI changes one control at a time,
 * so a partial body is the normal case, not an edge case — an omitted field
 * keeps whatever is already stored rather than resetting it. That also means
 * adding a future control cannot break an older client.
 *
 * <p>Fields are typed enums rather than strings deliberately (BR-33): an
 * unrecognised value is rejected at the boundary with a 400 and never reaches
 * the database. The read path is the lenient side — it resolves anything
 * missing to the documented default.
 */
public record AppearancePreferencesRequest(
        ThemePreset themePreset,
        ThemeMode themeMode,
        AccentColor themeAccent,
        UiDensity uiDensity,
        FontSize fontSize,
        FontFamily fontFamily,
        BackgroundTone backgroundTone,
        InterfaceStyle interfaceStyle,
        Boolean highContrast,
        Boolean reduceMotion
) {
}
