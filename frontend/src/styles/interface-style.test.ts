import { describe, expect, it } from 'vitest';
import { createAppTheme, interfaceStyleFromStored, interfaceStyles } from '../theme';
import { INTERFACE_STYLES, styleCssVariables, styleTokens } from '../design/tokens';

/**
 * FR-48 as reworked by BRD 26 — the interface style in the MUI theme.
 *
 * The token values themselves (and their sync with global.css) are checked in
 * design/style-tokens.test.ts. This file checks the theme side: MUI surfaces
 * read the --style-* variables rather than copies of the numbers, and a style
 * changes shape, never colour.
 */

type StyleOverrides = Record<string, Record<string, unknown>>;

function overrides(theme: ReturnType<typeof createAppTheme>, component: string): StyleOverrides {
  const entry = (theme.components as Record<string, { styleOverrides?: StyleOverrides }>)[component];
  expect(entry?.styleOverrides, `${component} overrides missing`).toBeDefined();
  return entry!.styleOverrides!;
}

describe('interface style (FR-48, BRD 26)', () => {
  it('exposes exactly the three styles the backend enum stores, Modern first', () => {
    expect(interfaceStyles.map((style) => style.stored)).toEqual(['MODERN', 'CLASSIC', 'LIQUID_GLASS']);
    expect(interfaceStyles.map((style) => style.id)).toEqual([...INTERFACE_STYLES]);
  });

  it('maps the stored values to the renamed ids (Q-26-4) and unknown values to Modern', () => {
    expect(interfaceStyleFromStored('LIQUID_GLASS')).toBe('glass');
    expect(interfaceStyleFromStored('CLASSIC')).toBe('classic');
    expect(interfaceStyleFromStored('MODERN')).toBe('modern');
    expect(interfaceStyleFromStored('SOMETHING_ELSE')).toBe('modern');
  });

  it('sets theme.shape.borderRadius to the style’s control radius', () => {
    for (const style of INTERFACE_STYLES) {
      expect(createAppTheme({ mode: 'light', style }).shape.borderRadius).toBe(styleTokens[style].light.radiusControl);
    }
  });

  it('defaults to Modern when no style is given', () => {
    expect(createAppTheme({ mode: 'light' }).shape.borderRadius).toBe(styleTokens.modern.light.radiusControl);
  });

  it('paints cards from the solid card tokens in every style (D-26-3)', () => {
    for (const style of INTERFACE_STYLES) {
      const root = overrides(createAppTheme({ mode: 'light', style }), 'MuiCard').root;
      expect(root).toMatchObject({
        backgroundColor: 'var(--style-card-bg)',
        border: 'var(--style-card-border)',
        borderRadius: 'var(--style-radius-card)',
        boxShadow: 'var(--style-shadow-rest)',
        backgroundImage: 'none',
      });
      expect(root).not.toHaveProperty('backdropFilter');
    }
  });

  it('paints dialogs and popovers from the floating tokens, so glass and its fallbacks reach them', () => {
    const theme = createAppTheme({ mode: 'dark', style: 'glass' });
    for (const [component, radius] of [['MuiDialog', 'var(--style-radius-dialog)'], ['MuiPopover', 'var(--style-radius-menu)']] as const) {
      const paper = overrides(theme, component).paper;
      expect(paper).toMatchObject({
        borderRadius: radius,
        backgroundColor: 'var(--style-floating-bg)',
        backdropFilter: 'var(--style-blur)',
        border: 'var(--style-floating-border)',
        boxShadow: 'var(--style-highlight), var(--style-shadow-overlay)',
      });
    }
  });

  it('uses a 30% scrim under glass dialogs and 40% otherwise (§4)', () => {
    const scrim = (style: 'modern' | 'glass') =>
      (overrides(createAppTheme({ mode: 'light', style }), 'MuiDialog').root as Record<string, { backgroundColor: string }>)['& .MuiBackdrop-root:not(.MuiBackdrop-invisible)'].backgroundColor;
    expect(scrim('glass')).toContain('0.30');
    expect(scrim('modern')).toContain('0.40');
  });

  it('takes button, input and chip radii from the tokens', () => {
    const theme = createAppTheme({ mode: 'light', style: 'classic' });
    expect(overrides(theme, 'MuiButton').root).toMatchObject({ borderRadius: 'var(--style-radius-control)' });
    expect(overrides(theme, 'MuiOutlinedInput').root).toMatchObject({ borderRadius: 'var(--style-radius-control)' });
    expect(overrides(theme, 'MuiChip').root).toMatchObject({ borderRadius: 'var(--style-radius-chip)' });
  });

  it('follows each style’s motion tokens (§3.5)', () => {
    for (const style of INTERFACE_STYLES) {
      const { duration, easing } = createAppTheme({ mode: 'light', style }).transitions;
      expect(duration.shortest).toBe(styleTokens[style].light.motionFast);
      expect(duration.standard).toBe(styleTokens[style].light.motionBase);
      expect(duration.complex).toBe(styleTokens[style].light.motionSlow);
      expect(easing.easeInOut).toBe(styleTokens[style].light.ease);
    }
  });

  /**
   * BR-14, extended: a style may not introduce a hue. Shadows (black or white
   * alpha, no hue) and the dark Modern hover border (a neutral grey) are the
   * only literal colours a style block may carry.
   */
  it('introduces no hue of its own', () => {
    const hueless = /rgba\((\d+), \1, \1, [\d.]+\)/g;
    for (const style of INTERFACE_STYLES) {
      for (const mode of ['light', 'dark'] as const) {
        for (const [name, value] of Object.entries(styleCssVariables(style, mode))) {
          const stripped = value.replace(hueless, '').replace(/transparent/g, '');
          if (name.startsWith('--style-shadow-') || name === '--style-hover-border' || name === '--style-floating-text-tertiary') {
            // Shadows are near-black alphas, and the other two are neutral
            // greys from the neutral ramp: none of them reads as a status.
            continue;
          }
          expect(stripped, `${style}/${mode} ${name}`).not.toMatch(/#[0-9a-f]{3,8}\b|rgba?\(/i);
        }
      }
    }
  });

  /** The composition claim: the style reaches shape and leaves the palette alone. */
  it('changes no palette value when the style changes', () => {
    const base = { mode: 'light', accent: 'teal', density: 'comfortable', highContrast: false, tone: 'warm', font: 'system' } as const;
    const classic = createAppTheme({ ...base, style: 'classic' });
    const glass = createAppTheme({ ...base, style: 'glass' });

    expect(glass.palette.primary.main).toBe(classic.palette.primary.main);
    expect(glass.palette.background.default).toBe(classic.palette.background.default);
    expect(glass.palette.background.paper).toBe(classic.palette.background.paper);
    expect(glass.palette.text.primary).toBe(classic.palette.text.primary);
    expect(glass.palette.divider).toBe(classic.palette.divider);
    expect(glass.shape.borderRadius).not.toBe(classic.shape.borderRadius);
  });

  it('keeps the chosen shape under high contrast (only translucency falls back, in CSS)', () => {
    const theme = createAppTheme({ mode: 'light', highContrast: true, style: 'glass' });
    expect(theme.shape.borderRadius).toBe(styleTokens.glass.light.radiusControl);
  });
});
