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
