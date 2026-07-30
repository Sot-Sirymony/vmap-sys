import { describe, expect, it } from 'vitest';
import { toSettings, toWire } from './appearance-mapping';
import type { ThemeSettings } from './ThemeModeContext';
import type { AppearancePreferences } from '../types/preferences';

const DEFAULTS: ThemeSettings = {
  mode: 'system',
  accent: 'blue',
  density: 'comfortable',
  fontSize: 'medium',
  highContrast: false,
  reduceMotion: false,
};

const STORED: AppearancePreferences = {
  themePreset: 'MIDNIGHT',
  themeMode: 'DARK',
  themeAccent: 'PURPLE',
  uiDensity: 'COMPACT',
  fontSize: 'LARGE',
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

  it('sends the backend’s own enum spelling', () => {
    const wire = toWire({ ...DEFAULTS, accent: 'steel', mode: 'light', fontSize: 'small', density: 'compact' });
    expect(wire.themeAccent).toBe('STEEL');
    expect(wire.themeMode).toBe('LIGHT');
    expect(wire.fontSize).toBe('SMALL');
    expect(wire.uiDensity).toBe('COMPACT');
  });
});
