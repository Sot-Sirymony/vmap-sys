import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { toSettings, toWire } from './appearance-mapping';
import type { ThemeSettings } from './ThemeModeContext';
import {
  accentOptions,
  backgroundTones,
  fontFamilies,
  interfaceStyles,
  themePresets,
  type AccentId,
} from '../theme';

/**
 * Every appearance choice the picker offers must be one the account can store.
 *
 * That sounds too obvious to test, which is precisely why it went wrong: FR-43
 * added the Vermilion and Violet accents to `theme.ts` and to the swatch row,
 * but not to the backend's `AccentColor` enum. Both shipped broken — the swatch
 * applied, the PUT came back 400, and the user got "applied on this device, but
 * could not be saved to your account" with no hint that the accent itself was
 * the problem. Nothing failed, because each side was internally consistent.
 *
 * So the check has to cross the boundary. This file reads the Java enums and
 * compares them against the frontend registries, which is the only place the two
 * lists are ever seen together. The same arrangement `palette-vars.test.ts` uses
 * to stop `global.css` drifting from `theme.ts`, applied one layer further out.
 *
 * The comparison is unordered on purpose: declaration order carries no meaning
 * on either side, and asserting it would fail on a harmless reshuffle.
 */
const ENUM_DIR = resolve(process.cwd(), '../backend/src/main/java/com/visionmapping/entity/enums');

/**
 * The constants declared by a Java enum.
 *
 * Only the body is read — the Javadoc above it is full of uppercase tokens
 * (`BR-14`, `FR-39.2`) that would otherwise be mistaken for constants. Line and
 * block comments inside the body are stripped for the same reason.
 */
function javaEnumConstants(name: string): string[] {
  const path = resolve(ENUM_DIR, `${name}.java`);
  // A clear failure beats silently skipping: a test that quietly passes when it
  // cannot find what it is checking is worse than no test.
  expect(existsSync(path), `${name}.java not found — is the backend checked out?`).toBe(true);

  const source = readFileSync(path, 'utf8');
  const body = new RegExp(`enum\\s+${name}\\s*\\{([\\s\\S]*)\\}`).exec(source);
  expect(body, `could not parse the body of ${name}.java`).not.toBeNull();

  const withoutComments = body![1].replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/[^\n]*/g, '');
  return [...withoutComments.matchAll(/\b[A-Z][A-Z0-9_]*\b/g)].map((match) => match[0]);
}

/** Compares two lists as sets, reporting what is missing from each side. */
function expectSameSet(frontend: string[], backend: string[], label: string) {
  const missingFromBackend = frontend.filter((value) => !backend.includes(value));
  const missingFromFrontend = backend.filter((value) => !frontend.includes(value));

  expect(
    missingFromBackend,
    `${label}: the frontend offers ${missingFromBackend.join(', ')}, which the backend cannot store — saving it would 400`,
  ).toEqual([]);
  expect(
    missingFromFrontend,
    `${label}: the backend accepts ${missingFromFrontend.join(', ')}, which the frontend never offers`,
  ).toEqual([]);
}

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

describe('appearance options match the wire format', () => {
  it('offers exactly the accents the backend enum can store', () => {
    expectSameSet(
      Object.keys(accentOptions).map((id) => id.toUpperCase()),
      javaEnumConstants('AccentColor'),
      'AccentColor',
    );
  });

  it('offers exactly the background tones the backend enum can store', () => {
    expectSameSet(
      backgroundTones.map((tone) => tone.stored),
      javaEnumConstants('BackgroundTone'),
      'BackgroundTone',
    );
  });

  it('offers exactly the fonts the backend enum can store', () => {
    expectSameSet(
      fontFamilies.map((font) => font.stored),
      javaEnumConstants('FontFamily'),
      'FontFamily',
    );
  });

  it('offers exactly the interface styles the backend enum can store', () => {
    expectSameSet(
      interfaceStyles.map((style) => style.stored),
      javaEnumConstants('InterfaceStyle'),
      'InterfaceStyle',
    );
  });

  /**
   * CUSTOM is the one value on the backend side with no frontend entry, and
   * that is by design: it is computed from a mode/accent pair matching no
   * preset, never selected. So it is added here rather than being allowed
   * through as a general exception.
   */
  it('offers exactly the presets the backend enum can store, plus computed CUSTOM', () => {
    expectSameSet(
      [...themePresets.map((preset) => preset.stored), 'CUSTOM'],
      javaEnumConstants('ThemePreset'),
      'ThemePreset',
    );
  });

  /**
   * The other half of the same failure: a translation that silently drops an
   * accent would also produce a request the backend rejects. Every accent has to
   * survive the round trip as itself.
   */
  it('round-trips every accent through the wire format', () => {
    for (const accent of Object.keys(accentOptions) as AccentId[]) {
      const wire = toWire({ ...DEFAULTS, accent });
      expect(wire.themeAccent, `${accent} lost its wire name`).toBe(accent.toUpperCase());
      expect(toSettings(wire, DEFAULTS).accent, `${accent} did not survive the round trip`).toBe(accent);
    }
  });
});
