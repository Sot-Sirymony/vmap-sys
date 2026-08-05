import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { buildTheme, interfaceStyles, styleShape, type InterfaceStyleId } from '../theme';

/**
 * FR-48 — the interface style, and the two invariants it depends on.
 *
 * The first is a contract between files: `styleShape` drives MUI components and
 * the `:root[data-style="modern"]` block drives hand-written CSS, and a card
 * whose corner disagreed with the panel beside it would be the visible symptom
 * of them drifting. Same arrangement as the `--palette-*` variables, and the
 * same reason for testing it.
 *
 * The second is the claim that makes this a cheap feature at all: a style
 * changes shape, never colour. That is what lets Modern compose with twelve
 * accents, six tones, and high contrast without a single new pair to
 * contrast-check — so it is asserted rather than merely documented.
 */
const css = readFileSync(resolve(process.cwd(), 'src/styles/global.css'), 'utf8');

/** The declarations inside one selector's block. */
function blockVars(selector: string): Map<string, string> {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const block = new RegExp(`${escaped}\\s*\\{([^}]*)\\}`).exec(css);
  expect(block, `${selector} block missing from global.css`).not.toBeNull();

  const found = new Map<string, string>();
  for (const match of block![1].matchAll(/(--[a-z0-9-]+)\s*:\s*([^;]+);/gi)) {
    found.set(match[1], match[2].trim());
  }
  return found;
}

/** Whitespace differs harmlessly between a CSS file and a JS string literal. */
function normalise(value: string): string {
  return value.replace(/\s+/g, ' ').trim();
}

describe('interface style (FR-48)', () => {
  it('exposes exactly the two styles the backend enum stores', () => {
    expect(interfaceStyles.map((style) => style.stored)).toEqual(['CLASSIC', 'MODERN']);
  });

  /**
   * AC: Classic is defined as the treatment that shipped before this control
   * existed, so it has to be byte-identical to Fluent's own tokens — otherwise
   * the "no existing user is redesigned" promise is not kept.
   */
  it('leaves Classic on Fluent’s 4px radius and flat surfaces', () => {
    const shape = styleShape('classic', 'light');
    expect(shape.radius).toBe(4);
    expect(shape.pillRadius).toBe(4);
    expect(shape.cardRadius).toBe(4);
    // `none`, not undefined: it is what suppresses MUI's dark-mode elevation
    // overlay gradient.
    expect(shape.cardWash).toBe('none');
    expect(shape.headingLetterSpacing).toBe('normal');
  });

  it('gives Modern rounder corners and a pill for chips and nav', () => {
    const shape = styleShape('modern', 'light');
    expect(shape.radius).toBeGreaterThan(styleShape('classic', 'light').radius);
    expect(shape.pillRadius).toBe(999);
    expect(shape.cardWash).toContain('linear-gradient');
  });

  it('matches the --radius global.css declares for Modern', () => {
    expect(blockVars(':root[data-style="modern"]').get('--radius'))
      .toBe(`${styleShape('modern', 'light').radius}px`);
  });

  /**
   * The card shadow is the one shape token both channels paint: MUI's Card uses
   * `styleShape().cardShadow`, and hand-written panels use `--shadow-4`. They
   * are the same tier and must be the same value, per mode.
   */
  it('keeps the Modern card shadow identical in the theme and the stylesheet', () => {
    expect(normalise(blockVars(':root[data-style="modern"]').get('--shadow-4') ?? ''))
      .toBe(normalise(styleShape('modern', 'light').cardShadow));
    expect(normalise(blockVars(':root[data-theme="dark"][data-style="modern"]').get('--shadow-4') ?? ''))
      .toBe(normalise(styleShape('modern', 'dark').cardShadow));
  });

  it('declares all four elevation tiers for Modern, in both modes', () => {
    for (const selector of [':root[data-style="modern"]', ':root[data-theme="dark"][data-style="modern"]']) {
      const declared = blockVars(selector);
      for (const tier of ['--shadow-2', '--shadow-4', '--shadow-8', '--shadow-16']) {
        expect(declared.has(tier), `${tier} missing from ${selector}`).toBe(true);
      }
    }
  });

  /**
   * BR-14, extended: a style may not introduce a hue. The shadows are the one
   * legitimate exception — a shadow is by definition a black or near-black with
   * an alpha, and it carries no meaning a user could misread as a status.
   */
  it('introduces no colour of its own', () => {
    for (const style of ['classic', 'modern'] as InterfaceStyleId[]) {
      for (const mode of ['light', 'dark'] as const) {
        const shape = styleShape(style, mode);
        const colourBearing = [shape.cardWash, shape.headingLetterSpacing].join(' ');
        expect(colourBearing, `${style}/${mode} names a literal colour`).not.toMatch(/#[0-9a-f]{3,8}\b/i);
        // The wash is allowed to reference the accent, because that is the
        // user's own colour rather than a new one this file chose.
        if (shape.cardWash !== 'none') {
          expect(shape.cardWash).toContain('var(--primary)');
        }
      }
    }
  });

  /** The stylesheet half of the same rule. */
  it('declares no colour in the Modern CSS block beyond shadow alphas', () => {
    for (const selector of [':root[data-style="modern"]', ':root[data-theme="dark"][data-style="modern"]']) {
      for (const [name, value] of blockVars(selector)) {
        if (name.startsWith('--shadow-')) {
          continue;
        }
        expect(value, `${name} in ${selector} names a colour`).not.toMatch(/#[0-9a-f]{3,8}\b|rgba?\(/i);
      }
    }
  });

  /**
   * The composition claim, checked where it would actually break: the style
   * reaches the theme's shape and typography and leaves its palette alone.
   */
  it('changes no palette value when the style changes', () => {
    const classic = buildTheme('light', 'teal', 'comfortable', false, 'warm', 'system', 'classic');
    const modern = buildTheme('light', 'teal', 'comfortable', false, 'warm', 'system', 'modern');

    expect(modern.palette.primary.main).toBe(classic.palette.primary.main);
    expect(modern.palette.background.default).toBe(classic.palette.background.default);
    expect(modern.palette.background.paper).toBe(classic.palette.background.paper);
    expect(modern.palette.text.primary).toBe(classic.palette.text.primary);
    expect(modern.palette.divider).toBe(classic.palette.divider);
    // …while the shape does change, so the test would still fail if the style
    // were being dropped on the floor entirely.
    expect(modern.shape.borderRadius).not.toBe(classic.shape.borderRadius);
  });

  it('composes with high contrast rather than being overridden by it', () => {
    // Unlike the background tone, a corner radius cannot hurt legibility, so
    // high contrast has no reason to take the user's chosen shape away.
    const theme = buildTheme('light', 'blue', 'comfortable', true, 'neutral', 'system', 'modern');
    expect(theme.shape.borderRadius).toBe(styleShape('modern', 'light').radius);
  });

  it('defaults to Classic when no style is given', () => {
    expect(buildTheme('light').shape.borderRadius).toBe(styleShape('classic', 'light').radius);
  });
});
