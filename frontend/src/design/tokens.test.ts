import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import {
  aliasedNeutralDefaults,
  brand,
  chartCategorical,
  chartOther,
  cssVariables,
  heatScale,
  neutralAliases,
  neutrals,
  STATUS_FAMILIES,
  statusTones,
  WORK_STATUS_FAMILIES,
  type Mode,
} from './tokens';
import { statusColor, statusColors, statusColorsDark, statusFamily, priorityColors, SYSTEM_STACK, type StatusToken } from '../theme';

/**
 * BRD 25 — the token contract. Enforces, rather than documents:
 *   - WCAG 2.2 AA for every text and non-text pairing the tokens promise (DA-NFR-01)
 *   - colour-vision separation for the status families and the chart palette (DA-NFR-02)
 *   - that global.css carries exactly these values (D-25-6)
 */

const MODES: Mode[] = ['light', 'dark'];

function toLinear(hex: string): number[] {
  const int = Number.parseInt(hex.slice(1), 16);
  return [(int >> 16) & 255, (int >> 8) & 255, int & 255].map((value) => {
    const scaled = value / 255;
    return scaled <= 0.04045 ? scaled / 12.92 : Math.pow((scaled + 0.055) / 1.055, 2.4);
  });
}

function contrast(a: string, b: string): number {
  const lum = (hex: string) => {
    const [r, g, bl] = toLinear(hex);
    return 0.2126 * r + 0.7152 * g + 0.0722 * bl;
  };
  const [lighter, darker] = [lum(a), lum(b)].sort((x, y) => y - x);
  return (lighter + 0.05) / (darker + 0.05);
}

/** Machado–Oliveira–Fernandes 2009, severity 1.0 — the same model theme.test.ts uses. */
const MACHADO = {
  protan: [[0.152286, 1.052583, -0.204868], [0.114503, 0.786281, 0.099216], [-0.003882, -0.048116, 1.051998]],
  deutan: [[0.367322, 0.860646, -0.227968], [0.280085, 0.672501, 0.047413], [-0.011820, 0.042940, 0.968881]],
} as const;

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

/** OKLab distance ×100, optionally under a colour-vision simulation. */
function distance(first: string, second: string, kind?: keyof typeof MACHADO): number {
  const project = (hex: string) => {
    const rgb = toLinear(hex);
    if (!kind) {
      return oklab(rgb);
    }
    const clamp = (value: number) => Math.max(0, Math.min(1, value));
    return oklab(MACHADO[kind].map((row) => clamp(row[0] * rgb[0] + row[1] * rgb[1] + row[2] * rgb[2])));
  };
  const [p, q] = [project(first), project(second)];
  return 100 * Math.hypot(p[0] - q[0], p[1] - q[1], p[2] - q[2]);
}

const cvdFloor = (a: string, b: string) => Math.min(distance(a, b, 'protan'), distance(a, b, 'deutan'));

describe('neutrals (BRD 25 §5.1)', () => {
  it.each(MODES)('text tokens meet AA on the surface and the canvas (%s)', (mode) => {
    const n = neutrals[mode];
    for (const ground of [n.surface, n.canvas, n.surfaceRaised]) {
      expect(contrast(n.textPrimary, ground)).toBeGreaterThanOrEqual(7);
      expect(contrast(n.textSecondary, ground)).toBeGreaterThanOrEqual(4.5);
      expect(contrast(n.textTertiary, ground)).toBeGreaterThanOrEqual(4.5);
    }
  });

  it.each(MODES)('input outlines meet the 3:1 non-text rule (%s)', (mode) => {
    const n = neutrals[mode];
    expect(contrast(n.borderStrong, n.surface)).toBeGreaterThanOrEqual(3);
  });

  it.each(MODES)('keeps a visible step from canvas to card (%s)', (mode) => {
    const n = neutrals[mode];
    expect(n.canvas).not.toBe(n.surface);
  });
});

describe('brand (BRD 25 §5.1)', () => {
  it.each(MODES)('carries its own text, and the tint pair is readable (%s)', (mode) => {
    const b = brand[mode];
    for (const fill of [b.main, b.hover, b.pressed]) {
      expect(contrast(fill, b.contrastText)).toBeGreaterThanOrEqual(4.5);
    }
    expect(contrast(b.tintForeground, b.tint)).toBeGreaterThanOrEqual(4.5);
  });

  it.each(MODES)('the focus ring (brand) is visible on canvas and card (%s)', (mode) => {
    expect(contrast(brand[mode].main, neutrals[mode].surface)).toBeGreaterThanOrEqual(3);
    expect(contrast(brand[mode].main, neutrals[mode].canvas)).toBeGreaterThanOrEqual(3);
  });
});

describe('status families (BRD 25 §5.2, D-25-4)', () => {
  it.each(MODES)('every fill is visible on the surface (%s)', (mode) => {
    for (const family of STATUS_FAMILIES) {
      expect(contrast(statusTones[mode][family].fill, neutrals[mode].surface), family).toBeGreaterThanOrEqual(3);
    }
  });

  it.each(MODES)('every chip label is readable on its chip and on the surface (%s)', (mode) => {
    for (const family of STATUS_FAMILIES) {
      const tone = statusTones[mode][family];
      expect(contrast(tone.fg, tone.bg), `${family} fg on bg`).toBeGreaterThanOrEqual(4.5);
      expect(contrast(tone.fg, neutrals[mode].surface), `${family} fg on surface`).toBeGreaterThanOrEqual(4.5);
    }
  });

  /**
   * The six work statuses share one chart (the stacked status bar), so every
   * pair must stay apart for red-green colour blindness. V24's blue and purple
   * measured ΔE ~1–2 here, which is the fault this test exists to prevent.
   */
  it.each(MODES)('work statuses stay apart under protan and deutan simulation (%s)', (mode) => {
    for (let i = 0; i < WORK_STATUS_FAMILIES.length; i += 1) {
      for (let j = i + 1; j < WORK_STATUS_FAMILIES.length; j += 1) {
        const a = statusTones[mode][WORK_STATUS_FAMILIES[i]].fill;
        const b = statusTones[mode][WORK_STATUS_FAMILIES[j]].fill;
        const detail = `${WORK_STATUS_FAMILIES[i]} ${a} vs ${WORK_STATUS_FAMILIES[j]} ${b} (${mode})`;
        expect(cvdFloor(a, b), detail).toBeGreaterThanOrEqual(10);
        expect(distance(a, b), detail).toBeGreaterThanOrEqual(10);
      }
    }
  });

  it('gives Open (obstacles) its own hue, no longer the Blocked orange', () => {
    for (const mode of MODES) {
      expect(statusTones[mode].open.fill).not.toBe(statusTones[mode].blocked.fill);
      expect(distance(statusTones[mode].open.fill, statusTones[mode].blocked.fill)).toBeGreaterThanOrEqual(15);
    }
  });

  it('keeps the High priority distinct from Blocked in both modes', () => {
    for (const mode of MODES) {
      expect(cvdFloor(priorityColors.HIGH, statusTones[mode].blocked.fill)).toBeGreaterThanOrEqual(8);
    }
    expect(cvdFloor(priorityColors.HIGH, priorityColors.CRITICAL)).toBeGreaterThanOrEqual(8);
  });
});

describe('theme.ts reads the status families', () => {
  const SAMPLE: Array<[StatusToken, keyof typeof statusTones.light]> = [
    ['NOT_STARTED', 'idle'],
    ['IN_PROGRESS', 'moving'],
    ['WAITING', 'waiting'],
    ['FOLLOWED_UP', 'waiting'],
    ['BLOCKED', 'blocked'],
    ['OPEN', 'open'],
    ['DECLINED', 'declined'],
    ['PAUSED', 'held'],
    ['COMPLETED', 'done'],
  ];

  it.each(SAMPLE)('maps %s to the %s family in both modes', (token, family) => {
    expect(statusFamily(token)).toBe(family);
    expect(statusColors[token]).toBe(statusTones.light[family].fill);
    expect(statusColorsDark[token]).toBe(statusTones.dark[family].fill);
    expect(statusColor(token, 'light')).toBe(statusTones.light[family].fill);
    expect(statusColor(token, 'dark')).toBe(statusTones.dark[family].fill);
  });

  it('knows nothing about an enum it has not been told about', () => {
    expect(statusFamily('SOMETHING_NEW')).toBeUndefined();
  });

  it('puts the Khmer face in the shared font stack (D-25-9)', () => {
    expect(SYSTEM_STACK).toContain('"Noto Sans Khmer Variable"');
  });
});

describe('chart palette (BRD 25 §5.3)', () => {
  it.each(MODES)('every slot is visible on the card (%s)', (mode) => {
    for (const color of [...chartCategorical[mode], chartOther[mode]]) {
      expect(contrast(color, neutrals[mode].surface), color).toBeGreaterThanOrEqual(3);
    }
  });

  it.each(MODES)('no two slots collapse under colour-vision simulation (%s)', (mode) => {
    const slots = chartCategorical[mode];
    for (let i = 0; i < slots.length; i += 1) {
      for (let j = i + 1; j < slots.length; j += 1) {
        const detail = `${slots[i]} vs ${slots[j]} (${mode})`;
        expect(cvdFloor(slots[i], slots[j]), detail).toBeGreaterThanOrEqual(8);
        expect(distance(slots[i], slots[j]), detail).toBeGreaterThanOrEqual(15);
      }
    }
  });

  // Each step stands further from the card than the last, so more always reads as more.
  it.each(MODES)('the heatmap scale rises in one direction (%s)', (mode) => {
    const steps = heatScale[mode];
    for (let i = 1; i < steps.length; i += 1) {
      expect(contrast(steps[i], neutrals[mode].surface)).toBeGreaterThan(contrast(steps[i - 1], neutrals[mode].surface));
    }
  });
});

/**
 * D-25-6: global.css carries the tokens. Parsed rather than trusted, like the
 * FR-47 palette block, so a value changed in one place fails here.
 */
describe('global.css carries the V25 tokens (D-25-6)', () => {
  // Resolved from the project root: under jsdom, import.meta.url is not a file URL.
  const css = readFileSync(resolve(process.cwd(), 'src/styles/global.css'), 'utf8');

  function block(selectorStart: string): Map<string, string> {
    const start = css.indexOf(selectorStart);
    expect(start, `${selectorStart} block missing`).toBeGreaterThanOrEqual(0);
    const body = css.slice(css.indexOf('{', start) + 1, css.indexOf('\n}', start));
    const found = new Map<string, string>();
    for (const match of body.matchAll(/(--[a-z0-9-]+)\s*:\s*([^;]+);/gi)) {
      found.set(match[1], match[2].trim());
    }
    return found;
  }

  const light = block(':root {\n  color: var(--foreground);');
  const dark = block('[data-theme="dark"] {');

  it('declares every light token with the value tokens.ts holds', () => {
    for (const [name, value] of Object.entries(cssVariables('light'))) {
      expect(light.get(name)?.toUpperCase(), `${name} (light)`).toBe(value.toUpperCase());
    }
  });

  it('declares every dark token with the value tokens.ts holds', () => {
    for (const [name, value] of Object.entries(cssVariables('dark'))) {
      expect(dark.get(name)?.toUpperCase(), `${name} (dark)`).toBe(value.toUpperCase());
    }
  });

  it('aliases the V25 surface and text names to the variables tones override', () => {
    for (const [name, value] of Object.entries(neutralAliases)) {
      expect(light.get(name), name).toBe(value);
    }
  });

  it.each(MODES)('sets the aliased variables to the V25 neutrals (%s)', (mode) => {
    const declared = mode === 'light' ? light : dark;
    for (const [name, value] of Object.entries(aliasedNeutralDefaults(mode))) {
      expect(declared.get(name)?.toUpperCase(), `${name} (${mode})`).toBe(value.toUpperCase());
    }
  });

  it('defaults the accent variables to the V25 brand', () => {
    expect(light.get('--primary')?.toUpperCase()).toBe(brand.light.main);
    expect(dark.get('--primary')?.toUpperCase()).toBe(brand.dark.main);
  });

  it('declares the Khmer face for the Khmer range only (D-25-9, DA-NFR-04)', () => {
    const face = css.slice(css.indexOf('@font-face'), css.indexOf('}', css.indexOf('@font-face')));
    expect(face).toContain("font-family: 'Noto Sans Khmer Variable'");
    expect(face).toContain('unicode-range: U+1780-17FF');
    expect(face).not.toMatch(/U\+0000/);
  });
});
