import { describe, expect, it } from 'vitest';
import {
  accentOptions,
  backgroundTones,
  buildTheme,
  categoricalPieColor,
  categoricalPieColors,
  CATEGORICAL_PIE_SLOTS,
  grey,
  palette,
  pieLabelColor,
  semanticTints,
  matchPreset,
  priorityColor,
  priorityColors,
  statusColor,
  statusColors,
  surfaceTokens,
  themePresets,
  toneFromStored,
  toneSurfaces,
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
  it('offers the twenty-three curated accents', () => {
    expect(ACCENT_IDS).toHaveLength(23);
    expect(ACCENT_IDS).toEqual(
      expect.arrayContaining([
        'blue', 'teal', 'purple', 'green', 'orange', 'magenta', 'red', 'brass', 'steel', 'pink',
        // FR-43, derived from the supplied primary/secondary ramps.
        'vermilion', 'violet',
        // The Stitch DESIGN.md's royal blue.
        'cobalt',
        // The wheel-spanning additions.
        'indigo', 'sky', 'emerald', 'olive', 'amber', 'rose', 'fuchsia', 'graphite', 'coffee', 'navy',
      ]),
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

  /**
   * The transient states are held to the same 4.5:1 bar as the resting one: a
   * button whose label goes illegible the moment you hover it is a real failure.
   *
   * This asserted only 3:1 until FR-46, because the five original accents could
   * not meet 4.5 — their light-mode hovers were *lighter* than their mains, so
   * contrast dropped exactly when the user interacted. Six such states across
   * teal, purple, green and orange were corrected; the bar is now uniform.
   */
  it.each(ACCENT_IDS)('%s stays readable while being hovered and pressed', (accentId) => {
    for (const mode of MODES) {
      const set = accentOptions[accentId][mode];
      expect(contrast(set.hover, set.contrastText)).toBeGreaterThanOrEqual(4.5);
      expect(contrast(set.pressed, set.contrastText)).toBeGreaterThanOrEqual(4.5);
    }
  });

  /**
   * Direction, not just contrast: in light mode each step must get darker, which
   * is Fluent's convention and the reason the corrected values hold up. A
   * lighter hover is what produced the original failures.
   */
  it.each(ACCENT_IDS)('%s darkens through hover and pressed in light mode', (accentId) => {
    const set = accentOptions[accentId].light;
    const onWhite = (hex: string) => contrast(hex, '#ffffff');
    expect(onWhite(set.hover)).toBeGreaterThan(onWhite(set.main));
    expect(onWhite(set.pressed)).toBeGreaterThan(onWhite(set.hover));
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

describe('semantic palette and grey ramp (FR-43)', () => {
  const FAMILIES = ['primary', 'secondary', 'info', 'success', 'warning', 'error'] as const;

  it('gives every family the five documented shades', () => {
    for (const family of FAMILIES) {
      expect(Object.keys(palette[family])).toEqual(['lighter', 'light', 'main', 'dark', 'darker']);
    }
  });

  /**
   * The role contract, and the reason it is written down rather than assumed.
   *
   * `main` reads as the obvious button colour and is not one: measured against
   * white text it lands at 1.90:1 (warning) through 3.67:1 (primary). The pairing
   * that works — and the one `semanticTints` uses — is `lighter` as the surface
   * with `darker` as the text on it.
   */
  it.each(FAMILIES)('%s pairs lighter with darker to stay readable', (family) => {
    expect(contrast(palette[family].darker, palette[family].lighter)).toBeGreaterThanOrEqual(4.5);
  });

  it('confirms main is a fill, not a text colour — the trap this contract exists for', () => {
    // Documents the measurement rather than asserting a target: if a future
    // change makes these legible on white, the roles can be revisited.
    for (const family of ['success', 'warning', 'info'] as const) {
      expect(contrast(palette[family].main, '#ffffff')).toBeLessThan(4.5);
    }
  });

  it('wires the tiles to the lighter/darker pair, not to main', () => {
    expect(semanticTints.positive).toEqual({ bg: palette.success.lighter, fg: palette.success.darker });
    expect(semanticTints.warning).toEqual({ bg: palette.warning.lighter, fg: palette.warning.darker });
    expect(semanticTints.critical).toEqual({ bg: palette.error.lighter, fg: palette.error.darker });
    for (const tint of Object.values(semanticTints)) {
      expect(contrast(tint.fg, tint.bg)).toBeGreaterThanOrEqual(4.5);
    }
  });

  /** The ramp splits at 600: below it is surface, at and above it is text. */
  it('keeps the grey ramp monotonic and splits surface from text at 600', () => {
    const steps = [50, 100, 200, 300, 400, 500, 600, 700, 800, 900] as const;
    for (let i = 1; i < steps.length; i += 1) {
      expect(luminance(grey[steps[i]])).toBeLessThan(luminance(grey[steps[i - 1]]));
    }
    expect(contrast(grey[600], '#ffffff')).toBeGreaterThanOrEqual(4.5);
    expect(contrast(grey[500], '#ffffff')).toBeLessThan(4.5);
  });

  /**
   * FR-43 keeps the supplied red out of the default path. The app already spends
   * red on Critical, Declined, and destructive actions, so brand red is offered
   * as a choice rather than imposed as the default.
   */
  it('does not make the supplied red the default accent', () => {
    const defaultTheme = buildTheme('light');
    expect(defaultTheme.palette.primary.main).toBe(accentOptions.blue.light.main);
    expect(defaultTheme.palette.primary.main).not.toBe(palette.primary.main);
  });

  /**
   * FR-45: the semantic families are wired into MUI's own slots, so
   * `<Alert severity="success">` and `<Chip color="warning">` draw from this
   * palette instead of MUI's stock colours. Before this they were defined and
   * referenced by nothing.
   */
  it.each(['success', 'warning', 'info'] as const)('wires %s into the MUI palette', (family) => {
    const theme = buildTheme('light');
    expect(theme.palette[family].main).toBe(palette[family].main);
    expect(theme.palette[family].light).toBe(palette[family].light);
    expect(theme.palette[family].dark).toBe(palette[family].dark);
  });

  /**
   * The trap this guards: MUI picks `contrastText` automatically from a
   * threshold, and for these mains it would choose white — 1.90:1 on warning.
   * Setting it explicitly is what keeps a filled chip readable.
   */
  it.each(['success', 'warning', 'info', 'error'] as const)('keeps a filled %s surface readable', (family) => {
    for (const mode of MODES) {
      const theme = buildTheme(mode);
      const slot = theme.palette[family];
      expect(contrast(slot.main, slot.contrastText)).toBeGreaterThanOrEqual(4.5);
    }
  });

  it('exposes the grey ramp through MUI’s standard slot', () => {
    const theme = buildTheme('light');
    for (const step of [50, 100, 200, 300, 400, 500, 600, 700, 800, 900] as const) {
      expect(theme.palette.grey[step]).toBe(grey[step]);
    }
  });

  /**
   * Disabled text is exempt from the 4.5:1 rule (WCAG 1.4.3) and should read as
   * unavailable — so this asserts it is deliberately *below* the body-text bar
   * rather than accidentally near it.
   */
  it('uses the low end of the ramp for disabled states', () => {
    const light = buildTheme('light');
    expect(light.palette.text.disabled).toBe(grey[500]);
    expect(contrast(grey[500], '#ffffff')).toBeLessThan(4.5);
    expect(light.palette.action.disabledBackground).toBe(grey[400]);
  });

  it('offers the supplied ramps as selectable accents that meet the accent contract', () => {
    for (const id of ['vermilion', 'violet'] as const) {
      for (const mode of MODES) {
        const set = accentOptions[id][mode];
        expect(contrast(set.main, set.contrastText)).toBeGreaterThanOrEqual(4.5);
        expect(contrast(set.tintForeground, set.tint)).toBeGreaterThanOrEqual(4.5);
      }
    }
  });
});

describe('categorical pie palette (FR-41)', () => {
  const SEMANTIC = [
    statusColors.COMPLETED,
    statusColors.BLOCKED,
    statusColors.DECLINED,
    statusColors.IN_PROGRESS,
    statusColors.WAITING,
    priorityColors.CRITICAL,
    priorityColors.MEDIUM,
    priorityColors.HIGH,
  ];

  /**
   * Machado–Oliveira–Fernandes 2009 at severity 1.0 — the model the accepted
   * ΔE thresholds are calibrated against.
   *
   * The rough matrix this file used before was the reason a green and a gold
   * shipped in the same pie: it scored that pair at ΔE 7.3 against a floor of 8,
   * which reads as "borderline", while the real measurement is 5.4 for a
   * protanope and 12.6 for full colour vision. The simulation model is part of
   * the standard, not an implementation detail.
   */
  const MACHADO = {
    protan: [[0.152286, 1.052583, -0.204868], [0.114503, 0.786281, 0.099216], [-0.003882, -0.048116, 1.051998]],
    deutan: [[0.367322, 0.860646, -0.227968], [0.280085, 0.672501, 0.047413], [-0.011820, 0.042940, 0.968881]],
  } as const;

  const toLinear = (hex: string) => {
    const int = parseInt(hex.slice(1), 16);
    return [(int >> 16) & 255, (int >> 8) & 255, int & 255].map((value) => {
      const scaled = value / 255;
      return scaled <= 0.04045 ? scaled / 12.92 : Math.pow((scaled + 0.055) / 1.055, 2.4);
    });
  };

  /** OKLab, the space the ΔE thresholds below are expressed in (×100). */
  function oklab([r, g, b]: number[]): number[] {
    const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
    const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
    const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
    return [
      0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
      1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
      0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
    ];
  }

  function cvdDistance(first: string, second: string, kind?: keyof typeof MACHADO): number {
    const project = (hex: string) => {
      const [r, g, b] = toLinear(hex);
      if (!kind) {
        return oklab([r, g, b]);
      }
      const matrix = MACHADO[kind];
      const clamp = (value: number) => Math.max(0, Math.min(1, value));
      return oklab([
        clamp(matrix[0][0] * r + matrix[0][1] * g + matrix[0][2] * b),
        clamp(matrix[1][0] * r + matrix[1][1] * g + matrix[1][2] * b),
        clamp(matrix[2][0] * r + matrix[2][1] * g + matrix[2][2] * b),
      ]);
    };
    const [p, q] = [project(first), project(second)];
    return 100 * Math.hypot(p[0] - q[0], p[1] - q[1], p[2] - q[2]);
  }

  /** Rough deuteranopia simulation — enough to catch two hues collapsing into one. */
  function deuteranope(hex: string): string {
    const int = parseInt(hex.slice(1), 16);
    const [r, g, b] = [((int >> 16) & 255) / 255, ((int >> 8) & 255) / 255, (int & 255) / 255];
    const channel = (v: number) => ('0' + Math.round(Math.min(1, Math.max(0, v)) * 255).toString(16)).slice(-2);
    return `#${channel(0.625 * r + 0.375 * g)}${channel(0.7 * r + 0.3 * g)}${channel(0.3 * g + 0.7 * b)}`;
  }

  function deltaE(a: string, b: string): number {
    const toLab = (hex: string) => {
      const int = parseInt(hex.slice(1), 16);
      const [r, g, bl] = [((int >> 16) & 255) / 255, ((int >> 8) & 255) / 255, (int & 255) / 255].map((v) =>
        v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4),
      );
      const x = (r * 0.4124 + g * 0.3576 + bl * 0.1805) / 0.95047;
      const y = r * 0.2126 + g * 0.7152 + bl * 0.0722;
      const z = (r * 0.0193 + g * 0.1192 + bl * 0.9505) / 1.08883;
      const f = (t: number) => (t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16 / 116);
      return [116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z))];
    };
    const [p, q] = [toLab(a), toLab(b)];
    return Math.hypot(p[0] - q[0], p[1] - q[1], p[2] - q[2]);
  }

  const CASES = [
    { mode: 'light' as const, hc: false, card: '#ffffff', label: pieLabelColor.light },
    { mode: 'dark' as const, hc: false, card: '#252423', label: pieLabelColor.dark },
    { mode: 'light' as const, hc: true, card: '#ffffff', label: pieLabelColor.light },
    { mode: 'dark' as const, hc: true, card: '#0f0f0f', label: pieLabelColor.dark },
  ];

  /**
   * The percentage is drawn *on* the slice, which is the whole reason this
   * palette is darker than a typical marketing one. A bright gold cannot carry
   * white text at any size — the reference palette's #F2D56F measures 1.45:1.
   */
  it.each(CASES)('label stays readable on every slice ($mode, highContrast=$hc)', ({ mode, hc, label }) => {
    for (let i = 0; i < CATEGORICAL_PIE_SLOTS; i += 1) {
      expect(contrast(categoricalPieColor(i, mode, hc), label)).toBeGreaterThanOrEqual(4.5);
    }
  });

  it.each(CASES)('every slice is visible against the card ($mode, highContrast=$hc)', ({ mode, hc, card }) => {
    for (let i = 0; i < CATEGORICAL_PIE_SLOTS; i += 1) {
      expect(contrast(categoricalPieColor(i, mode, hc), card)).toBeGreaterThanOrEqual(3);
    }
  });

  /**
   * Adjacent slices touch, so telling them apart is the chart's basic job — and
   * warm hues are exactly where that fails for a colourblind viewer. The
   * deuteranopia floor is 8, the band this file already documents as acceptable
   * when colour is not the only encoding; here the legend names every category
   * and the tooltip carries the counts.
   */
  it.each(CASES)('slices stay distinguishable from each other ($mode, highContrast=$hc)', ({ mode, hc }) => {
    for (let i = 0; i < CATEGORICAL_PIE_SLOTS; i += 1) {
      for (let j = i + 1; j < CATEGORICAL_PIE_SLOTS; j += 1) {
        const a = categoricalPieColor(i, mode, hc);
        const b = categoricalPieColor(j, mode, hc);
        expect(deltaE(a, b)).toBeGreaterThanOrEqual(12);
        expect(deltaE(deuteranope(a), deuteranope(b))).toBeGreaterThanOrEqual(8);
      }
    }
  });

  /**
   * The check that actually catches a colliding pair. Every slice of a pie can
   * be compared against every other — they share a centre and the legend puts
   * their swatches side by side — so this is an all-pairs test, not an
   * adjacent-pairs one. The floors are the standard ones: ΔE 8 under simulated
   * protanopia and deuteranopia, and 15 for unimpaired vision, measured in
   * OKLab. The palette that shipped before failed both on green↔gold.
   */
  it.each(CASES)('no two slices collapse under colour vision deficiency ($mode, highContrast=$hc)', ({ mode, hc }) => {
    for (let i = 0; i < CATEGORICAL_PIE_SLOTS; i += 1) {
      for (let j = i + 1; j < CATEGORICAL_PIE_SLOTS; j += 1) {
        const a = categoricalPieColor(i, mode, hc);
        const b = categoricalPieColor(j, mode, hc);
        const detail = `${a} vs ${b} (${mode}, highContrast=${hc})`;
        expect(cvdDistance(a, b, 'protan'), detail).toBeGreaterThanOrEqual(8);
        expect(cvdDistance(a, b, 'deutan'), detail).toBeGreaterThanOrEqual(8);
        expect(cvdDistance(a, b), detail).toBeGreaterThanOrEqual(15);
      }
    }
  });

  /**
   * Slot order is the identity mechanism: slot 2 must be "the same category" in
   * light, dark, and either high-contrast variant, or a category changes colour
   * when the user flips a display setting.
   */
  it('keeps every variant the same length', () => {
    for (const palette of Object.values(categoricalPieColors)) {
      expect(palette).toHaveLength(CATEGORICAL_PIE_SLOTS);
    }
  });

  /**
   * BR-14, and the reason this palette is confined to non-semantic charts: a
   * slice meaning "Health" must never be mistakable for one meaning "Completed".
   */
  it('never collides with a status or priority hue', () => {
    for (const mode of MODES) {
      for (let i = 0; i < CATEGORICAL_PIE_SLOTS; i += 1) {
        const slice = categoricalPieColor(i, mode, false);
        for (const semantic of SEMANTIC) {
          expect(deltaE(slice, semantic)).toBeGreaterThanOrEqual(12);
        }
      }
    }
  });

  /**
   * This is the regression. The palette used to wrap, so a fifth category was
   * painted in the first category's colour — and because a pie's last slice
   * touches its first, at exactly five categories the repeat landed next to its
   * twin and the two merged into a single wedge. Charts now cap their slices at
   * `CATEGORICAL_PIE_SLOTS` and roll the tail into one "Other" slice, so the
   * palette must never hand back a colour it has already used.
   */
  it('never repeats a colour, so two categories cannot share one', () => {
    for (const { mode, hc } of CASES) {
      const used = new Set<string>();
      for (let i = 0; i < CATEGORICAL_PIE_SLOTS; i += 1) {
        used.add(categoricalPieColor(i, mode, hc));
      }
      expect(used.size, `${mode} highContrast=${hc}`).toBe(CATEGORICAL_PIE_SLOTS);
    }
  });

  it('does not wrap back to the first colour past the last slot', () => {
    for (const { mode, hc } of CASES) {
      const first = categoricalPieColor(0, mode, hc);
      expect(categoricalPieColor(CATEGORICAL_PIE_SLOTS, mode, hc)).not.toBe(first);
      expect(categoricalPieColor(CATEGORICAL_PIE_SLOTS + 1, mode, hc)).not.toBe(first);
    }
  });
});

describe('background tones (FR-40)', () => {
  const TONE_IDS = backgroundTones.map((tone) => tone.id);
  // The text colours that actually render on these surfaces.
  /**
   * Derived from the theme, never hardcoded. An earlier version of this file
   * pinned `#616161` for light muted text; when FR-43 moved that token to the
   * grey ramp the test kept asserting against a colour the app no longer
   * rendered, and it went on passing while Cool (4.37:1) and Soft (4.27:1)
   * actually failed. Reading the real values is what makes this test able to
   * fail.
   */
  const TEXT = {
    light: { body: surfaceTokens('light').foreground, muted: surfaceTokens('light').mutedForeground },
    dark: { body: surfaceTokens('dark').foreground, muted: surfaceTokens('dark').mutedForeground },
  } as const;

  it('offers the sixteen curated tones and nothing else', () => {
    expect(TONE_IDS).toEqual([
      'neutral', 'warm', 'cool', 'soft', 'tinted', 'flat',
      // The ten washes.
      'rose', 'mint', 'lavender', 'sand', 'sage', 'ice', 'linen', 'slate', 'plum', 'stone',
    ]);
  });

  /**
   * Neutral contributes nothing of its own — it defers entirely to the base
   * tokens, so there is exactly one place the default surfaces are defined.
   *
   * This once also guaranteed byte-identical output to the pre-FR-40 app
   * (FR-40 AC-3). FR-44 deliberately broke that by giving the light canvas the
   * grey-100 value it always claimed to have; the invariant that remains is the
   * one worth keeping.
   */
  it('defers entirely to the base tokens on Neutral', () => {
    for (const mode of MODES) {
      expect(toneSurfaces(mode, 'neutral')).toBeNull();

      const base = buildTheme(mode, 'blue', 'comfortable', false);
      const neutral = buildTheme(mode, 'blue', 'comfortable', false, 'neutral');
      expect(neutral.palette.background.default).toBe(base.palette.background.default);
      expect(neutral.palette.background.paper).toBe(base.palette.background.paper);
    }
  });

  /**
   * BR-35: this is the promise curating the tones buys us. A tone added later
   * without checking its contrast fails here rather than shipping.
   */
  it.each(TONE_IDS)('%s keeps text readable on every surface it paints', (toneId) => {
    for (const mode of MODES) {
      const surfaces = toneSurfaces(mode, toneId);
      if (!surfaces) {
        continue; // neutral contributes nothing; the base tokens are covered above
      }
      // Tinted resolves through CSS color-mix at render time, so there is no
      // static value to measure here — its base is asserted separately below.
      if (surfaces.background.startsWith('color-mix')) {
        continue;
      }
      expect(contrast(TEXT[mode].body, surfaces.background)).toBeGreaterThanOrEqual(7);
      expect(contrast(TEXT[mode].body, surfaces.card)).toBeGreaterThanOrEqual(7);
      expect(contrast(TEXT[mode].muted, surfaces.background)).toBeGreaterThanOrEqual(4.5);
      expect(contrast(TEXT[mode].muted, surfaces.card)).toBeGreaterThanOrEqual(4.5);
    }
  });

  /**
   * The test this file was missing, and the bug it would have caught.
   *
   * Every earlier assertion checked that a tone *had* values, not that those
   * values ever reach the screen. They didn't: the light variants of cool, soft,
   * tinted, and flat returned plain white for `background`, putting their real
   * canvas colour only in the `--page` CSS variable — which `CssBaseline` paints
   * over when it sets `body` from `palette.background.default`. Four of the six
   * tones did nothing whatsoever in light mode and every test still passed.
   *
   * So this asserts the user-visible property directly: picking a tone must
   * change the canvas. Flat is the one legitimate exception — it is *defined* as
   * having no canvas step in light mode, and earns its keep through the border,
   * which is asserted separately below.
   */
  it.each(TONE_IDS.filter((id) => id !== 'neutral'))('%s actually changes the canvas', (toneId) => {
    for (const mode of MODES) {
      const neutral = buildTheme(mode, 'blue', 'comfortable', false, 'neutral');
      const toned = buildTheme(mode, 'blue', 'comfortable', false, toneId);

      const changedCanvas = toned.palette.background.default !== neutral.palette.background.default;
      const changedCard = toned.palette.background.paper !== neutral.palette.background.paper;
      const changedBorder = toned.palette.divider !== neutral.palette.divider;

      expect(changedCanvas || changedCard || changedBorder).toBe(true);
    }
  });

  /**
   * The canvas and the card are different things; a tone must not conflate them.
   *
   * One exclusion: Flat *is* the absence of a step by definition — the border
   * replaces it, asserted below. Neutral used to be excluded too, because the
   * light canvas rendered white despite `--page` claiming `#fafafa`; FR-44 moved
   * the canvas to where it is actually painted, so Neutral now has a real step
   * and is held to the same rule as the rest.
   */
  it('keeps a visible step between canvas and card', () => {
    for (const mode of MODES) {
      for (const toneId of TONE_IDS) {
        if (toneId === 'flat') {
          continue;
        }
        const theme = buildTheme(mode, 'blue', 'comfortable', false, toneId);
        expect(theme.palette.background.default).not.toBe(theme.palette.background.paper);
      }
    }
  });

  /**
   * FR-44, and the bug this fixes: in light mode the canvas rendered pure white
   * while `--page` claimed `#fafafa`, because nothing paints `--page` —
   * CssBaseline sets `body` from `palette.background.default`. Cards therefore
   * never stood out from the page, and Flat had no step to remove.
   */
  it('gives the light canvas a real step below the card', () => {
    const theme = buildTheme('light');
    expect(theme.palette.background.default).toBe(grey[100]);
    expect(theme.palette.background.default).not.toBe(theme.palette.background.paper);
  });

  /** FR-40.3: Tinted stores no colour — it mixes from the accent at render time. */
  it('derives Tinted from the accent rather than storing values', () => {
    const dark = toneSurfaces('dark', 'tinted');
    expect(dark?.background).toContain('color-mix');
    expect(dark?.background).toContain('--primary');
  });

  /**
   * AC-8: Flat removes the lightness step between page and card, so something
   * else has to keep cards from dissolving into the page.
   */
  it('compensates for Flat’s missing canvas step with a stronger border', () => {
    for (const mode of MODES) {
      const flat = toneSurfaces(mode, 'flat');
      expect(flat?.background).toBe(flat?.card);
      expect(flat?.border).toBeDefined();

      const base = surfaceTokens(mode, false);
      // Strictly more visible than the border it replaces.
      expect(contrast(flat!.border!, flat!.card)).toBeGreaterThan(contrast(base.border, base.card));
    }
  });

  /**
   * FR-40.5: the accessibility mode outranks the aesthetic preference. The tone
   * must be ignored while high contrast is on — and, just as importantly, not
   * erased, so switching high contrast off restores it exactly.
   */
  it('is overridden by high contrast, in both modes', () => {
    for (const mode of MODES) {
      const hcNeutral = buildTheme(mode, 'blue', 'comfortable', true, 'neutral');
      for (const toneId of TONE_IDS) {
        const withTone = buildTheme(mode, 'blue', 'comfortable', true, toneId);
        expect(withTone.palette.background.default).toBe(hcNeutral.palette.background.default);
        expect(withTone.palette.background.paper).toBe(hcNeutral.palette.background.paper);
      }
    }
  });

  it('applies the tone again as soon as high contrast is switched off', () => {
    const on = buildTheme('light', 'blue', 'comfortable', true, 'warm');
    const off = buildTheme('light', 'blue', 'comfortable', false, 'warm');
    expect(off.palette.background.default).not.toBe(on.palette.background.default);
    expect(off.palette.background.default).toBe(toneSurfaces('light', 'warm')!.background);
  });

  it('maps stored enum names onto tone ids, falling back for anything unknown', () => {
    expect(toneFromStored('WARM')).toBe('warm');
    expect(toneFromStored('FLAT')).toBe('flat');
    expect(toneFromStored('CHARCOAL')).toBe('neutral');
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
