/**
 * BRD 25 — design tokens (D-25-6). The one source for the V25 visual system.
 *
 * Where these values go:
 *   - `theme.ts` reads the status families, neutrals and brand for the MUI theme
 *     and for `statusColor()`.
 *   - `global.css` carries the same values as CSS variables (the `--st-*`,
 *     `--chart-*`, `--heat-*`, surface and text tokens). `tokens.test.ts` parses
 *     the stylesheet and fails if any value drifts from this file, so the two
 *     cannot disagree (the same guard FR-47 uses for the `--palette-*` block).
 *   - Charts (BRD 25 phase 3, `chartTheme.ts`) read the CSS variables at run
 *     time, so they follow mode, tone and accent with no hex values in components.
 *
 * Every colour below was measured before it was written down, with the same
 * method `theme.test.ts` uses: WCAG 2.2 contrast, and colour-vision distance in
 * OKLab (×100) under the Machado–Oliveira–Fernandes 2009 protan and deutan
 * simulations. `tokens.test.ts` enforces the floors.
 *
 * Status meaning (BRD 25 §5.2, D-25-4):
 *   moving   work is moving        In progress, Active, Contacted, Sent, Replied, Agreed, Planned
 *   waiting  waiting on someone    Waiting, Followed up, In review          (indigo)
 *   blocked  work cannot move      Blocked                                   (orange, work only)
 *   open     an issue is open      Open (obstacles, issue reports)           (berry)
 *   declined negative outcome      Declined
 *   done     finished              Completed, Resolved, Closed
 *   idle     not started           Not started, Idea, To contact, Draft
 *   held     on hold               Paused, Archived, Accepted, Won't fix
 *
 * Separation is by lightness as well as hue, because hue alone collapses for
 * red-green colour blindness: in light mode Blocked is a bright orange beside a
 * deep green Completed, and Waiting is a deep indigo beside a mid blue In
 * progress; dark mode reverses the lightness where needed. Every pair of work
 * statuses (the six that share one chart) clears ΔE 10 under both simulations.
 */

export type Mode = 'light' | 'dark';

export type StatusFamily = 'moving' | 'waiting' | 'blocked' | 'open' | 'declined' | 'done' | 'idle' | 'held';

export type StatusTone = {
  /** Icons, chart fills, the coloured dot. Meets 3:1 on the surface (WCAG 1.4.11). */
  fill: string;
  /** Chip background. */
  bg: string;
  /** Chip text. Meets 4.5:1 on `bg` and on the surface. */
  fg: string;
};

export const STATUS_FAMILIES: readonly StatusFamily[] = ['moving', 'waiting', 'blocked', 'open', 'declined', 'done', 'idle', 'held'];

/** The six work statuses that share one chart, in display order (Completed last). */
export const WORK_STATUS_FAMILIES: readonly StatusFamily[] = ['idle', 'moving', 'waiting', 'blocked', 'held', 'done'];

export const statusTones: Record<Mode, Record<StatusFamily, StatusTone>> = {
  light: {
    moving: { fill: '#0F6CBD', bg: '#EBF3FC', fg: '#0B5A9E' },
    waiting: { fill: '#3B2596', bg: '#EFECF9', fg: '#3B2596' },
    blocked: { fill: '#E46C1E', bg: '#FDF0E7', fg: '#A8400C' },
    open: { fill: '#B4009E', bg: '#FBEAF8', fg: '#9C0089' },
    declined: { fill: '#C50F1F', bg: '#FDECEE', fg: '#B00E1C' },
    done: { fill: '#0E6B0E', bg: '#E9F4E9', fg: '#0B5E0B' },
    idle: { fill: '#6B7380', bg: '#F1F3F5', fg: '#57606A' },
    held: { fill: '#3F4650', bg: '#EEF0F3', fg: '#3F4650' },
  },
  // Dark chip backgrounds are the fill mixed at 16% over the dark surface
  // (#171A1F), written out as hex so the contrast test measures what renders.
  dark: {
    moving: { fill: '#4FA3F0', bg: '#203040', fg: '#4FA3F0' },
    waiting: { fill: '#7B6BE6', bg: '#27273F', fg: '#ABA0F5' },
    blocked: { fill: '#E0612A', bg: '#372521', fg: '#F08A5D' },
    open: { fill: '#EE6FD3', bg: '#39283C', fg: '#EE6FD3' },
    declined: { fill: '#F47A84', bg: '#3A292F', fg: '#F47A84' },
    done: { fill: '#72D872', bg: '#26382C', fg: '#72D872' },
    idle: { fill: '#98A1AD', bg: '#2C3036', fg: '#98A1AD' },
    held: { fill: '#D3D8DE', bg: '#35383E', fg: '#D3D8DE' },
  },
};

/** Neutral surfaces and text (BRD 25 §5.1), for the default Neutral tone. */
export type NeutralTokens = {
  canvas: string;
  surface: string;
  surfaceRaised: string;
  surfaceSunken: string;
  border: string;
  divider: string;
  /** Input and select outlines: 3:1 on the surface (WCAG 1.4.11). */
  borderStrong: string;
  textPrimary: string;
  textSecondary: string;
  textTertiary: string;
};

export const neutrals: Record<Mode, NeutralTokens> = {
  light: {
    canvas: '#F6F7F9',
    surface: '#FFFFFF',
    surfaceRaised: '#FFFFFF',
    surfaceSunken: '#F1F3F5',
    border: '#E5E7EB',
    divider: '#EEF0F3',
    borderStrong: '#8A939F',
    textPrimary: '#1B1F24',
    textSecondary: '#57606A',
    textTertiary: '#646C78',
  },
  dark: {
    canvas: '#0E1014',
    surface: '#171A1F',
    surfaceRaised: '#1E2228',
    surfaceSunken: '#12151A',
    border: '#2A2F37',
    divider: '#23272E',
    borderStrong: '#6B7480',
    textPrimary: '#E8EAED',
    textSecondary: '#A8B0BA',
    textTertiary: '#8E97A3',
  },
};

/**
 * The default brand (Fluent 2 brand-80), in the accent shape theme.ts uses.
 * The user's accent setting replaces this slot only (D-25-7).
 */
export const brand: Record<Mode, { main: string; hover: string; pressed: string; contrastText: string; tint: string; tintForeground: string }> = {
  light: { main: '#0F6CBD', hover: '#115EA3', pressed: '#0C3B5E', contrastText: '#FFFFFF', tint: '#EBF3FC', tintForeground: '#0B5A9E' },
  dark: { main: '#4FA3F0', hover: '#6BB3F3', pressed: '#8AC3F5', contrastText: '#0E1014', tint: '#1A2A3B', tintForeground: '#8AC3F5' },
};

/**
 * Categorical chart palette (BRD 25 §5.3): at most five colours. A sixth or
 * later series folds into "Other". Teal and orchid are split by lightness
 * (darker teal, lighter orchid) because they share a lightness otherwise and
 * collapse for deuteranopes.
 */
export const chartCategorical: Record<Mode, readonly [string, string, string, string, string]> = {
  light: ['#2F6FDE', '#E07A1F', '#0F8A7C', '#D25AA8', '#2A2370'],
  dark: ['#4A86F0', '#F09A4A', '#1E8F81', '#F290D2', '#D9D4FF'],
};

export const chartOther: Record<Mode, string> = { light: '#8A939F', dark: '#6B7480' };

/** Sequential scale for the review heatmap; step 0 is "none" and stays neutral. */
export const heatScale: Record<Mode, readonly [string, string, string, string, string]> = {
  light: ['#EEF0F3', '#C7DDF5', '#8BB8EA', '#3F85D4', '#0F5AA6'],
  dark: ['#1E2228', '#1D3550', '#245487', '#3477C2', '#6BB3F3'],
};

/** 4px base spacing scale (BRD 25 §11). Only these values in dashboard components. */
export const spacing = [0, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64] as const;

/** Radius scale (Modern). Classic flattens every tier to 4 (FR-48). */
export const radius = { sm: 6, md: 8, lg: 12, xl: 16, pill: 999 } as const;

/** Motion (BRD 25 §8). All zeroed under prefers-reduced-motion. */
export const motion = {
  fast: 150,
  base: 200,
  slow: 250,
  ease: 'cubic-bezier(0.2, 0, 0, 1)',
  easeExit: 'cubic-bezier(0.4, 0, 1, 1)',
} as const;

/** Type scale (BRD 25 §4): size / English line height / Khmer line height, in px. */
export const typeScale = {
  display: { size: 32, line: 40, lineKm: 48, weight: 600 },
  numericXl: { size: 40, line: 44, lineKm: 48, weight: 600 },
  title: { size: 20, line: 28, lineKm: 34, weight: 600 },
  subtitle: { size: 16, line: 24, lineKm: 30, weight: 600 },
  body: { size: 14, line: 20, lineKm: 26, weight: 400 },
  caption: { size: 12, line: 16, lineKm: 22, weight: 400 },
  overline: { size: 11, line: 16, lineKm: 22, weight: 600 },
} as const;

/**
 * The existing stylesheet variables that carry a V25 neutral. Tones and high
 * contrast override these variables, so the V25 names (`--canvas`, `--surface`,
 * `--text-primary`, `--text-secondary`) are declared in global.css as aliases of
 * them (`--canvas: var(--page)`) rather than as second copies of the hex value.
 * `tokens.test.ts` checks the defaults below.
 */
export function aliasedNeutralDefaults(mode: Mode): Record<string, string> {
  const n = neutrals[mode];
  return {
    '--page': n.canvas,
    '--card': n.surface,
    '--foreground': n.textPrimary,
    '--muted-foreground': n.textSecondary,
    '--border': n.border,
  };
}

/** The V25 aliases global.css declares once, in `:root`. */
export const neutralAliases: Record<string, string> = {
  '--canvas': 'var(--page)',
  '--surface': 'var(--card)',
  '--text-primary': 'var(--foreground)',
  '--text-secondary': 'var(--muted-foreground)',
};

/**
 * The literal CSS variables global.css must carry for a mode, as name → value.
 * `tokens.test.ts` compares these against the stylesheet.
 */
export function cssVariables(mode: Mode): Record<string, string> {
  const n = neutrals[mode];
  const vars: Record<string, string> = {
    '--surface-raised': n.surfaceRaised,
    '--surface-sunken': n.surfaceSunken,
    '--divider': n.divider,
    '--border-strong': n.borderStrong,
    '--text-tertiary': n.textTertiary,
    '--chart-other': chartOther[mode],
  };
  for (const family of STATUS_FAMILIES) {
    const tone = statusTones[mode][family];
    vars[`--st-${family}`] = tone.fill;
    vars[`--st-${family}-bg`] = tone.bg;
    vars[`--st-${family}-fg`] = tone.fg;
  }
  chartCategorical[mode].forEach((color, index) => {
    vars[`--chart-${index + 1}`] = color;
  });
  heatScale[mode].forEach((color, index) => {
    vars[`--heat-${index}`] = color;
  });
  return vars;
}

/* ------------------------------------------------------------------------- */
/* BRD 26 — interface style tokens (D-26-1)                                   */
/* ------------------------------------------------------------------------- */

/**
 * The three interface styles, by their CSS id (`data-style` on `<html>`). The
 * stored wire values stay `MODERN`, `CLASSIC` and `LIQUID_GLASS` (Q-26-4).
 *
 *   modern   the calm flagship and the only default (D-26-7)
 *   classic  dense Fluent 2 for power users: 4px corners, tight shadows
 *   glass    expressive; glass only on floating layers (D-26-3)
 */
export type InterfaceStyle = 'modern' | 'classic' | 'glass';

export const INTERFACE_STYLES: readonly InterfaceStyle[] = ['modern', 'classic', 'glass'];

export type StyleTokens = {
  /** Buttons, inputs, selects (px). */
  radiusControl: number;
  radiusCard: number;
  radiusChip: number;
  radiusDialog: number;
  /** Menus, popovers, tooltips. */
  radiusMenu: number;
  /** Navigation selection shape. `rect` adds a 3px accent bar (Classic). */
  navShape: 'pill' | 'rect';
  /** Header anatomy (D-26-9). */
  header: 'greeting' | 'breadcrumbs';
  shadowRest: string;
  shadowHover: string;
  shadowOverlay: string;
  /** Border colour a clickable card takes on hover. */
  hoverBorder: string;
  cardBg: string;
  cardBorder: string;
  /** Sidebar, header, menus, dialogs, the hero card. Translucent only in Glass. */
  floatingBg: string;
  /** Glass over the plain app canvas only, where the backdrop is known (D-26-5). */
  floatingBgCanvas: string;
  blur: string;
  floatingBorder: string;
  /** Top-edge highlight, as an inset shadow. A no-op shadow outside Glass, so it can be combined. */
  highlight: string;
  /** Children of floating layers (D-26-4). */
  insetBg: string;
  /** Sidebar panel: sunken in Modern, raised in Classic, glass in Liquid Glass (§4). */
  sidebarBg: string;
  /**
   * Header background while the page is at the top. Modern and Liquid Glass sit
   * on the canvas until content scrolls under them, then take the floating
   * material; Classic is always solid (§4).
   */
  headerRestBg: string;
  /** Review heatmap cell corners (§4). */
  heatRadius: number;
  /** Tertiary text on a floating layer. Glass promotes it to secondary (D-26-5). */
  floatingTextTertiary: string;
  cardPad: string;
  cardPadPhone: string;
  /** Grid gaps: wide (≥ 1200px), medium, narrow (< 600px). */
  gap: readonly [string, string, string];
  zoneGap: string;
  motionFast: number;
  motionBase: number;
  motionSlow: number;
  ease: string;
  /** `1` means no press scale (Classic changes colour only). */
  pressScale: number;
  /** Chart gridline dash pattern; `none` is a solid line. */
  chartGridDash: string;
  chartBarRadius: number;
  chartTooltipBg: string;
  chartTooltipBlur: string;
  chartTooltipRadius: number;
  /**
   * The surface opacity behind `floatingBg` / `floatingBgCanvas`, for the
   * contrast test. `1` means solid.
   */
  floatingAlpha: number;
  floatingCanvasAlpha: number;
};

/** A shadow that draws nothing but can still sit in a comma-separated list. */
export const NO_SHADOW = '0 0 0 0 transparent';

const modernLight: StyleTokens = {
  radiusControl: 8,
  radiusCard: 12,
  radiusChip: 999,
  radiusDialog: 16,
  radiusMenu: 8,
  navShape: 'pill',
  header: 'greeting',
  shadowRest: '0 1px 2px rgba(16, 24, 40, 0.04), 0 1px 3px rgba(16, 24, 40, 0.06)',
  shadowHover: '0 4px 12px rgba(16, 24, 40, 0.08)',
  shadowOverlay: '0 12px 32px rgba(16, 24, 40, 0.14)',
  hoverBorder: 'var(--border-strong)',
  cardBg: 'var(--surface)',
  cardBorder: '1px solid var(--border)',
  floatingBg: 'var(--surface-raised)',
  floatingBgCanvas: 'var(--surface-raised)',
  blur: 'none',
  floatingBorder: '1px solid var(--border)',
  highlight: NO_SHADOW,
  insetBg: 'var(--surface-sunken)',
  sidebarBg: 'var(--sidebar)',
  headerRestBg: 'transparent',
  heatRadius: 3,
  floatingTextTertiary: neutrals.light.textTertiary,
  cardPad: '20px',
  cardPadPhone: '16px',
  gap: ['24px', '16px', '12px'],
  zoneGap: '32px',
  motionFast: 150,
  motionBase: 200,
  motionSlow: 250,
  ease: 'cubic-bezier(0.2, 0, 0, 1)',
  pressScale: 0.98,
  chartGridDash: '2 4',
  chartBarRadius: 4,
  chartTooltipBg: 'var(--surface-raised)',
  chartTooltipBlur: 'none',
  chartTooltipRadius: 8,
  floatingAlpha: 1,
  floatingCanvasAlpha: 1,
};

const classicLight: StyleTokens = {
  ...modernLight,
  radiusControl: 4,
  radiusCard: 4,
  radiusChip: 4,
  radiusDialog: 4,
  radiusMenu: 4,
  navShape: 'rect',
  header: 'breadcrumbs',
  sidebarBg: 'var(--surface-raised)',
  headerRestBg: 'var(--surface-raised)',
  heatRadius: 1,
  shadowRest: '0 0.6px 1.8px rgba(0, 0, 0, 0.10), 0 0.3px 0.9px rgba(0, 0, 0, 0.06)',
  shadowHover: '0 1.6px 3.6px rgba(0, 0, 0, 0.13), 0 0.3px 0.9px rgba(0, 0, 0, 0.10)',
  shadowOverlay: '0 6.4px 14.4px rgba(0, 0, 0, 0.13), 0 1.2px 3.6px rgba(0, 0, 0, 0.08)',
  cardPad: '12px',
  cardPadPhone: '12px',
  gap: ['12px', '12px', '8px'],
  zoneGap: '20px',
  motionFast: 100,
  motionBase: 150,
  motionSlow: 200,
  ease: 'cubic-bezier(0.33, 0, 0.67, 1)',
  pressScale: 1,
  chartGridDash: 'none',
  chartBarRadius: 0,
  chartTooltipRadius: 4,
};

const glassLight: StyleTokens = {
  ...modernLight,
  radiusControl: 12,
  radiusCard: 18,
  radiusChip: 999,
  radiusDialog: 24,
  radiusMenu: 14,
  shadowRest: '0 6px 18px -6px rgba(16, 24, 40, 0.12)',
  shadowHover: '0 10px 30px -6px rgba(16, 24, 40, 0.16)',
  shadowOverlay: '0 28px 64px -12px rgba(16, 24, 40, 0.26)',
  floatingBg: 'color-mix(in srgb, var(--surface) 88%, transparent)',
  floatingBgCanvas: 'color-mix(in srgb, var(--surface) 74%, transparent)',
  blur: 'blur(20px) saturate(1.4)',
  floatingBorder: '1px solid rgba(255, 255, 255, 0.55)',
  highlight: 'inset 0 1px 0 rgba(255, 255, 255, 0.35)',
  sidebarBg: 'color-mix(in srgb, var(--surface) 88%, transparent)',
  heatRadius: 4,
  floatingTextTertiary: 'var(--muted-foreground)',
  motionFast: 200,
  motionBase: 250,
  motionSlow: 350,
  ease: 'cubic-bezier(0.2, 0.9, 0.1, 1)',
  pressScale: 0.97,
  chartBarRadius: 6,
  chartTooltipBg: 'color-mix(in srgb, var(--surface) 88%, transparent)',
  chartTooltipBlur: 'blur(20px) saturate(1.4)',
  chartTooltipRadius: 14,
  floatingAlpha: 0.88,
  floatingCanvasAlpha: 0.74,
};

/**
 * `styleTokens[style][mode]` (BRD 26 §3). global.css carries the same values as
 * `--style-*` variables; `style-tokens.test.ts` keeps the two equal.
 */
export const styleTokens: Record<InterfaceStyle, Record<Mode, StyleTokens>> = {
  modern: {
    light: modernLight,
    dark: {
      ...modernLight,
      // The border carries the edge in dark mode.
      shadowRest: NO_SHADOW,
      shadowHover: NO_SHADOW,
      shadowOverlay: '0 12px 32px rgba(0, 0, 0, 0.50)',
      hoverBorder: '#363C46',
      floatingTextTertiary: neutrals.dark.textTertiary,
    },
  },
  classic: {
    light: classicLight,
    dark: {
      ...classicLight,
      shadowRest: '0 0.6px 1.8px rgba(0, 0, 0, 0.40)',
      shadowHover: '0 1.6px 3.6px rgba(0, 0, 0, 0.45)',
      shadowOverlay: '0 6.4px 14.4px rgba(0, 0, 0, 0.50)',
      floatingTextTertiary: neutrals.dark.textTertiary,
    },
  },
  glass: {
    light: glassLight,
    dark: {
      ...glassLight,
      shadowRest: '0 6px 18px -6px rgba(0, 0, 0, 0.45)',
      shadowHover: '0 10px 30px -6px rgba(0, 0, 0, 0.55)',
      shadowOverlay: '0 32px 72px -12px rgba(0, 0, 0, 0.70)',
      floatingBg: 'color-mix(in srgb, var(--surface-raised) 85%, transparent)',
      floatingBgCanvas: 'color-mix(in srgb, var(--surface-raised) 74%, transparent)',
      blur: 'blur(20px) saturate(1.2)',
      floatingBorder: '1px solid rgba(255, 255, 255, 0.10)',
      highlight: 'inset 0 1px 0 rgba(255, 255, 255, 0.06)',
      sidebarBg: 'color-mix(in srgb, var(--surface-raised) 85%, transparent)',
      chartTooltipBg: 'color-mix(in srgb, var(--surface-raised) 85%, transparent)',
      chartTooltipBlur: 'blur(20px) saturate(1.2)',
      floatingAlpha: 0.85,
    },
  },
};

/** The solid values glass falls back to (D-26-6), for the floating tokens only. */
export const glassFallback: Record<string, string> = {
  '--style-floating-bg': 'var(--surface-raised)',
  '--style-floating-bg-canvas': 'var(--surface-raised)',
  '--style-blur': 'none',
  '--style-floating-border': '1px solid var(--border)',
  '--style-highlight': NO_SHADOW,
  '--style-sidebar-bg': 'var(--surface-raised)',
  '--style-chart-tooltip-bg': 'var(--surface-raised)',
  '--style-chart-tooltip-blur': 'none',
};

const px = (value: number) => `${value}px`;
const ms = (value: number) => `${value}ms`;

/**
 * The `--style-*` variables one style × mode block in global.css must carry,
 * as name → value.
 */
export function styleCssVariables(style: InterfaceStyle, mode: Mode): Record<string, string> {
  const t = styleTokens[style][mode];
  return {
    '--style-radius-control': px(t.radiusControl),
    '--style-radius-card': px(t.radiusCard),
    '--style-radius-chip': px(t.radiusChip),
    '--style-radius-dialog': px(t.radiusDialog),
    '--style-radius-menu': px(t.radiusMenu),
    '--style-radius-nav': t.navShape === 'pill' ? '999px' : '4px',
    '--style-nav-bar': t.navShape === 'rect' ? '3px' : '0px',
    '--style-shadow-rest': t.shadowRest,
    '--style-shadow-hover': t.shadowHover,
    '--style-shadow-overlay': t.shadowOverlay,
    '--style-hover-border': t.hoverBorder,
    '--style-card-bg': t.cardBg,
    '--style-card-border': t.cardBorder,
    '--style-floating-bg': t.floatingBg,
    '--style-floating-bg-canvas': t.floatingBgCanvas,
    '--style-blur': t.blur,
    '--style-floating-border': t.floatingBorder,
    '--style-highlight': t.highlight,
    '--style-inset-bg': t.insetBg,
    '--style-sidebar-bg': t.sidebarBg,
    '--style-header-rest-bg': t.headerRestBg,
    '--style-heat-radius': px(t.heatRadius),
    '--style-floating-text-tertiary': t.floatingTextTertiary,
    '--style-card-pad': t.cardPad,
    '--style-card-pad-phone': t.cardPadPhone,
    '--style-gap-lg': t.gap[0],
    '--style-gap-md': t.gap[1],
    '--style-gap-sm': t.gap[2],
    '--style-zone-gap': t.zoneGap,
    '--style-motion-fast': ms(t.motionFast),
    '--style-motion-base': ms(t.motionBase),
    '--style-motion-slow': ms(t.motionSlow),
    '--style-ease': t.ease,
    '--style-press-scale': String(t.pressScale),
    '--style-chart-grid-dash': t.chartGridDash,
    '--style-chart-bar-radius': px(t.chartBarRadius),
    '--style-chart-tooltip-bg': t.chartTooltipBg,
    '--style-chart-tooltip-blur': t.chartTooltipBlur,
    '--style-chart-tooltip-radius': px(t.chartTooltipRadius),
  };
}

/** The CSS selector of each style × mode block, in cascade order. */
export function styleBlockSelector(style: InterfaceStyle, mode: Mode): string {
  if (style === 'modern') {
    // Modern is also what renders before the attribute is set.
    return mode === 'light'
      ? ':root:not([data-style]),\n:root[data-style="modern"]'
      : ':root[data-theme="dark"]:not([data-style]),\n:root[data-theme="dark"][data-style="modern"]';
  }
  return mode === 'light' ? `:root[data-style="${style}"]` : `:root[data-theme="dark"][data-style="${style}"]`;
}

/**
 * Legacy radius and shadow names, kept as aliases so existing CSS follows the
 * style (BRD 26 §3.1). `--radius-sm` stays a small tier (kbd, thead corners,
 * menu rows) capped at 6px.
 */
export const legacyStyleAliases: Record<string, string> = {
  '--radius': 'var(--style-radius-card)',
  '--radius-sm': 'min(var(--style-radius-control), 6px)',
  '--radius-md': 'var(--style-radius-control)',
  '--radius-lg': 'var(--style-radius-card)',
  '--radius-xl': 'var(--style-radius-dialog)',
  '--radius-pill': 'var(--style-radius-chip)',
  '--shadow-2': 'var(--style-shadow-rest)',
  '--shadow-4': 'var(--style-shadow-hover)',
  '--shadow-8': 'var(--style-shadow-overlay)',
  '--shadow-16': 'var(--style-shadow-overlay)',
  '--motion-fast': 'var(--style-motion-fast)',
  '--motion-base': 'var(--style-motion-base)',
  '--motion-slow': 'var(--style-motion-slow)',
  '--motion-ease': 'var(--style-ease)',
};
