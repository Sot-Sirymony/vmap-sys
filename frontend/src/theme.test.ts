import { describe, expect, it } from 'vitest';
import {
  accentOptions,
  buildTheme,
  matchPreset,
  priorityColor,
  priorityColors,
  statusColor,
  statusColors,
  surfaceTokens,
  themePresets,
  type AccentId,
  type PriorityToken,
  type StatusToken,
} from './theme';

/** WCAG relative luminance, so the contrast claims in theme.ts are enforced, not just asserted in comments. */
function luminance(hex: string): number {
  const channel = (value: number) => {
    const scaled = value / 255;
    return scaled <= 0.04045 ? scaled / 12.92 : Math.pow((scaled + 0.055) / 1.055, 2.4);
  };
  const int = parseInt(hex.slice(1), 16);
  return 0.2126 * channel((int >> 16) & 255) + 0.7152 * channel((int >> 8) & 255) + 0.0722 * channel(int & 255);
}

function contrast(a: string, b: string): number {
  const [lighter, darker] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (lighter + 0.05) / (darker + 0.05);
}

const ACCENT_IDS = Object.keys(accentOptions) as AccentId[];
const MODES = ['light', 'dark'] as const;

describe('accent palette (FR-39.2)', () => {
  it('offers the ten curated accents', () => {
    expect(ACCENT_IDS).toHaveLength(10);
    expect(ACCENT_IDS).toEqual(
      expect.arrayContaining(['blue', 'teal', 'purple', 'green', 'orange', 'magenta', 'red', 'brass', 'steel', 'pink']),
    );
  });

  // The promise FR-39.2 makes is that picking any accent cannot produce
  // unreadable text. That is a property of the values, so it is tested as one
  // rather than trusted — a new accent added without checking will fail here.
  it.each(ACCENT_IDS)('%s keeps its label readable in both modes', (accentId) => {
    for (const mode of MODES) {
      const set = accentOptions[accentId][mode];
      expect(contrast(set.main, set.contrastText)).toBeGreaterThanOrEqual(4.5);
      expect(contrast(set.tintForeground, set.tint)).toBeGreaterThanOrEqual(4.5);
    }
  });

  it.each(ACCENT_IDS)('%s stays readable while being hovered and pressed', (accentId) => {
    for (const mode of MODES) {
      const set = accentOptions[accentId][mode];
      // A button whose text goes illegible mid-interaction is a real failure, so
      // the transient states are held to the same bar as the resting one.
      expect(contrast(set.hover, set.contrastText)).toBeGreaterThanOrEqual(3);
      expect(contrast(set.pressed, set.contrastText)).toBeGreaterThanOrEqual(3);
    }
  });

  it('gives every accent a distinct main colour, so the swatches mean something', () => {
    for (const mode of MODES) {
      const mains = ACCENT_IDS.map((id) => accentOptions[id][mode].main);
      expect(new Set(mains).size).toBe(mains.length);
    }
  });
});

describe('theme presets (FR-39.1)', () => {
  it('is a bundle of existing knobs — every preset resolves to a real accent', () => {
    for (const preset of themePresets) {
      expect(accentOptions[preset.accent]).toBeDefined();
      expect(['light', 'dark', 'system']).toContain(preset.mode);
    }
  });

  it('recognises each preset from its own mode and accent', () => {
    for (const preset of themePresets) {
      expect(matchPreset(preset.mode, preset.accent)).toBe(preset.stored);
    }
  });

  /**
   * FR-39.1's central claim: adjust one control and the app admits it is no
   * longer the preset, rather than keeping a label that now lies.
   */
  it('reports CUSTOM for a combination no preset defines', () => {
    expect(matchPreset('light', 'pink')).toBe('CUSTOM');
    expect(matchPreset('dark', 'brass')).toBe('CUSTOM');
  });

  it('has no two presets claiming the same combination', () => {
    const combos = themePresets.map((preset) => `${preset.mode}:${preset.accent}`);
    expect(new Set(combos).size).toBe(combos.length);
  });
});

describe('high contrast (FR-39.3)', () => {
  it('raises text contrast past 7:1 in both modes', () => {
    for (const mode of MODES) {
      const normal = surfaceTokens(mode, false);
      const high = surfaceTokens(mode, true);

      expect(contrast(high.foreground, high.background)).toBeGreaterThanOrEqual(7);
      // Secondary text is the first thing to disappear for a low-vision user,
      // so it has to clear the bar too — not just the primary text.
      expect(contrast(high.mutedForeground, high.background)).toBeGreaterThanOrEqual(7);
      expect(contrast(high.foreground, high.background)).toBeGreaterThan(
        contrast(normal.foreground, normal.background),
      );
    }
  });

  it('makes borders visible structure rather than a hint', () => {
    for (const mode of MODES) {
      const high = surfaceTokens(mode, true);
      // The default #e1e1e1 on white is 1.3:1 — decoration, not a boundary.
      expect(contrast(high.border, high.background)).toBeGreaterThanOrEqual(3);
    }
  });

  it('composes with light and dark instead of replacing either', () => {
    // Same toggle, opposite modes: both get stronger, and each stays on its own
    // side of the light/dark divide.
    expect(luminance(surfaceTokens('light', true).background)).toBeGreaterThan(0.5);
    expect(luminance(surfaceTokens('dark', true).background)).toBeLessThan(0.5);
  });

  it('steps the accent along its own ramp, keeping the user’s choice recognisable', () => {
    const normal = buildTheme('light', 'teal', 'comfortable', false);
    const high = buildTheme('light', 'teal', 'comfortable', true);

    expect(high.palette.primary.main).toBe(accentOptions.teal.light.pressed);
    expect(high.palette.primary.main).not.toBe(normal.palette.primary.main);
  });
});

describe('status and priority under high contrast (BR-34)', () => {
  const STATUS_SAMPLE: StatusToken[] = ['NOT_STARTED', 'IN_PROGRESS', 'WAITING', 'BLOCKED', 'PAUSED', 'COMPLETED'];
  const PRIORITIES: PriorityToken[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

  it('leaves the normal palettes untouched', () => {
    for (const token of STATUS_SAMPLE) {
      expect(statusColor(token, 'light', false)).toBe(statusColors[token]);
      expect(statusColor(token, 'dark', false)).toBe(statusColors[token]);
    }
    for (const token of PRIORITIES) {
      expect(priorityColor(token, 'light', false)).toBe(priorityColors[token]);
    }
  });

  it('keeps every status legible on the high-contrast surface of its own mode', () => {
    for (const token of STATUS_SAMPLE) {
      expect(contrast(statusColor(token, 'light', true), '#ffffff')).toBeGreaterThanOrEqual(4.5);
      expect(contrast(statusColor(token, 'dark', true), '#000000')).toBeGreaterThanOrEqual(4.5);
    }
  });

  it('keeps every priority legible on the high-contrast surface of its own mode', () => {
    for (const token of PRIORITIES) {
      expect(contrast(priorityColor(token, 'light', true), '#ffffff')).toBeGreaterThanOrEqual(4.5);
      expect(contrast(priorityColor(token, 'dark', true), '#000000')).toBeGreaterThanOrEqual(4.5);
    }
  });

  /**
   * BR-34's actual rule. The hue is what carries the meaning, so it is the thing
   * that must survive: a Completed badge that stopped being green would make the
   * accessibility setting cost the user everything they had learned.
   */
  it.each([
    ['COMPLETED', 120],
    ['BLOCKED', 16],
    ['IN_PROGRESS', 206],
    ['WAITING', 265],
  ] as const)('preserves the hue family of %s', (token, expectedHue) => {
    for (const mode of MODES) {
      const hex = statusColor(token as StatusToken, mode, true);
      const int = parseInt(hex.slice(1), 16);
      const [r, g, b] = [((int >> 16) & 255) / 255, ((int >> 8) & 255) / 255, (int & 255) / 255];
      const max = Math.max(r, g, b);
      const min = Math.min(r, g, b);
      const delta = max - min;
      let hue: number;
      if (max === r) {
        hue = ((g - b) / delta) % 6;
      } else if (max === g) {
        hue = (b - r) / delta + 2;
      } else {
        hue = (r - g) / delta + 4;
      }
      hue = ((hue * 60) + 360) % 360;
      const drift = Math.min(Math.abs(hue - expectedHue), 360 - Math.abs(hue - expectedHue));
      expect(drift).toBeLessThanOrEqual(25);
    }
  });

  /**
   * BR-14 warns that a HIGH priority chip must never read as a BLOCKED status
   * chip. High contrast pushes hues toward the extremes, which is exactly where
   * two warm colours are most likely to collapse into one.
   */
  it('keeps the priority scale distinct from the status hues it sits next to', () => {
    for (const mode of MODES) {
      expect(priorityColor('HIGH', mode, true)).not.toBe(statusColor('BLOCKED', mode, true));
      expect(priorityColor('CRITICAL', mode, true)).not.toBe(statusColor('DECLINED', mode, true));
    }
  });

  it('falls back to a legible neutral for an enum it does not know', () => {
    const unknown = 'SOMETHING_NEW' as StatusToken;
    expect(contrast(statusColor(unknown, 'light', true), '#ffffff')).toBeGreaterThanOrEqual(4.5);
    expect(contrast(statusColor(unknown, 'dark', true), '#000000')).toBeGreaterThanOrEqual(4.5);
  });
});
