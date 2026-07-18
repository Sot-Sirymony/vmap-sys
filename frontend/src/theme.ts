import { createTheme } from '@mui/material/styles';

/**
 * Fluent 2 design tokens (Microsoft's Fluent Design System). Values match
 * Fluent's own published tokens rather than an invented palette — see
 * global.css's :root block for the same values as plain CSS custom
 * properties (used by a handful of non-MUI elements).
 *   --primary        #0078d4  Communication Blue (brand)
 *   --primary hover  #106ebe
 *   --primary pressed #005a9e
 *   --primary tint   #deecf9
 *   neutralForeground1 #242424  (body text)
 *   neutralForeground2 #616161  (secondary text)
 *   neutralStroke      #e1e1e1  (borders)
 *   neutralBackground3 #f5f5f5  (recessed panels, table headers)
 *   destructive        #d13438  Fluent "Danger"
 *
 * Font: Segoe UI Variable is Fluent's signature face. An unlayered
 * `:root { font-family: ... }` rule in global.css is what actually renders
 * (it beats the layered Tailwind `font-sans` class regardless of source
 * order, since unlayered CSS always wins over anything inside `@layer`) —
 * kept in sync with the stack below.
 */
/**
 * Status palette — one source of truth for every status hue in the app, shared
 * by StatusBadge and the dashboard's status charts so a "Completed" green is
 * the same green everywhere.
 *
 * Four chromatic slots carry meaning; the two neutral steps mean "no activity":
 *   blue    work is moving      IN_PROGRESS, ACTIVE, CONTACTED, SENT, REPLIED
 *   purple  waiting on someone  WAITING, FOLLOWED_UP
 *   orange  needs attention     BLOCKED, OPEN
 *   red     negative outcome    DECLINED
 *   green   done                COMPLETED, RESOLVED, CLOSED
 *   neutral not started / idle  the rest (two lightness steps so NOT_STARTED
 *                               and PAUSED stay distinct as adjacent chart bars)
 *
 * WAITING is purple, not amber. Amber-vs-orange (the previous WAITING/BLOCKED
 * pair) collapses to near-identical under red-green colorblindness — measured
 * ΔE 3.6 against a ΔE 12 target — so the two states that most need telling
 * apart were the two a colorblind user could not distinguish. Purple clears it.
 *
 * The remaining green↔orange pair sits at ΔE 8.9 (the 8–12 floor band), which
 * is only permissible because status is never encoded as color alone: every
 * badge carries its text label, and every chart has an axis label or legend.
 *
 * Hex values are Fluent's own tokens, matching the rest of this file.
 */
const NEUTRAL_IDLE = '#a19f9d'; // Fluent neutralForeground disabled-ish
const NEUTRAL_HELD = '#605e5c'; // Fluent neutralForeground3, darker than idle
const BLUE_MOVING = '#0078d4'; // Communication Blue (same as brand primary)
const PURPLE_WAITING = '#8764b8'; // Fluent purple
const ORANGE_ATTENTION = '#d83b01'; // Fluent severe-warning orange
const RED_NEGATIVE = '#d13438'; // Fluent Danger (same as palette.error)
const GREEN_DONE = '#107c10'; // Fluent Success

export const statusColors = {
  // WorkStatus — goals, steps, tasks
  NOT_STARTED: NEUTRAL_IDLE,
  IN_PROGRESS: BLUE_MOVING,
  WAITING: PURPLE_WAITING,
  BLOCKED: ORANGE_ATTENTION,
  PAUSED: NEUTRAL_HELD,
  COMPLETED: GREEN_DONE,

  // DreamStatus / LifecycleStatus
  IDEA: NEUTRAL_IDLE,
  ACTIVE: BLUE_MOVING,
  ARCHIVED: NEUTRAL_HELD,

  // PartnerStatus
  TO_CONTACT: NEUTRAL_IDLE,
  CONTACTED: BLUE_MOVING,
  DECLINED: RED_NEGATIVE,

  // ObstacleStatus
  OPEN: ORANGE_ATTENTION,
  RESOLVED: GREEN_DONE,
  ACCEPTED: NEUTRAL_HELD,

  // CommunicationStatus
  DRAFT: NEUTRAL_IDLE,
  SENT: BLUE_MOVING,
  FOLLOWED_UP: PURPLE_WAITING,
  REPLIED: BLUE_MOVING,
  CLOSED: GREEN_DONE,
} as const;

export type StatusToken = keyof typeof statusColors;

/**
 * Priority palette — an escalation scale, not a set of categories. Priority is
 * ordinal (Low < Medium < High < Critical), so each step both warms and darkens:
 * urgency should read as rising even before the label is read.
 *
 *   neutral  LOW       no urgency — deliberately colorless
 *   gold     MEDIUM
 *   orange   HIGH
 *   red      CRITICAL
 *
 * Each step darkens hard (L 0.70 → 0.77 → 0.59 → 0.48) rather than only shifting
 * hue. That is what makes MEDIUM and HIGH survive colorblindness: warm hues
 * collapse into one another under deuteranopia, so the lightness gap is doing
 * the work — measured ΔE 26.2, against ΔE 12 as the target. (The status palette
 * above solves the same problem a different way, by moving WAITING off the warm
 * ramp entirely.)
 *
 * LOW and MEDIUM sit under 3:1 against a white surface. That is allowed here for
 * the same reason it is in the design system's own severity palette: priority is
 * never encoded as color alone — every badge carries its text label, and the
 * dashboard's priority chart has axis labels.
 *
 * Kept distinct from the status hues above so a HIGH-priority chip never reads
 * as a BLOCKED chip: different orange (#ca5010 vs #d83b01), different red
 * (#a4262c vs #d13438).
 */
export const priorityColors = {
  LOW: NEUTRAL_IDLE,
  MEDIUM: '#eaa300', // Fluent gold
  HIGH: '#ca5010', // Fluent orange, shade
  CRITICAL: '#a4262c', // Fluent red, shade
} as const;

export type PriorityToken = keyof typeof priorityColors;

// For a key that belongs to neither palette — an enum value added on the backend
// before the frontend knows about it. Renders as "no state" rather than picking
// an arbitrary hue that would imply a meaning it doesn't have.
export const neutralFallback = NEUTRAL_IDLE;

/**
 * BR-15 (V2.1): every color the app renders lives in this file or global.css.
 * The exports below relocate values that page/component files used to
 * hardcode. Values are moved verbatim — recoloring (e.g. the partner chart's
 * WAITING orange, audit finding V-02, or the non-Fluent moonshot violet) is
 * FR-25/FR-27 work, not this file's.
 */

// Fluent neutrals shared by tiles, icons, and chart fallbacks.
export const fluentNeutrals = {
  fg2: '#616161', // neutralForeground2 — secondary text
  fgDisabled: '#8a8886',
  stroke: '#e1e1e1',
  bg3: '#f5f5f5', // recessed panels
} as const;

// Semantic tile tints (Fluent Success/Warning/Danger tint+foreground pairs)
// used by dashboard stat tiles and the attention panel.
export const semanticTints = {
  neutral: { bg: '#f5f5f5', fg: '#616161' },
  positive: { bg: '#dff6dd', fg: '#107c10' },
  warning: { bg: '#fdece3', fg: '#d83b01' },
  critical: { bg: '#fde7e9', fg: '#d13438' },
} as const;

// Moonshot marker (FR-14). Audit note: violet is not a Fluent token and the
// deep shade fails contrast on dark surfaces — revisit under FR-26.4.
export const moonshotViolet = '#7c3aed';
export const moonshotVioletDeep = '#6b21a8';
export const moonshotTint = '#f3edfd';

// Dashboard chart palettes. FR-25.3 (audit V-02): the partner pipeline chart
// now draws each stage in exactly the color its status badge uses — the
// one-source-of-truth rule beats per-chart hue tuning. CONTACTED and ACTIVE
// share the "work is moving" blue by design; the legend's counts tell them
// apart, and WAITING is the same colorblind-safe purple as everywhere else.
export const partnerPipelineColors = {
  TO_CONTACT: statusColors.TO_CONTACT,
  CONTACTED: statusColors.CONTACTED,
  ACTIVE: statusColors.ACTIVE,
  WAITING: statusColors.WAITING,
  DECLINED: statusColors.DECLINED,
  COMPLETED: statusColors.COMPLETED,
} as const;

export const obstacleTypeColors: Record<string, string> = {
  KNOWLEDGE: '#004578',
  SKILL: '#005a9e',
  TIME: '#0063b1',
  MONEY: '#106ebe',
  MOTIVATION: '#0078d4',
  PARTNER: '#2b88d8',
  SYSTEM: '#4ba0e1',
  DECISION: '#71afe5',
  OTHER: '#8a8886',
  OTHER_TYPES: '#e1e1e1',
};

// Review heatmap intensity ramp (FR-25.3): neutral stroke for "none", then
// the Fluent green family up to the same Success green the status palette
// uses for Completed — intensity reads as "increasingly done".
export const heatmapLevelColors = ['#e1e1e1', '#dff0df', '#6bbf6c', '#107c10', '#0b5a0b'] as const;

// "Depth, applied to data": Fluent's blue ramp for charts whose categories
// carry no inherent meaning, dark → light, plus the brand blue for
// single-series charts.
export const chartBlueRamp = ['#005a9e', '#0078d4', '#2b88d8', '#71afe5', '#c7e0f4', '#deecf9'] as const;
export const chartPrimary = BLUE_MOVING;

// FR-29.5: per-Vision-Area identity dots. A fixed mini-palette of Fluent
// hues deliberately distinct from the status and priority palettes (BR-14)
// — an area's dot never reads as a state. Index by `area.id % length`.
export const visionAreaDotPalette = [
  '#0078d4', // blue
  '#038387', // teal
  '#8764b8', // purple
  '#498205', // green (Fluent "forest")
  '#c239b3', // magenta
  '#986f0b', // brass
  '#005b70', // steel
  '#8e562e', // bronze
] as const;

export function visionAreaDotColor(areaId: number): string {
  return visionAreaDotPalette[Math.abs(areaId) % visionAreaDotPalette.length];
}

/**
 * FR-18.3 — curated accent choices. Each accent ships pre-validated light and
 * dark values (main/hover/pressed on the brand ramp, plus the tint pair used
 * for selected-state washes), so contrast never depends on user judgment.
 * The status/priority palettes above are deliberately NOT part of this map
 * (BR-14): an accent choice must never change what "Completed" green means.
 */
export type AccentId = 'blue' | 'teal' | 'purple' | 'green' | 'orange';

type AccentSet = {
  main: string;
  hover: string;
  pressed: string;
  contrastText: string;
  tint: string;
  tintForeground: string;
};

export const accentOptions: Record<AccentId, { label: string; light: AccentSet; dark: AccentSet }> = {
  blue: {
    label: 'Blue',
    light: { main: '#0078d4', hover: '#106ebe', pressed: '#005a9e', contrastText: '#ffffff', tint: '#deecf9', tintForeground: '#005a9e' },
    dark: { main: '#4ba0e1', hover: '#71afe5', pressed: '#2b88d8', contrastText: '#1b1a19', tint: '#0e3a5c', tintForeground: '#9ec9ec' },
  },
  teal: {
    label: 'Teal',
    light: { main: '#038387', hover: '#159195', pressed: '#026d70', contrastText: '#ffffff', tint: '#d5f0f0', tintForeground: '#026d70' },
    dark: { main: '#58c2c6', hover: '#7ad4d8', pressed: '#31a8ad', contrastText: '#1b1a19', tint: '#0c3536', tintForeground: '#9be0e2' },
  },
  purple: {
    label: 'Purple',
    light: { main: '#8764b8', hover: '#9d7fc6', pressed: '#6b4fa0', contrastText: '#ffffff', tint: '#ece5f6', tintForeground: '#5a4180' },
    dark: { main: '#a68ccc', hover: '#bda7d9', pressed: '#8764b8', contrastText: '#1b1a19', tint: '#2b2140', tintForeground: '#cdbce6' },
  },
  green: {
    label: 'Green',
    light: { main: '#107c10', hover: '#2d8f2d', pressed: '#0b5a0b', contrastText: '#ffffff', tint: '#dff0df', tintForeground: '#0b5a0b' },
    dark: { main: '#6bbf6c', hover: '#8ccf8d', pressed: '#4aa74c', contrastText: '#1b1a19', tint: '#16301c', tintForeground: '#a4d7a6' },
  },
  orange: {
    label: 'Orange',
    light: { main: '#ca5010', hover: '#d86b2b', pressed: '#a3400c', contrastText: '#ffffff', tint: '#f8e3d7', tintForeground: '#8a3a0e' },
    dark: { main: '#df8e57', hover: '#e8a97e', pressed: '#ca5010', contrastText: '#1b1a19', tint: '#3a2415', tintForeground: '#f0c0a4' },
  },
};

export type Density = 'comfortable' | 'compact';

/**
 * Builds the MUI theme for a color mode (O-2 dark mode).
 *
 * Dark values are Fluent's dark-theme tokens, not inverted light ones:
 *   neutralBackground1 dark  #1b1a19  (page)
 *   surface / card dark      #252423
 *   neutralForeground1 dark  #f3f2f1
 *   neutralForeground2 dark  #a19f9d
 *   neutralStroke dark       #3b3a39
 *   brand (dark-legible)     #4ba0e1  (a lighter step of the Communication
 *                            Blue ramp — #0078d4 falls under 3:1 on #252423)
 *
 * The status/priority palettes above are shared with charts and badges and
 * stay identical in both modes: they are mid-tone hues chosen to survive on
 * either surface, and a "Completed" green that changed between modes would
 * break the one-source-of-truth rule they exist for.
 */
export function buildTheme(mode: 'light' | 'dark', accent: AccentId = 'blue', density: Density = 'comfortable') {
  const dark = mode === 'dark';
  const brand = accentOptions[accent][mode];
  const border = dark ? '#3b3a39' : '#e1e1e1';

  return createTheme({
    palette: {
      mode,
      background: {
        default: dark ? '#1b1a19' : '#ffffff', // --background
        paper: dark ? '#252423' : '#ffffff', // --card / --popover
      },
      text: {
        primary: dark ? '#f3f2f1' : '#242424', // --foreground (Fluent neutralForeground1)
        secondary: dark ? '#a19f9d' : '#616161', // --muted-foreground (Fluent neutralForeground2)
      },
      primary: {
        main: brand.main, // --primary (the chosen accent's ramp, FR-18.3)
        dark: brand.pressed,
        light: brand.hover,
        contrastText: brand.contrastText, // --primary-foreground
      },
      secondary: {
        main: dark ? '#323130' : '#f5f5f5', // --secondary / --muted (Fluent neutralBackground3)
        contrastText: dark ? '#f3f2f1' : '#242424', // --secondary-foreground
      },
      error: {
        main: dark ? '#e37d80' : '#d13438', // --destructive (Fluent Danger, lightened for dark)
      },
      divider: border, // --border / --input (Fluent neutralStroke)
    },
    shape: {
      borderRadius: 4, // --radius: Fluent's 4px corner radius
    },
    // FR-18.4: Compact narrows the spacing base every sx/gap multiplier builds
    // on; the table/card paddings in global.css follow via [data-density].
    spacing: density === 'compact' ? 6 : 8,
    // FR-18.1: no silent MUI defaults — these ARE the default values, stated
    // explicitly so the theme is the single place they can ever change, and
    // global.css media queries reference the same numbers (600/900).
    breakpoints: {
      values: { xs: 0, sm: 600, md: 900, lg: 1200, xl: 1536 },
    },
    // FR-20.4 motion tokens: three tiers only — fast (hover feedback), base
    // (open/close), slow (layout shifts). global.css exposes the same values
    // as --motion-fast/--motion-base/--motion-slow for non-MUI elements, and
    // its prefers-reduced-motion block zeroes every animation for users who
    // asked for that (BR-19).
    transitions: {
      duration: {
        shortest: 100,
        shorter: 100,
        short: 180,
        standard: 180,
        complex: 250,
        enteringScreen: 180,
        leavingScreen: 100,
      },
    },
    // The toast stack in global.css sits on this same scale (--z-toast: 1400,
    // the snackbar tier) instead of inventing its own number.
    zIndex: {
      appBar: 1100,
      drawer: 1200,
      modal: 1300,
      snackbar: 1400,
      tooltip: 1500,
    },
    // FR-20.1 type ramp — the app's entire scale, 6 sizes (M-7 target ≤ 9).
    // global.css mirrors these as --font-* variables for non-MUI text; both
    // must change together. Semantics: h1 = page title (exactly one per
    // page — axe `page-has-heading-one`), h2 = section/panel title, h3 =
    // card/column title, body1 = default text, body2 = secondary/dense text
    // (tables), caption = metadata (codes, dates, hints).
    typography: {
      fontFamily: '"Segoe UI Variable", "Segoe UI", -apple-system, BlinkMacSystemFont, Roboto, Helvetica, Arial, sans-serif',
      h1: { fontSize: '1.5rem', fontWeight: 600, lineHeight: 1.3 },
      h2: { fontSize: '1.25rem', fontWeight: 600, lineHeight: 1.35 },
      h3: { fontSize: '1rem', fontWeight: 600, lineHeight: 1.4 },
      h4: { fontSize: '1rem', fontWeight: 600, lineHeight: 1.4 },
      h5: { fontSize: '1rem', fontWeight: 600, lineHeight: 1.4 },
      h6: { fontSize: '1rem', fontWeight: 600, lineHeight: 1.4 },
      subtitle1: { fontSize: '0.9rem', fontWeight: 600, lineHeight: 1.4 },
      subtitle2: { fontSize: '0.875rem', fontWeight: 600, lineHeight: 1.4 },
      body1: { fontSize: '0.9rem', lineHeight: 1.5 },
      body2: { fontSize: '0.875rem', lineHeight: 1.45 },
      caption: { fontSize: '0.75rem', lineHeight: 1.4 },
      overline: { fontSize: '0.75rem', fontWeight: 600, lineHeight: 1.4 },
    },
    components: {
      // MUI's contained Button defaults to uppercase text + a drop shadow;
      // the current design is flat, sentence-case, and semibold.
      MuiButton: {
        defaultProps: { disableElevation: true },
        styleOverrides: {
          root: { textTransform: 'none', fontWeight: 600 },
        },
      },
      // MUI's Chip has its own hardcoded pill radius (~16px) that doesn't
      // follow theme.shape.borderRadius — override it directly so badges get
      // Fluent's flatter 4px corners instead of a Material-style pill.
      MuiChip: {
        styleOverrides: {
          root: { fontWeight: 700, borderRadius: 4 },
          label: { fontWeight: 700 },
        },
      },
      // MUI's default Card elevation is a generic Material drop-shadow. Fluent
      // cards instead pair a thin neutral-stroke border with its own shadow
      // scale (--shadow-4 from global.css) — elevation is disabled here so
      // that shadow doesn't stack on top of this one.
      MuiCard: {
        defaultProps: { elevation: 0 },
        styleOverrides: {
          root: {
            border: `1px solid ${border}`,
            boxShadow: '0 1.6px 3.6px rgba(0,0,0,0.10), 0 0.3px 0.9px rgba(0,0,0,0.06)',
            backgroundImage: 'none', // MUI dark mode adds an elevation overlay gradient; Fluent surfaces are flat
          },
        },
      },
      // Dialog defaults to MUI's heaviest Material elevation (24) — replaced
      // with Fluent's own top elevation tier (--shadow-16), since a modal is
      // the most "raised" surface in the app and should read as clearly above
      // everything else, not just have a bigger version of the Card shadow.
      MuiDialog: {
        styleOverrides: {
          paper: {
            boxShadow: '0 6.4px 14.4px rgba(0,0,0,0.13), 0 1.2px 3.6px rgba(0,0,0,0.08)',
            backgroundImage: 'none',
          },
        },
      },
      // Fluent's focus indicator is a visible outer ring around the whole
      // control, not just a thicker border — MUI's default focus behavior
      // already colors the border via palette.primary, so this adds the ring
      // on top rather than replacing it. Covers both TextField and Select,
      // since outlined Select renders through OutlinedInput internally.
      MuiOutlinedInput: {
        styleOverrides: {
          root: {
            '&.Mui-focused': {
              outline: `2px solid ${brand.main}`,
              outlineOffset: '1px',
            },
          },
        },
      },
    },
  });
}

const theme = buildTheme('light');

export default theme;
