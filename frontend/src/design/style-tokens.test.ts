import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import {
  glassFallback,
  INTERFACE_STYLES,
  legacyStyleAliases,
  neutrals,
  styleBlockSelector,
  styleCssVariables,
  styleTokens,
  type Mode,
} from './tokens';

/**
 * BRD 26 — the interface style token contract (D-26-1, D-26-5, D-26-6).
 * Enforces, rather than documents:
 *   - that global.css carries exactly the `--style-*` values in tokens.ts
 *   - that the legacy radius, shadow and motion names are aliases of them
 *   - that glass keeps primary and secondary text at 4.5:1 over any backdrop
 *   - that glass falls back to solid in every case D-26-6 lists
 */

const css = readFileSync(resolve(process.cwd(), 'src/styles/global.css'), 'utf8');
const MODES: Mode[] = ['light', 'dark'];

/** The declarations of the first block whose selector is exactly `selector`. */
function blockVars(selector: string, source = css): Map<string, string> {
  const start = source.indexOf(`${selector} {`);
  expect(start, `${selector} block missing from global.css`).toBeGreaterThanOrEqual(0);
  const open = source.indexOf('{', start);
  const close = source.indexOf('}', open);
  const found = new Map<string, string>();
  for (const match of source.slice(open + 1, close).matchAll(/(--[a-z0-9-]+)\s*:\s*([^;]+);/gi)) {
    found.set(match[1], match[2].trim());
  }
  return found;
}

/** The body of an at-rule (`@media …`, `@supports …`), braces balanced. */
function atRuleBody(prelude: string): string {
  const start = css.indexOf(prelude);
  expect(start, `${prelude} missing from global.css`).toBeGreaterThanOrEqual(0);
  let depth = 0;
  for (let index = css.indexOf('{', start); index < css.length; index += 1) {
    if (css[index] === '{') depth += 1;
    if (css[index] === '}') {
      depth -= 1;
      if (depth === 0) return css.slice(css.indexOf('{', start) + 1, index);
    }
  }
  throw new Error(`unbalanced ${prelude}`);
}

function channels(hex: string): number[] {
  const int = Number.parseInt(hex.slice(1), 16);
  return [(int >> 16) & 255, (int >> 8) & 255, int & 255];
}

function toHex(rgb: number[]): string {
  return `#${rgb.map((value) => Math.round(value).toString(16).padStart(2, '0')).join('')}`;
}

/** sRGB source-over compositing, which is what color-mix(in srgb …) over a backdrop renders. */
function composite(surface: string, alpha: number, backdrop: string): string {
  const top = channels(surface);
  const bottom = channels(backdrop);
  return toHex(top.map((value, index) => value * alpha + bottom[index] * (1 - alpha)));
}

function contrast(a: string, b: string): number {
  const lum = (hex: string) => {
    const [r, g, bl] = channels(hex).map((value) => {
      const scaled = value / 255;
      return scaled <= 0.04045 ? scaled / 12.92 : Math.pow((scaled + 0.055) / 1.055, 2.4);
    });
    return 0.2126 * r + 0.7152 * g + 0.0722 * bl;
  };
  const [hi, lo] = [lum(a), lum(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

describe('style tokens (BRD 26)', () => {
  it('carries every style × mode block in global.css exactly as tokens.ts defines it', () => {
    for (const style of INTERFACE_STYLES) {
      for (const mode of MODES) {
        const declared = blockVars(styleBlockSelector(style, mode));
        const expected = styleCssVariables(style, mode);
        expect(Object.fromEntries(declared), `${style} ${mode}`).toEqual(expected);
      }
    }
  });

  it('declares the legacy radius, shadow and motion names as aliases of the style tokens', () => {
    const root = blockVars(':root');
    for (const [name, value] of Object.entries(legacyStyleAliases)) {
      expect(root.get(name), name).toBe(value);
    }
  });

  it('declares no legacy shadow literal in the dark block', () => {
    const dark = blockVars('[data-theme="dark"]');
    for (const tier of ['--shadow-2', '--shadow-4', '--shadow-8', '--shadow-16']) {
      expect(dark.has(tier), `${tier} in [data-theme="dark"] would beat the style tokens`).toBe(false);
    }
  });

  it('gives each style the shape the matrix sets (§3.1)', () => {
    expect(styleTokens.modern.light).toMatchObject({ radiusControl: 8, radiusCard: 12, radiusChip: 999, radiusDialog: 16, radiusMenu: 8, navShape: 'pill', header: 'greeting' });
    expect(styleTokens.classic.light).toMatchObject({ radiusControl: 4, radiusCard: 4, radiusChip: 4, radiusDialog: 4, radiusMenu: 4, navShape: 'rect', header: 'breadcrumbs' });
    expect(styleTokens.glass.light).toMatchObject({ radiusControl: 12, radiusCard: 18, radiusChip: 999, radiusDialog: 24, radiusMenu: 14, navShape: 'pill', header: 'greeting' });
  });

  it('gives each style its sidebar, header-at-rest and heatmap values (§4)', () => {
    expect(styleTokens.modern.light).toMatchObject({ sidebarBg: 'var(--sidebar)', headerRestBg: 'transparent', heatRadius: 3 });
    expect(styleTokens.classic.light).toMatchObject({ sidebarBg: 'var(--surface-raised)', headerRestBg: 'var(--surface-raised)', heatRadius: 1 });
    expect(styleTokens.glass.light).toMatchObject({ headerRestBg: 'transparent', heatRadius: 4 });
    expect(styleTokens.glass.light.sidebarBg).toContain('88%');
    expect(styleTokens.glass.dark.sidebarBg).toContain('85%');
  });

  it('keeps cards solid in every style (D-26-3)', () => {
    for (const style of INTERFACE_STYLES) {
      for (const mode of MODES) {
        expect(styleTokens[style][mode].cardBg).toBe('var(--surface)');
        expect(styleTokens[style][mode].insetBg).toBe('var(--surface-sunken)');
      }
    }
  });

  it('makes only Liquid Glass translucent', () => {
    expect(styleTokens.modern.light.blur).toBe('none');
    expect(styleTokens.classic.dark.blur).toBe('none');
    expect(styleTokens.glass.light.blur).toContain('blur(20px)');
    expect(styleTokens.glass.light.floatingBg).toContain('88%');
    expect(styleTokens.glass.dark.floatingBg).toContain('85%');
  });

  it('writes the opacity it declares into the colour mix', () => {
    for (const mode of MODES) {
      const glass = styleTokens.glass[mode];
      expect(glass.floatingBg).toContain(`${Math.round(glass.floatingAlpha * 100)}%`);
      expect(glass.floatingBgCanvas).toContain(`${Math.round(glass.floatingCanvasAlpha * 100)}%`);
    }
  });
});

/**
 * D-26-5 — the glass floors. The surface under the text is the floating colour
 * at its opacity, composited over the worst backdrop: black in light mode,
 * white in dark mode (a background image or content scrolling under the header
 * can be either). Primary and secondary text must stay at 4.5:1.
 */
describe('glass contrast floors (D-26-5)', () => {
  const surfaces: Record<Mode, string> = { light: neutrals.light.surface, dark: neutrals.dark.surfaceRaised };

  for (const mode of MODES) {
    const glass = styleTokens.glass[mode];
    const n = neutrals[mode];

    it(`keeps primary and secondary text at 4.5:1 on ${mode} glass over black and white`, () => {
      for (const backdrop of ['#000000', '#FFFFFF']) {
        const rendered = composite(surfaces[mode], glass.floatingAlpha, backdrop);
        expect(contrast(n.textPrimary, rendered), `primary on ${rendered}`).toBeGreaterThanOrEqual(4.5);
        expect(contrast(n.textSecondary, rendered), `secondary on ${rendered}`).toBeGreaterThanOrEqual(4.5);
      }
    });

    it(`keeps text at 4.5:1 on ${mode} canvas glass over the plain canvas`, () => {
      const rendered = composite(surfaces[mode], glass.floatingCanvasAlpha, n.canvas);
      expect(contrast(n.textPrimary, rendered)).toBeGreaterThanOrEqual(4.5);
      expect(contrast(n.textSecondary, rendered)).toBeGreaterThanOrEqual(4.5);
    });

    it(`promotes tertiary text to secondary on ${mode} glass`, () => {
      // Tertiary cannot reach 4.5:1 at the floor over the worst backdrop, which
      // is why the promotion exists.
      const worst = composite(surfaces[mode], glass.floatingAlpha, mode === 'light' ? '#000000' : '#FFFFFF');
      expect(contrast(n.textTertiary, worst)).toBeLessThan(4.5);
      expect(glass.floatingTextTertiary).toBe('var(--muted-foreground)');
    });
  }

  it('would fail at the old 60% / 66% opacity, so the test can catch a regression', () => {
    const light = composite(neutrals.light.surface, 0.6, '#000000');
    const dark = composite(neutrals.dark.surfaceRaised, 0.66, '#FFFFFF');
    expect(contrast(neutrals.light.textSecondary, light)).toBeLessThan(4.5);
    expect(contrast(neutrals.dark.textSecondary, dark)).toBeLessThan(4.5);
  });
});

/** D-26-6 — every fallback path turns every translucent token solid. */
describe('glass fallbacks (D-26-6)', () => {
  function expectFallback(vars: Map<string, string>, where: string) {
    for (const [name, value] of Object.entries(glassFallback)) {
      expect(vars.get(name), `${name} in ${where}`).toBe(value);
    }
  }

  it('falls back under the app’s High contrast setting, in both modes', () => {
    expectFallback(blockVars(':root[data-style="glass"][data-contrast="high"],\n:root[data-theme="dark"][data-style="glass"][data-contrast="high"]'), 'high contrast');
  });

  for (const prelude of ['@media (prefers-contrast: more)', '@media (prefers-reduced-transparency: reduce)', '@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px)))']) {
    it(`falls back under ${prelude}, in both modes`, () => {
      const body = atRuleBody(prelude);
      expectFallback(blockVars(':root[data-style="glass"],\n  :root[data-theme="dark"][data-style="glass"]', body), prelude);
    });
  }

  it('places the fallbacks after the style blocks, so they win the cascade', () => {
    const lastStyleBlock = css.indexOf(`${styleBlockSelector('glass', 'dark')} {`);
    expect(css.indexOf(':root[data-style="glass"][data-contrast="high"]')).toBeGreaterThan(lastStyleBlock);
    expect(css.indexOf('@media (prefers-reduced-transparency: reduce)')).toBeGreaterThan(lastStyleBlock);
  });
});
