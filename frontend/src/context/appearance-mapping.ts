import { accentOptions, backgroundTones, fontFamilies, fontFamilyFromStored, interfaceStyleFromStored, interfaceStyles, matchPreset, toneFromStored, type AccentId, type BackgroundToneId, type Density, type FontFamilyId, type InterfaceStyleId } from '../theme';
import type {
  AppearancePreferences,
  StoredAccent,
  StoredDensity,
  StoredFontSize,
  StoredThemeMode,
} from '../types/preferences';
import type { FontSize, ThemeMode, ThemeSettings } from './ThemeModeContext';

/**
 * FR-39.6 — translation between the backend's enum names and the frontend's
 * setting values, in one place.
 *
 * Both directions are defensive on purpose. `toSettings` is fed by the network,
 * so anything it does not recognise falls back to the default rather than
 * propagating an invalid value into the theme (BR-33) — a backend a version
 * ahead, sending an accent this build has never heard of, must not blank the UI.
 */

const MODE_TO_WIRE: Record<ThemeMode, StoredThemeMode> = {
  light: 'LIGHT',
  dark: 'DARK',
  system: 'SYSTEM',
};

const MODE_FROM_WIRE: Record<StoredThemeMode, ThemeMode> = {
  LIGHT: 'light',
  DARK: 'dark',
  SYSTEM: 'system',
};

const DENSITY_TO_WIRE: Record<Density, StoredDensity> = {
  comfortable: 'COMFORTABLE',
  compact: 'COMPACT',
};

const FONT_SIZE_TO_WIRE: Record<FontSize, StoredFontSize> = {
  small: 'SMALL',
  medium: 'MEDIUM',
  large: 'LARGE',
};

const FONT_SIZE_FROM_WIRE: Record<StoredFontSize, FontSize> = {
  SMALL: 'small',
  MEDIUM: 'medium',
  LARGE: 'large',
};

/**
 * Accent ids are the same words in a different case, so the map is derived from
 * `accentOptions` rather than written out — a new accent added to the theme is
 * then automatically translatable, instead of silently missing here.
 */
function accentFromWire(wire: string): AccentId | null {
  const candidate = wire.toLowerCase();
  return candidate in accentOptions ? (candidate as AccentId) : null;
}

function accentToWire(accent: AccentId): StoredAccent {
  return accent.toUpperCase() as StoredAccent;
}

/**
 * FR-40: tone ids are the same words in a different case, so the pair below is
 * derived from `backgroundTones` rather than written out — a tone added to the
 * theme is then translatable automatically instead of silently missing here.
 */
function toneFromWire(wire: string, fallback: BackgroundToneId): BackgroundToneId {
  return backgroundTones.some((tone) => tone.stored === wire) ? toneFromStored(wire) : fallback;
}

function toneToWire(tone: BackgroundToneId): AppearancePreferences['backgroundTone'] {
  return backgroundTones.find((entry) => entry.id === tone)?.stored ?? 'NEUTRAL';
}

/** FR-42: same derive-from-the-registry approach as the tones. */
function fontFromWire(wire: string, fallback: FontFamilyId): FontFamilyId {
  return fontFamilies.some((font) => font.stored === wire) ? fontFamilyFromStored(wire) : fallback;
}

function fontToWire(font: FontFamilyId): AppearancePreferences['fontFamily'] {
  return fontFamilies.find((entry) => entry.id === font)?.stored ?? 'SYSTEM';
}

/** FR-48: same derive-from-the-registry approach as the tones and fonts. */
function styleFromWire(wire: string, fallback: InterfaceStyleId): InterfaceStyleId {
  return interfaceStyles.some((style) => style.stored === wire) ? interfaceStyleFromStored(wire) : fallback;
}

function styleToWire(style: InterfaceStyleId): AppearancePreferences['interfaceStyle'] {
  return interfaceStyles.find((entry) => entry.id === style)?.stored ?? 'CLASSIC';
}

/** Wire → frontend settings, with every unrecognised value replaced by `fallback`'s. */
export function toSettings(wire: AppearancePreferences, fallback: ThemeSettings): ThemeSettings {
  return {
    mode: MODE_FROM_WIRE[wire.themeMode] ?? fallback.mode,
    accent: accentFromWire(wire.themeAccent) ?? fallback.accent,
    density: wire.uiDensity === 'COMPACT' ? 'compact' : 'comfortable',
    fontSize: FONT_SIZE_FROM_WIRE[wire.fontSize] ?? fallback.fontSize,
    backgroundTone: toneFromWire(wire.backgroundTone, fallback.backgroundTone),
    fontFamily: fontFromWire(wire.fontFamily, fallback.fontFamily),
    interfaceStyle: styleFromWire(wire.interfaceStyle, fallback.interfaceStyle),
    highContrast: Boolean(wire.highContrast),
    reduceMotion: Boolean(wire.reduceMotion),
  };
}

/**
 * Frontend settings → wire.
 *
 * `themePreset` is derived from the mode/accent pair rather than tracked
 * separately (FR-39.1). Storing a remembered label would let it drift out of
 * step with what is actually on screen; deriving it means "Custom" is simply
 * what a pair that matches no preset is called.
 */
export function toWire(settings: ThemeSettings): AppearancePreferences {
  return {
    themePreset: matchPreset(settings.mode, settings.accent),
    themeMode: MODE_TO_WIRE[settings.mode],
    themeAccent: accentToWire(settings.accent),
    uiDensity: DENSITY_TO_WIRE[settings.density],
    fontSize: FONT_SIZE_TO_WIRE[settings.fontSize],
    backgroundTone: toneToWire(settings.backgroundTone),
    fontFamily: fontToWire(settings.fontFamily),
    interfaceStyle: styleToWire(settings.interfaceStyle),
    highContrast: settings.highContrast,
    reduceMotion: settings.reduceMotion,
  };
}
