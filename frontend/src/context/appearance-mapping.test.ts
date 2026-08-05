import { describe, expect, it } from 'vitest';
import { toSettings, toWire } from './appearance-mapping';
import type { ThemeSettings } from './ThemeModeContext';
import type { AppearancePreferences } from '../types/preferences';

const DEFAULTS: ThemeSettings = {
  mode: 'system',
  accent: 'blue',
  density: 'comfortable',
  fontSize: 'medium',
  backgroundTone: 'neutral',
  fontFamily: 'system',
  interfaceStyle: 'classic',
  highContrast: false,
  reduceMotion: false,
};

const STORED: AppearancePreferences = {
  themePreset: 'MIDNIGHT',
  themeMode: 'DARK',
  themeAccent: 'PURPLE',
  uiDensity: 'COMPACT',
  fontSize: 'LARGE',
  backgroundTone: 'WARM',
  fontFamily: 'INTER',
  interfaceStyle: 'MODERN',
  highContrast: true,
  reduceMotion: true,
};

describe('appearance mapping (FR-39.6)', () => {
  it('translates every stored field into a setting', () => {
    expect(toSettings(STORED, DEFAULTS)).toEqual({
      mode: 'dark',
      accent: 'purple',
      density: 'compact',
      fontSize: 'large',
      backgroundTone: 'warm',
      fontFamily: 'inter',
      interfaceStyle: 'modern',
      highContrast: true,
      reduceMotion: true,
    });
  });

  it('survives a round trip without drift', () => {
    const settings = toSettings(STORED, DEFAULTS);
    expect(toSettings(toWire(settings), DEFAULTS)).toEqual(settings);
  });

  it('derives the preset from the mode and accent rather than echoing the stored label', () => {
    // The stored label said MIDNIGHT, but these values are Ocean. What is on
    // screen wins — a remembered label that has gone stale must not be trusted.
    const settings = toSettings({ ...STORED, themeMode: 'LIGHT', themeAccent: 'TEAL' }, DEFAULTS);
    expect(toWire(settings).themePreset).toBe('OCEAN');
  });

  it('labels a combination matching no preset as CUSTOM', () => {
    const settings = toSettings({ ...STORED, themeMode: 'LIGHT', themeAccent: 'PINK' }, DEFAULTS);
    expect(toWire(settings).themePreset).toBe('CUSTOM');
  });

  /**
   * BR-33: the network is not trusted. A backend one version ahead can send an
   * accent this build has never heard of, and the answer is to fall back — not to
   * put an unknown value into the theme and render something broken.
   */
  it('falls back to the default for an accent it does not know', () => {
    const settings = toSettings({ ...STORED, themeAccent: 'CHARTREUSE' as never }, DEFAULTS);
    expect(settings.accent).toBe(DEFAULTS.accent);
    // The fields it *could* read are still applied — one bad value doesn't
    // discard the rest of the user's saved appearance.
    expect(settings.mode).toBe('dark');
    expect(settings.fontSize).toBe('large');
  });

  it('falls back to the default for an unknown mode or font size', () => {
    const settings = toSettings(
      { ...STORED, themeMode: 'SEPIA' as never, fontSize: 'ENORMOUS' as never },
      DEFAULTS,
    );
    expect(settings.mode).toBe(DEFAULTS.mode);
    expect(settings.fontSize).toBe(DEFAULTS.fontSize);
  });

  it('treats a missing boolean as off rather than undefined', () => {
    const settings = toSettings(
      { ...STORED, highContrast: undefined as never, reduceMotion: undefined as never },
      DEFAULTS,
    );
    expect(settings.highContrast).toBe(false);
    expect(settings.reduceMotion).toBe(false);
  });

  /** FR-40: an unknown tone falls back rather than reaching the theme (BR-33). */
  it('falls back to the default for a tone it does not know', () => {
    const settings = toSettings({ ...STORED, backgroundTone: 'CHARCOAL' as never }, DEFAULTS);
    expect(settings.backgroundTone).toBe(DEFAULTS.backgroundTone);
  });

  it('round-trips every tone through the wire format', () => {
    for (const stored of ['NEUTRAL', 'WARM', 'COOL', 'SOFT', 'TINTED', 'FLAT'] as const) {
      const settings = toSettings({ ...STORED, backgroundTone: stored }, DEFAULTS);
      expect(toWire(settings).backgroundTone).toBe(stored);
    }
  });

  /** FR-42: an unknown font falls back rather than reaching the theme (BR-33). */
  it('falls back to the default for a font it does not know', () => {
    const settings = toSettings({ ...STORED, fontFamily: 'COMIC_SANS' as never }, DEFAULTS);
    expect(settings.fontFamily).toBe(DEFAULTS.fontFamily);
  });

  it('round-trips every font through the wire format', () => {
    for (const stored of ['SYSTEM', 'PUBLIC_SANS', 'INTER', 'DM_SANS', 'NUNITO_SANS'] as const) {
      const settings = toSettings({ ...STORED, fontFamily: stored }, DEFAULTS);
      expect(toWire(settings).fontFamily).toBe(stored);
    }
  });

  /** FR-48: an unknown style falls back rather than reaching the theme (BR-33). */
  it('falls back to the default for an interface style it does not know', () => {
    const settings = toSettings({ ...STORED, interfaceStyle: 'BRUTALIST' as never }, DEFAULTS);
    expect(settings.interfaceStyle).toBe(DEFAULTS.interfaceStyle);
  });

  it('round-trips every interface style through the wire format', () => {
    for (const stored of ['CLASSIC', 'MODERN'] as const) {
      const settings = toSettings({ ...STORED, interfaceStyle: stored }, DEFAULTS);
      expect(toWire(settings).interfaceStyle).toBe(stored);
    }
  });

  it('sends the backend’s own enum spelling', () => {
    const wire = toWire({ ...DEFAULTS, accent: 'steel', mode: 'light', fontSize: 'small', density: 'compact' });
    expect(wire.themeAccent).toBe('STEEL');
    expect(wire.themeMode).toBe('LIGHT');
    expect(wire.fontSize).toBe('SMALL');
    expect(wire.uiDensity).toBe('COMPACT');
  });
});
