import { alpha, createTheme } from '@mui/material/styles';
import { tr } from './i18n/translate';
import { brand as v25Brand, neutrals, statusTones, styleTokens, type InterfaceStyle, type StatusFamily } from './design/tokens';

/**
 * BRD 25 (D-25-6): colour values come from `design/tokens.ts`, the one token
 * source shared with global.css and the charts. This file maps them onto MUI and
 * onto the app's status, priority and accent vocabularies.
 *
 *   brand (default accent)  #0F6CBD light / #4FA3F0 dark   Fluent 2 brand-80
 *   body text               #1B1F24 light / #E8EAED dark
 *   secondary text          #57606A light / #A8B0BA dark
 *   borders                 #E5E7EB light / #2A2F37 dark
 *   page canvas             #F6F7F9 light / #0E1014 dark
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
 * Each status belongs to one meaning family (BRD 25 §5.2, D-25-4):
 *   moving   blue    IN_PROGRESS, ACTIVE, CONTACTED, AGREED, SENT, REPLIED, PLANNED
 *   waiting  indigo  WAITING, FOLLOWED_UP, IN_REVIEW
 *   blocked  orange  BLOCKED (work only)
 *   open     berry   OPEN (obstacles, issue reports)
 *   declined red     DECLINED
 *   done     green   COMPLETED, RESOLVED, CLOSED
 *   idle     grey    NOT_STARTED, IDEA, TO_CONTACT, DRAFT
 *   held     slate   PAUSED, ARCHIVED, ACCEPTED, WONT_FIX (a darker step than
 *                    idle in light mode, lighter in dark, so the two stay
 *                    distinct as adjacent chart bars)
 *
 * V25 fixed two faults in the V24 palette:
 *   - Orange meant both BLOCKED and OPEN. OPEN is now berry.
 *   - WAITING purple (#8764b8) and IN_PROGRESS blue (#0078d4) were ΔE ~1–2
 *     apart for red-green colour blindness. WAITING is now an indigo split from
 *     the blue by lightness, and BLOCKED is split from COMPLETED the same way.
 *     Every pair of work statuses now clears ΔE 10 (OKLab, Machado protan and
 *     deutan); `design/tokens.test.ts` enforces it.
 *
 * Status is still never encoded as colour alone: every badge carries its label.
 *
 * Unlike V24, the palette is mode-specific: dark mode uses lighter fills that
 * keep 3:1 on the dark surface. `statusColor()` picks the right one.
 */
const STATUS_FAMILY = {
  // WorkStatus — goals, steps, tasks
  NOT_STARTED: 'idle',
  IN_PROGRESS: 'moving',
  WAITING: 'waiting',
  BLOCKED: 'blocked',
  PAUSED: 'held',
  COMPLETED: 'done',

  // DreamStatus / LifecycleStatus
  IDEA: 'idle',
  ACTIVE: 'moving',
  ARCHIVED: 'held',

  // PartnerStatus
  TO_CONTACT: 'idle',
  CONTACTED: 'moving',
  AGREED: 'moving',
  DECLINED: 'declined',

  // ObstacleStatus
  OPEN: 'open',
  RESOLVED: 'done',
  ACCEPTED: 'held',

  // CommunicationStatus
  DRAFT: 'idle',
  SENT: 'moving',
  FOLLOWED_UP: 'waiting',
  REPLIED: 'moving',
  CLOSED: 'done',

  // IssueReportStatus (FR-38) — OPEN/IN_PROGRESS/RESOLVED/CLOSED reuse the
  // families above; these three are the states unique to the triage lifecycle.
  IN_REVIEW: 'waiting',
  PLANNED: 'moving',
  WONT_FIX: 'held',
} as const satisfies Record<string, StatusFamily>;

export type StatusToken = keyof typeof STATUS_FAMILY;

/** The meaning family of a status, or undefined for an enum this build does not know. */
export function statusFamily(token: string): StatusFamily | undefined {
  return (STATUS_FAMILY as Record<string, StatusFamily>)[token];
}

function statusFills(mode: 'light' | 'dark'): Record<StatusToken, string> {
  const tones = statusTones[mode];
  return Object.fromEntries(
    Object.entries(STATUS_FAMILY).map(([token, family]) => [token, tones[family].fill]),
  ) as Record<StatusToken, string>;
}

/** Light-mode status fills. */
export const statusColors = statusFills('light');
/** Dark-mode status fills (BRD 25). */
export const statusColorsDark = statusFills('dark');

const NEUTRAL_IDLE = statusTones.light.idle.fill;
const BLUE_MOVING = statusTones.light.moving.fill;

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
 * as a BLOCKED chip. V25: BLOCKED moved to a brighter orange (#E46C1E light,
 * #E0612A dark), which the old HIGH (#ca5010) sat only ΔE 5.9 from in dark mode,
 * so HIGH is now a deeper rust: ΔE ≥ 9.0 from BLOCKED in both modes and from
 * CRITICAL, and well clear of MEDIUM, under protan/deutan simulation (OKLab,
 * Machado), and 5.18:1 on white. `design/tokens.test.ts` enforces the BLOCKED
 * and CRITICAL distances.
 */
export const priorityColors = {
  LOW: NEUTRAL_IDLE,
  MEDIUM: '#eaa300', // Fluent gold
  HIGH: '#C2410A', // rust (V25)
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

/**
 * FR-43 — the five-shade semantic palette and the grey ramp.
 *
 * Each family runs lighter → light → main → dark → darker, and the shades are
 * NOT interchangeable. This is the part that is easy to get wrong: `main` is an
 * *icon and fill* colour, not a button colour. Measured against white text, the
 * mains land at 1.90:1 (warning), 2.28:1 (success), 2.37:1 (info) and 3.17:1
 * (error) — every one of them illegible. Even `dark` is not universally safe:
 * success-dark is 4.22:1 and warning-dark 4.00:1, both short of 4.5:1.
 *
 * So the roles are fixed, and `semanticTints` below encodes them:
 *   lighter → tint background
 *   main    → icon, chart fill, the coloured dot
 *   darker  → the text sitting on the tint
 *
 * The grey ramp splits at 600: 50–500 are surfaces, borders and dividers
 * (grey-300 is 1.29:1 on white — a boundary, not text), while 600–900 carry
 * text (grey-600 is 4.88:1, exactly a secondary-text colour).
 */
export const palette = {
  primary: { lighter: '#FFE3D5', light: '#FFC1AC', main: '#FF3030', dark: '#B71833', darker: '#7A0930' },
  secondary: { lighter: '#EFD8FF', light: '#C884FF', main: '#8E33FF', dark: '#5119B7', darker: '#27097A' },
  info: { lighter: '#CAFDF5', light: '#61F3F3', main: '#00B8D9', dark: '#006C9C', darker: '#003768' },
  success: { lighter: '#D3FCD2', light: '#77ED8B', main: '#22C55E', dark: '#118D57', darker: '#065E49' },
  warning: { lighter: '#FFF5CC', light: '#FFD666', main: '#FFAB00', dark: '#B76E00', darker: '#7A4100' },
  error: { lighter: '#FFE9D5', light: '#FFAC82', main: '#FF5630', dark: '#B71D18', darker: '#7A0916' },
} as const;

export const grey = {
  50: '#FCFDFD',
  100: '#F9FAFB',
  200: '#F4F6F8',
  300: '#DFE3E8',
  400: '#C4CDD5',
  500: '#919EAB',
  600: '#637381',
  700: '#454F5B',
  800: '#1C252E',
  900: '#141A21',
} as const;

// Fluent neutrals shared by tiles, icons, and chart fallbacks.
export const fluentNeutrals = {
  fg2: '#616161', // neutralForeground2 — secondary text
  fgDisabled: '#8a8886',
  stroke: '#e1e1e1',
  bg3: '#f5f5f5', // recessed panels
} as const;

/**
 * Semantic tile tints used by the dashboard stat tiles and the attention panel.
 *
 * FR-43 re-points these at the palette above, using each family's documented
 * roles: `lighter` as the wash and `darker` as the text on it. The previous
 * pairs put a `main`-weight hue on a pale tint (e.g. #d83b01 on #fdece3, 4.1:1);
 * every pair below clears 6.8:1, because the text now comes from the end of the
 * ramp meant to carry it.
 */
export const semanticTints = {
  neutral: { bg: grey[200], fg: grey[700] },
  positive: { bg: palette.success.lighter, fg: palette.success.darker },
  warning: { bg: palette.warning.lighter, fg: palette.warning.darker },
  critical: { bg: palette.error.lighter, fg: palette.error.darker },
  info: { bg: palette.info.lighter, fg: palette.info.darker },
} as const;

// Moonshot marker (FR-14). Audit note: violet is not a Fluent token and the
// deep shade fails contrast on dark surfaces — revisit under FR-26.4.
export const moonshotViolet = '#7c3aed';
export const moonshotVioletDeep = '#6b21a8';
export const moonshotTint = '#f3edfd';

// BRD 25: the dashboard's pie palette (FR-41), green heatmap ramp, blue chart
// ramp and partner pipeline colours were removed with the charts that used
// them. Dashboard charts now take their colours from design/tokens.ts
// (`chartCategorical`, `heatScale`, the status families) through
// components/dashboard/charts/chartTheme.tsx.

// The brand blue for single-series charts (the reports still use it).
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
export type AccentId =
  | 'blue'
  | 'teal'
  | 'purple'
  | 'green'
  | 'orange'
  // FR-39.2 additions.
  | 'magenta'
  | 'red'
  | 'brass'
  | 'steel'
  | 'pink'
  // FR-43 additions, derived from the supplied primary/secondary ramps.
  | 'vermilion'
  | 'violet'
  // The royal blue from the Stitch project's DESIGN.md style guide.
  | 'cobalt'
  // Ten more hues, spanning the wheel plus two neutrals-with-a-cast.
  | 'indigo'
  | 'sky'
  | 'emerald'
  | 'olive'
  | 'amber'
  | 'rose'
  | 'fuchsia'
  | 'graphite'
  | 'coffee'
  | 'navy';

type AccentSet = {
  main: string;
  hover: string;
  pressed: string;
  contrastText: string;
  tint: string;
  tintForeground: string;
};

export const accentOptions: Record<AccentId, { label: string; light: AccentSet; dark: AccentSet }> = {
  // BRD 25: the default accent is Fluent 2 brand-80, from design/tokens.ts.
  blue: {
    label: tr('Blue'),
    light: { ...v25Brand.light },
    dark: { ...v25Brand.dark },
  },
  teal: {
    label: tr('Teal'),
    light: { main: '#038387', hover: '#037377', pressed: '#026265', contrastText: '#ffffff', tint: '#d5f0f0', tintForeground: '#026d70' },
    dark: { main: '#58c2c6', hover: '#76cdd0', pressed: '#419093', contrastText: '#1b1a19', tint: '#0c3536', tintForeground: '#9be0e2' },
  },
  purple: {
    label: tr('Purple'),
    light: { main: '#8764b8', hover: '#7758a2', pressed: '#654b8a', contrastText: '#ffffff', tint: '#ece5f6', tintForeground: '#5a4180' },
    dark: { main: '#a68ccc', hover: '#b6a1d5', pressed: '#927bb4', contrastText: '#1b1a19', tint: '#2b2140', tintForeground: '#cdbce6' },
  },
  green: {
    label: tr('Green'),
    light: { main: '#107c10', hover: '#0e6d0e', pressed: '#0c5d0c', contrastText: '#ffffff', tint: '#dff0df', tintForeground: '#0b5a0b' },
    dark: { main: '#6bbf6c', hover: '#86cb86', pressed: '#539554', contrastText: '#1b1a19', tint: '#16301c', tintForeground: '#a4d7a6' },
  },
  orange: {
    label: tr('Orange'),
    light: { main: '#ca5010', hover: '#b2460e', pressed: '#983c0c', contrastText: '#ffffff', tint: '#f8e3d7', tintForeground: '#8a3a0e' },
    dark: { main: '#df8e57', hover: '#e5a275', pressed: '#b77447', contrastText: '#1b1a19', tint: '#3a2415', tintForeground: '#f0c0a4' },
  },
  // FR-39.2: five more accents, same shape and same guarantee. Every value
  // below was contrast-checked before being written down: each of main, hover,
  // and pressed clears 4.5:1 against its own contrastText, and each tint pair
  // clears 4.5:1 — in both modes.
  //
  // Note the direction of the light ramp: hover and pressed get progressively
  // DARKER, not lighter. That is Fluent's own convention — visible in Blue
  // above, where #0078d4 → #106ebe → #005a9e descends in lightness — and going
  // the other way would drop contrast exactly when the user is interacting.
  magenta: {
    label: tr('Magenta'),
    light: { main: '#b4009e', hover: '#9c0089', pressed: '#8a0079', contrastText: '#ffffff', tint: '#f8e0f4', tintForeground: '#8a0079' },
    dark: { main: '#e06ecd', hover: '#e98fd8', pressed: '#cf5cbd', contrastText: '#1b1a19', tint: '#3d0f36', tintForeground: '#f3b6e8' },
  },
  red: {
    label: tr('Red'),
    light: { main: '#c4314b', hover: '#ad2942', pressed: '#9b2033', contrastText: '#ffffff', tint: '#fde7e9', tintForeground: '#9b2033' },
    dark: { main: '#e8737f', hover: '#ef9199', pressed: '#d96370', contrastText: '#1b1a19', tint: '#3f1418', tintForeground: '#f5b8be' },
  },
  brass: {
    label: tr('Brass'),
    light: { main: '#8e6a0b', hover: '#7d5d0a', pressed: '#6b5008', contrastText: '#ffffff', tint: '#f7ecd0', tintForeground: '#6b5008' },
    dark: { main: '#d4b158', hover: '#e0c37b', pressed: '#b8963f', contrastText: '#1b1a19', tint: '#332708', tintForeground: '#e8d69b' },
  },
  steel: {
    label: tr('Steel'),
    light: { main: '#005b70', hover: '#004d5f', pressed: '#003a49', contrastText: '#ffffff', tint: '#d6ebf1', tintForeground: '#00485a' },
    dark: { main: '#5ba7bd', hover: '#7dbccd', pressed: '#43909f', contrastText: '#1b1a19', tint: '#0a2c36', tintForeground: '#a3d3e0' },
  },
  // FR-43: the supplied primary and secondary ramps, offered as accents rather
  // than as the app's default primary. The reference's #FF3030 measures 3.67:1
  // against white text, so it cannot be a button colour; light mode therefore
  // takes `main` from the dark end of the ramp and dark mode from the light end
  // — the same construction every accent above uses.
  //
  // Vermilion is deliberately opt-in, never the default: the app already spends
  // red on Critical, Declined, and destructive actions, and a red Save button
  // beside a red Delete button is a hazard whatever the measured ΔE says.
  vermilion: {
    label: tr('Vermilion'),
    light: { main: '#B71833', hover: '#9c142c', pressed: '#7A0930', contrastText: '#ffffff', tint: '#FFE3D5', tintForeground: '#7A0930' },
    dark: { main: '#FFC1AC', hover: '#FFE3D5', pressed: '#FF8A7A', contrastText: '#1b1a19', tint: '#4a1220', tintForeground: '#FFC1AC' },
  },
  violet: {
    label: tr('Violet'),
    light: { main: '#8E33FF', hover: '#7522e0', pressed: '#5119B7', contrastText: '#ffffff', tint: '#EFD8FF', tintForeground: '#27097A' },
    dark: { main: '#C884FF', hover: '#EFD8FF', pressed: '#A85CFF', contrastText: '#1b1a19', tint: '#27097A', tintForeground: '#EFD8FF' },
  },
  pink: {
    label: tr('Pink'),
    light: { main: '#b8306e', hover: '#a32860', pressed: '#931f56', contrastText: '#ffffff', tint: '#fce4f1', tintForeground: '#931f56' },
    dark: { main: '#eb84b6', hover: '#f2a2c8', pressed: '#d1699b', contrastText: '#1b1a19', tint: '#3d1229', tintForeground: '#f7bdd8' },
  },
  // The Stitch DESIGN.md's "Vibrant Royal Blue". The guide names #3B82F6 as the
  // accent and #2563EB for solid CTAs; #3B82F6 measures 3.68:1 against white,
  // so — exactly like Vermilion — light mode takes `main` from the CTA end of
  // the ramp and keeps the vibrant value as dark mode's pressed state. The
  // light tint is the guide's segmented-control blue (#D1E9FE). All eight
  // pairs measured before being written down: 5.17/6.70/8.72/6.98 light,
  // 6.84/9.64/4.73/8.42 dark.
  cobalt: {
    label: tr('Cobalt'),
    light: { main: '#2563EB', hover: '#1D4ED8', pressed: '#1E40AF', contrastText: '#ffffff', tint: '#D1E9FE', tintForeground: '#1E40AF' },
    dark: { main: '#60A5FA', hover: '#93C5FD', pressed: '#3B82F6', contrastText: '#1b1a19', tint: '#16294A', tintForeground: '#A8C7FA' },
  },
  // Ten more accents, same construction and the same guarantee as everything
  // above: every main/hover/pressed clears 4.5:1 against its contrastText and
  // every tint pair clears 4.5:1, in both modes, with light-mode ramps
  // descending in lightness (Fluent's convention — see the FR-39.2 note).
  // All 80 pairs were measured before being written down; the tightest is
  // indigo's dark pressed at 5.07:1. theme.test.ts enforces every one.
  indigo: {
    label: tr('Indigo'),
    light: { main: '#4F46E5', hover: '#4338CA', pressed: '#3730A3', contrastText: '#ffffff', tint: '#E0E7FF', tintForeground: '#3730A3' },
    dark: { main: '#818CF8', hover: '#A5B4FC', pressed: '#7280F5', contrastText: '#1b1a19', tint: '#232567', tintForeground: '#C7D2FE' },
  },
  sky: {
    label: tr('Sky'),
    light: { main: '#0369A1', hover: '#075985', pressed: '#0C4A6E', contrastText: '#ffffff', tint: '#E0F2FE', tintForeground: '#0C4A6E' },
    dark: { main: '#38BDF8', hover: '#7DD3FC', pressed: '#0EA5E9', contrastText: '#1b1a19', tint: '#082F49', tintForeground: '#7DD3FC' },
  },
  emerald: {
    label: tr('Emerald'),
    light: { main: '#047857', hover: '#065F46', pressed: '#064E3B', contrastText: '#ffffff', tint: '#D1FAE5', tintForeground: '#065F46' },
    dark: { main: '#34D399', hover: '#6EE7B7', pressed: '#10B981', contrastText: '#1b1a19', tint: '#06281E', tintForeground: '#A7F3D0' },
  },
  olive: {
    label: tr('Olive'),
    light: { main: '#4D7C0F', hover: '#3F6212', pressed: '#365314', contrastText: '#ffffff', tint: '#ECFCCB', tintForeground: '#365314' },
    dark: { main: '#A3E635', hover: '#BEF264', pressed: '#84CC16', contrastText: '#1b1a19', tint: '#1A2E05', tintForeground: '#D9F99D' },
  },
  amber: {
    label: tr('Amber'),
    light: { main: '#B45309', hover: '#92400E', pressed: '#78350F', contrastText: '#ffffff', tint: '#FEF3C7', tintForeground: '#78350F' },
    dark: { main: '#FBBF24', hover: '#FCD34D', pressed: '#F59E0B', contrastText: '#1b1a19', tint: '#3A2A08', tintForeground: '#FDE68A' },
  },
  rose: {
    label: tr('Rose'),
    light: { main: '#BE123C', hover: '#9F1239', pressed: '#881337', contrastText: '#ffffff', tint: '#FFE4E6', tintForeground: '#9F1239' },
    dark: { main: '#FB7185', hover: '#FDA4AF', pressed: '#F43F5E', contrastText: '#1b1a19', tint: '#4C0519', tintForeground: '#FECDD3' },
  },
  fuchsia: {
    label: tr('Fuchsia'),
    light: { main: '#A21CAF', hover: '#86198F', pressed: '#701A75', contrastText: '#ffffff', tint: '#FAE8FF', tintForeground: '#86198F' },
    dark: { main: '#E879F9', hover: '#F0ABFC', pressed: '#D946EF', contrastText: '#1b1a19', tint: '#4A044E', tintForeground: '#F5D0FE' },
  },
  graphite: {
    label: tr('Graphite'),
    light: { main: '#475569', hover: '#334155', pressed: '#1E293B', contrastText: '#ffffff', tint: '#E2E8F0', tintForeground: '#334155' },
    dark: { main: '#94A3B8', hover: '#CBD5E1', pressed: '#7A8BA3', contrastText: '#1b1a19', tint: '#27303C', tintForeground: '#CBD5E1' },
  },
  coffee: {
    label: tr('Coffee'),
    light: { main: '#8B4513', hover: '#74390F', pressed: '#5E2E0C', contrastText: '#ffffff', tint: '#F5E6D8', tintForeground: '#5E2E0C' },
    dark: { main: '#D39C74', hover: '#E4BC9E', pressed: '#C1824F', contrastText: '#1b1a19', tint: '#3B2716', tintForeground: '#E9C9AC' },
  },
  navy: {
    label: tr('Navy'),
    light: { main: '#1E3A8A', hover: '#182E6E', pressed: '#122353', contrastText: '#ffffff', tint: '#DBEAFE', tintForeground: '#1E3A8A' },
    dark: { main: '#8FA8F8', hover: '#B4C4FB', pressed: '#6E8AF0', contrastText: '#1b1a19', tint: '#14204A', tintForeground: '#BFD0FF' },
  },
};

/**
 * FR-39.1 — named theme presets.
 *
 * A preset is nothing more than a (mode, accent) pair the user could equally set
 * by hand. Keeping it to values that already exist is deliberate: there is one
 * source of truth for what renders, and "Custom" falls out of a simple
 * comparison instead of needing a parallel code path that could disagree with
 * the individual controls.
 *
 * There is deliberately no third "surface tone" knob. It was the obvious way to
 * make presets feel more distinct, and it is exactly the hidden dimension this
 * design rules out — a look you cannot reach through the normal controls is a
 * look the user cannot adjust or undo.
 */
export type ThemePresetId =
  | 'fluentSystem'
  | 'fluentLight'
  | 'fluentDark'
  | 'ocean'
  | 'forest'
  | 'slate'
  | 'midnight'
  | 'stitch';

/** The wire value stored per account; `CUSTOM` is computed, never selected. */
export type StoredThemePreset = 'FLUENT_SYSTEM' | 'FLUENT_LIGHT' | 'FLUENT_DARK' | 'OCEAN' | 'FOREST' | 'SLATE' | 'MIDNIGHT' | 'STITCH' | 'CUSTOM';

export type ThemePresetDefinition = {
  id: ThemePresetId;
  stored: StoredThemePreset;
  label: string;
  description: string;
  mode: 'light' | 'dark' | 'system';
  accent: AccentId;
};

export const themePresets: ThemePresetDefinition[] = [
  { id: 'fluentSystem', stored: 'FLUENT_SYSTEM', label: 'Fluent System', description: tr('Follows your device'), mode: 'system', accent: 'blue' },
  { id: 'fluentLight', stored: 'FLUENT_LIGHT', label: 'Fluent Light', description: tr('Always light, brand blue'), mode: 'light', accent: 'blue' },
  { id: 'fluentDark', stored: 'FLUENT_DARK', label: 'Fluent Dark', description: tr('Always dark, brand blue'), mode: 'dark', accent: 'blue' },
  { id: 'ocean', stored: 'OCEAN', label: tr('Ocean'), description: tr('Light and teal'), mode: 'light', accent: 'teal' },
  { id: 'forest', stored: 'FOREST', label: tr('Forest'), description: tr('Light and green'), mode: 'light', accent: 'green' },
  { id: 'slate', stored: 'SLATE', label: tr('Slate'), description: tr('Light and steel blue'), mode: 'light', accent: 'steel' },
  { id: 'midnight', stored: 'MIDNIGHT', label: tr('Midnight'), description: tr('Dark and purple'), mode: 'dark', accent: 'purple' },
  // From the Stitch project's DESIGN.md ("Clean, Modern, Data-Driven"): light
  // workspace with the guide's royal blue. Its other rules — Inter, the cool
  // sidebar wash, soft-shadow cards — are the existing Font/Tone/Style knobs
  // (Inter / Cool / Modern), which a preset deliberately does not touch.
  { id: 'stitch', stored: 'STITCH', label: 'Stitch', description: tr('Light and cobalt blue'), mode: 'light', accent: 'cobalt' },
];

/**
 * Which preset the current settings amount to, or `'CUSTOM'` when they match
 * none. Derived rather than trusted: the stored label can go stale the moment a
 * user nudges an individual control, and a preset name that lies about what is
 * on screen is worse than no name at all.
 */
export function matchPreset(mode: 'light' | 'dark' | 'system', accent: AccentId): StoredThemePreset {
  return themePresets.find((preset) => preset.mode === mode && preset.accent === accent)?.stored ?? 'CUSTOM';
}

/**
 * The text colour MUI puts on a filled `success` / `warning` / `info` / `error`
 * surface. grey-800, not white: those mains are fills and measure 1.90:1 to
 * 3.17:1 against white, so MUI's automatic pick would produce unreadable chips.
 */
const PALETTE_CONTRAST_TEXT = grey[800];

/** Buttons are pills in every interface style (docs/Optimize Appearance/button.md). */
export const BUTTON_RADIUS = '9999px';

export type Density = 'comfortable' | 'compact';

/**
 * FR-42 — the typeface choices.
 *
 * `system` is the default and is not a web font: it is the platform's own UI
 * face, which renders instantly and downloads nothing. It stays the default
 * deliberately — a font that costs a network request should be something a user
 * opts into, not something everyone pays for.
 *
 * Every stack ends in the same system fallback chain, so a font that fails to
 * load (offline, blocked, or a stale cache) degrades to the default face rather
 * than to an unstyled serif.
 *
 * All four are variable fonts, which is why one file per family covers every
 * weight the app uses.
 */
export type FontFamilyId = 'system' | 'publicSans' | 'inter' | 'dmSans' | 'nunitoSans' | 'montserrat' | 'lexend' | 'plusJakartaSans';

/**
 * The tail every stack shares — Fluent's face first, then each platform's own.
 *
 * BRD 25 (D-25-9): "Noto Sans Khmer Variable" sits before the generic faces so
 * Khmer text always has a proper face. global.css declares it for the Khmer
 * Unicode range only, so it never replaces a Latin face and its file downloads
 * only when Khmer text is on screen.
 */
export const SYSTEM_STACK = '"Segoe UI Variable", "Segoe UI", -apple-system, BlinkMacSystemFont, "Noto Sans Khmer Variable", Roboto, Helvetica, Arial, sans-serif';

export type FontFamilyDefinition = {
  id: FontFamilyId;
  /** Backend enum name. */
  stored: 'SYSTEM' | 'PUBLIC_SANS' | 'INTER' | 'DM_SANS' | 'NUNITO_SANS' | 'MONTSERRAT' | 'LEXEND' | 'PLUS_JAKARTA_SANS';
  label: string;
  description: string;
  stack: string;
};

export const fontFamilies: FontFamilyDefinition[] = [
  {
    id: 'system',
    stored: 'SYSTEM',
    label: tr('System default'),
    description: tr('Matches your device — loads instantly'),
    stack: SYSTEM_STACK,
  },
  {
    id: 'publicSans',
    stored: 'PUBLIC_SANS',
    label: 'Public Sans',
    description: tr('Neutral and highly legible'),
    stack: `"Public Sans Variable", ${SYSTEM_STACK}`,
  },
  {
    id: 'inter',
    stored: 'INTER',
    label: 'Inter',
    description: tr('Designed for screens'),
    stack: `"Inter Variable", ${SYSTEM_STACK}`,
  },
  {
    id: 'dmSans',
    stored: 'DM_SANS',
    label: 'DM Sans',
    description: tr('Geometric and low-contrast'),
    stack: `"DM Sans Variable", ${SYSTEM_STACK}`,
  },
  {
    id: 'nunitoSans',
    stored: 'NUNITO_SANS',
    label: 'Nunito Sans',
    description: tr('Rounded and friendly'),
    stack: `"Nunito Sans Variable", ${SYSTEM_STACK}`,
  },
  {
    id: 'montserrat',
    stored: 'MONTSERRAT',
    label: 'Montserrat',
    description: tr('Geometric, from the Stitch style guide'),
    stack: `"Montserrat Variable", ${SYSTEM_STACK}`,
  },
  {
    id: 'lexend',
    stored: 'LEXEND',
    label: 'Lexend',
    description: tr('Tuned for effortless reading'),
    stack: `"Lexend Variable", ${SYSTEM_STACK}`,
  },
  {
    id: 'plusJakartaSans',
    stored: 'PLUS_JAKARTA_SANS',
    label: 'Plus Jakarta Sans',
    description: tr('Contemporary and open'),
    stack: `"Plus Jakarta Sans Variable", ${SYSTEM_STACK}`,
  },
];

export function fontFamilyFromStored(stored: string): FontFamilyId {
  return fontFamilies.find((font) => font.stored === stored)?.id ?? 'system';
}

export function fontStack(id: FontFamilyId): string {
  return fontFamilies.find((font) => font.id === id)?.stack ?? SYSTEM_STACK;
}

/**
 * FR-40 — background tones.
 *
 * A tone is a coordinated *set* of surfaces (FR-40.2), not one colour: page
 * canvas, card, the subtle wash used for archived rows, and the sidebar all move
 * together, so the depth relationship between them survives whichever tone is
 * picked.
 *
 * Curated rather than a colour picker, deliberately (BR-35). The background is
 * what every piece of text in the product sits on, so an arbitrary value would
 * downgrade "no appearance choice can make text unreadable" into a runtime check
 * the user is able to fail. Every pair below was measured before being written
 * down: body text ≥ 7:1 and secondary text ≥ 4.5:1 against each surface, in both
 * modes.
 *
 * `tinted` carries no values (FR-40.3) — it is mixed from the active accent in
 * CSS, so the ten accents don't become twenty more colours to hand-validate and
 * a future accent works with no change here.
 */
export type BackgroundToneId =
  | 'neutral'
  | 'warm'
  | 'cool'
  | 'soft'
  | 'tinted'
  | 'flat'
  // Ten more washes, same construction: a coordinated surface set per tone,
  // every value measured against the body/muted text bars before landing.
  | 'rose'
  | 'mint'
  | 'lavender'
  | 'sand'
  | 'sage'
  | 'ice'
  | 'linen'
  | 'slate'
  | 'plum'
  | 'stone';

export type BackgroundToneDefinition = {
  id: BackgroundToneId;
  /** Backend enum name. */
  stored:
    | 'NEUTRAL'
    | 'WARM'
    | 'COOL'
    | 'SOFT'
    | 'TINTED'
    | 'FLAT'
    | 'ROSE'
    | 'MINT'
    | 'LAVENDER'
    | 'SAND'
    | 'SAGE'
    | 'ICE'
    | 'LINEN'
    | 'SLATE'
    | 'PLUM'
    | 'STONE';
  label: string;
  description: string;
  /**
   * Swatch triple for the picker: [canvas, card, sidebar] — the real values
   * the tone paints (mirror the `[data-tone]` blocks in global.css; a swatch
   * that understates its tone sells the wrong choice). `tinted` is resolved
   * from the live accent at render time; its entries are stand-ins.
   */
  preview: { light: [string, string, string]; dark: [string, string, string] };
};

export const backgroundTones: BackgroundToneDefinition[] = [
  {
    id: 'neutral',
    stored: 'NEUTRAL',
    label: tr('Neutral'),
    description: tr('Clean grey default'),
    preview: { light: ['#F9FAFB', '#ffffff', '#f5f5f5'], dark: ['#1b1a19', '#252423', '#252423'] },
  },
  {
    id: 'warm',
    stored: 'WARM',
    label: tr('Warm'),
    description: tr('Cream, easy on the eyes'),
    preview: { light: ['#faf7f2', '#fffdfa', '#f5f1e9'], dark: ['#1f1b17', '#2a2521', '#2a2521'] },
  },
  {
    id: 'cool',
    stored: 'COOL',
    label: tr('Cool'),
    description: tr('Calm blue-grey cast'),
    preview: { light: ['#EDF3F9', '#ffffff', '#E7EEF6'], dark: ['#171a1d', '#212529', '#212529'] },
  },
  {
    id: 'soft',
    stored: 'SOFT',
    label: tr('Soft'),
    description: tr('Deeper canvas, cards stand out'),
    preview: { light: ['#eef0f2', '#ffffff', '#e7eaee'], dark: ['#141414', '#242424', '#242424'] },
  },
  {
    id: 'tinted',
    stored: 'TINTED',
    label: tr('Tinted'),
    description: tr('Washed with your accent'),
    // Resolved from the accent at render time; these stand in for the swatch.
    preview: { light: ['#f7fafd', '#ffffff', '#eef4f9'], dark: ['#16191c', '#202427', '#202427'] },
  },
  {
    id: 'flat',
    stored: 'FLAT',
    label: tr('Flat'),
    description: tr('One surface, borders only'),
    preview: { light: ['#ffffff', '#ffffff', '#ffffff'], dark: ['#1f1e1d', '#1f1e1d', '#1f1e1d'] },
  },
  {
    id: 'rose',
    stored: 'ROSE',
    label: tr('Rose'),
    description: tr('Gentle pink blush'),
    preview: { light: ['#FAF3F5', '#FFFBFC', '#F4E9ED'], dark: ['#201819', '#2B2224', '#2B2224'] },
  },
  {
    id: 'mint',
    stored: 'MINT',
    label: tr('Mint'),
    description: tr('Fresh green wash'),
    preview: { light: ['#EFF6F1', '#FBFEFC', '#E7EFEA'], dark: ['#161B18', '#202623', '#202623'] },
  },
  {
    id: 'lavender',
    stored: 'LAVENDER',
    label: tr('Lavender'),
    description: tr('Quiet violet cast'),
    preview: { light: ['#F4F2FA', '#FDFCFF', '#EDEAF5'], dark: ['#1A181F', '#24222B', '#24222B'] },
  },
  {
    id: 'sand',
    stored: 'SAND',
    label: tr('Sand'),
    description: tr('Deeper desert beige'),
    preview: { light: ['#F5F0E6', '#FDFAF2', '#EEE7D8'], dark: ['#1E1A14', '#29241C', '#29241C'] },
  },
  {
    id: 'sage',
    stored: 'SAGE',
    label: tr('Sage'),
    description: tr('Muted grey-green'),
    preview: { light: ['#F0F2EE', '#FAFBF9', '#E8ECE5'], dark: ['#191C18', '#232722', '#232722'] },
  },
  {
    id: 'ice',
    stored: 'ICE',
    label: tr('Ice'),
    description: tr('Bright glacial blue'),
    preview: { light: ['#EAF4FB', '#ffffff', '#DFECF7'], dark: ['#151A20', '#1F262E', '#1F262E'] },
  },
  {
    id: 'linen',
    stored: 'LINEN',
    label: tr('Linen'),
    description: tr('Soft paper white'),
    preview: { light: ['#FBF9F4', '#ffffff', '#F4F0E7'], dark: ['#1D1B17', '#272420', '#272420'] },
  },
  {
    id: 'slate',
    stored: 'SLATE',
    label: tr('Slate'),
    description: tr('Deep blue-grey canvas'),
    preview: { light: ['#E9ECF0', '#F8FAFC', '#DFE4EA'], dark: ['#15181C', '#1E2227', '#1E2227'] },
  },
  {
    id: 'plum',
    stored: 'PLUM',
    label: tr('Plum'),
    description: tr('Dusky purple-pink'),
    preview: { light: ['#F6F0F6', '#FDFAFD', '#EFE6EF'], dark: ['#1E1820', '#28222A', '#28222A'] },
  },
  {
    id: 'stone',
    stored: 'STONE',
    label: tr('Stone'),
    description: tr('Warm mineral grey'),
    preview: { light: ['#F2F0ED', '#FAF9F7', '#EAE7E2'], dark: ['#1B1A18', '#252320', '#252320'] },
  },
];

export function toneFromStored(stored: string): BackgroundToneId {
  return backgroundTones.find((tone) => tone.stored === stored)?.id ?? 'neutral';
}

/**
 * FR-48, reworked by BRD 26 — the interface style.
 *
 * The dimension the other appearance settings leave alone: shape, elevation,
 * translucency, layout density and motion. It decides no hue, so every style
 * composes with all accents, tones and high contrast.
 *
 * The values live in `design/tokens.ts` (`styleTokens`) and global.css carries
 * them as `--style-*` variables, switched by `data-style` on `<html>`. The MUI
 * overrides below read those variables rather than copies of the numbers, so
 * MUI and hand-written CSS cannot disagree, and the glass fallbacks in the
 * stylesheet (D-26-6) reach MUI surfaces with no extra code.
 *
 *   modern   the calm flagship and the only default (D-26-7)
 *   classic  dense Fluent 2 for power users
 *   glass    Liquid Glass: glass only on floating layers (D-26-3)
 */
export type InterfaceStyleId = InterfaceStyle;

export type InterfaceStyleDefinition = {
  id: InterfaceStyleId;
  /** Backend enum name (unchanged by the Q-26-4 rename). */
  stored: 'CLASSIC' | 'MODERN' | 'LIQUID_GLASS';
  label: string;
  /** The one-line "who it suits" (BRD 26 §7). */
  description: string;
};

/** In picker order: the default first. */
export const interfaceStyles: InterfaceStyleDefinition[] = [
  {
    id: 'modern',
    stored: 'MODERN',
    label: tr('Modern'),
    description: tr('Calm and spacious. The recommended look.'),
  },
  {
    id: 'classic',
    stored: 'CLASSIC',
    label: tr('Classic'),
    description: tr('Compact and crisp, for scanning lots of rows.'),
  },
  {
    id: 'glass',
    stored: 'LIQUID_GLASS',
    label: tr('Liquid Glass'),
    description: tr('Frosted panels that float over your background.'),
  },
];

export function interfaceStyleFromStored(stored: string): InterfaceStyleId {
  return interfaceStyles.find((style) => style.stored === stored)?.id ?? 'modern';
}

/** Tighter tracking on the two display sizes; Classic keeps Fluent's normal tracking. */
function headingLetterSpacing(style: InterfaceStyleId): string {
  return style === 'classic' ? 'normal' : '-0.018em';
}

/**
 * The surface values a tone contributes, for the MUI palette.
 *
 * `background` is the **page canvas** and `card` the surface that sits on it.
 * That mapping is the whole point and is easy to get wrong: `CssBaseline` paints
 * `body` from `palette.background.default`, and `body` covers the `<html>`
 * background that `--page` sets. So the canvas the user actually sees comes from
 * here, not from the stylesheet — `var(--page)` has exactly one consumer
 * (`:root`) and it is painted over.
 *
 * The first version of this function returned `#ffffff` for the light variants
 * of cool/soft/tinted/flat, putting their real canvas colour only in `--page`.
 * The result was four tones that were byte-identical to Neutral in light mode
 * and did nothing at all. If you change these values, check them against what
 * `body` ends up with, not against the CSS.
 *
 * These have to exist in the theme as well as in `global.css`: MUI paints
 * `Paper`, `Card`, and `Dialog` from `palette.background`, so if only the
 * stylesheet knew about tones, every MUI surface would stay neutral while the
 * CSS-styled ones shifted — the same disagreement the accent handling avoids.
 *
 * `tinted` returns a `color-mix()` referencing the inline `--primary`, which is
 * a valid CSS colour and resolves in the browser exactly as the stylesheet's
 * version does (FR-40.3). `neutral` returns null so callers keep the base
 * values untouched, which is what makes it a true no-op (AC-3).
 */
export function toneSurfaces(
  mode: 'light' | 'dark',
  tone: BackgroundToneId,
): { background: string; card: string; border?: string } | null {
  const dark = mode === 'dark';
  switch (tone) {
    case 'warm':
      return dark ? { background: '#1f1b17', card: '#2a2521' } : { background: '#faf7f2', card: '#fffdfa' };
    case 'cool':
      return dark ? { background: '#171a1d', card: '#212529' } : { background: '#EDF3F9', card: '#ffffff' };
    case 'soft':
      return dark ? { background: '#141414', card: '#242424' } : { background: '#eef0f2', card: '#ffffff' };
    case 'tinted':
      return dark
        ? {
            background: 'color-mix(in srgb, var(--primary) 6%, #1b1a19)',
            card: 'color-mix(in srgb, var(--primary) 8%, #252423)',
          }
        : { background: 'color-mix(in srgb, var(--primary) 4%, #F9FAFB)', card: '#ffffff' };
    case 'flat':
      // AC-8: with no lightness step left, the border carries the separation.
      return dark
        ? { background: '#1f1e1d', card: '#1f1e1d', border: '#4d4b49' }
        : { background: '#ffffff', card: '#ffffff', border: '#d4d4d4' };
    // The ten washes. Same shape as warm/cool above; every value cleared the
    // body ≥7:1 and muted ≥4.5:1 bars in both modes before being written down
    // (theme.test.ts measures them all).
    case 'rose':
      return dark ? { background: '#201819', card: '#2B2224' } : { background: '#FAF3F5', card: '#FFFBFC' };
    case 'mint':
      return dark ? { background: '#161B18', card: '#202623' } : { background: '#EFF6F1', card: '#FBFEFC' };
    case 'lavender':
      return dark ? { background: '#1A181F', card: '#24222B' } : { background: '#F4F2FA', card: '#FDFCFF' };
    case 'sand':
      return dark ? { background: '#1E1A14', card: '#29241C' } : { background: '#F5F0E6', card: '#FDFAF2' };
    case 'sage':
      return dark ? { background: '#191C18', card: '#232722' } : { background: '#F0F2EE', card: '#FAFBF9' };
    case 'ice':
      return dark ? { background: '#151A20', card: '#1F262E' } : { background: '#EAF4FB', card: '#ffffff' };
    case 'linen':
      return dark ? { background: '#1D1B17', card: '#272420' } : { background: '#FBF9F4', card: '#ffffff' };
    case 'slate':
      return dark ? { background: '#15181C', card: '#1E2227' } : { background: '#E9ECF0', card: '#F8FAFC' };
    case 'plum':
      return dark ? { background: '#1E1820', card: '#28222A' } : { background: '#F6F0F6', card: '#FDFAFD' };
    case 'stone':
      return dark ? { background: '#1B1A18', card: '#252320' } : { background: '#F2F0ED', card: '#FAF9F7' };
    default:
      return null;
  }
}

export type SurfaceTokens = {
  background: string;
  card: string;
  foreground: string;
  mutedForeground: string;
  secondary: string;
  border: string;
  error: string;
};

/**
 * The neutral surface/text tokens for one (mode, highContrast) combination —
 * the four cases resolved in one table instead of a ternary per property.
 *
 * FR-39.3: high contrast is a *stronger* version of the same design, not a
 * different one. Text goes to pure black/white (21:1 against its surface,
 * comfortably past the 7:1 target), secondary text stops being a soft grey and
 * clears 7:1 too, and borders become visible structure rather than a hint —
 * `#e1e1e1` on white is 1.3:1, which is decoration, not a boundary a
 * low-vision user can actually see.
 *
 * The mode still decides light-vs-dark; high contrast only decides how far
 * apart the values sit. That is what lets the toggle compose with either mode
 * instead of replacing them.
 */
export function surfaceTokens(mode: 'light' | 'dark', highContrast = false): SurfaceTokens {
  if (mode === 'dark') {
    return highContrast
      ? {
          background: '#000000',
          card: '#0f0f0f',
          foreground: '#ffffff',
          mutedForeground: '#e8e8e8',
          secondary: '#2a2a2a',
          border: '#b0aeac',
          error: '#ff9ba0',
        }
      : {
          // BRD 25 §5.1 dark neutrals (design/tokens.ts).
          background: neutrals.dark.canvas,
          card: neutrals.dark.surface,
          foreground: neutrals.dark.textPrimary,
          mutedForeground: neutrals.dark.textSecondary,
          secondary: '#252A31',
          border: neutrals.dark.border,
          error: '#e37d80',
        };
  }

  return highContrast
    ? {
        background: '#ffffff',
        card: '#ffffff',
        foreground: '#000000',
        mutedForeground: '#2b2b2b',
        secondary: '#ededed',
        border: '#4a4a4a',
        error: palette.error.darker,
      }
    : {
        // FR-43: the neutral steps now come from the grey ramp rather than
        // being individually chosen Fluent values — 800 for body text, 600 for
        // secondary, 300 for borders, 200 for recessed panels. Same roles, one
        // ramp, so the greys relate to each other instead of coincidentally
        // sitting near each other.
        // FR-44: the page canvas is grey-100, not white. It always *claimed* to
        // be #fafafa via the --page variable, but nothing painted --page:
        // CssBaseline sets body from palette.background.default, and that was
        // white, so cards never stood out from the page in light mode. The
        // canvas belongs here, where it is actually rendered.
        // BRD 25 §5.1: the V25 neutrals (design/tokens.ts) replace the grey
        // ramp steps here. Same roles; a cooler canvas and a slightly lighter
        // secondary text that still clears 4.5:1 on every tone.
        background: neutrals.light.canvas,
        card: neutrals.light.surface,
        foreground: neutrals.light.textPrimary,
        // FR-44: grey-700, not grey-600. Secondary text sits on the page canvas
        // as well as on cards (a page subtitle is not inside a card), and
        // grey-600 measures only 4.88:1 on white — it drops under 4.5:1 the
        // moment a background tone tints the canvas, failing on Cool (4.37) and
        // Soft (4.27). grey-700 holds 7.29:1 in the worst case while keeping a
        // clear step below the grey-800 body text.
        mutedForeground: neutrals.light.textSecondary,
        secondary: neutrals.light.surfaceSunken,
        border: neutrals.light.border,
        error: palette.error.dark,
      };
}

/**
 * FR-39.3 / BR-34 — the status and priority hues under high contrast.
 *
 * BR-34 is the constraint that shapes these tables: contrast may move a
 * colour's *lightness*, never its hue or meaning. Completed stays green,
 * Blocked stays orange, Critical stays red — a user who has learned the palette
 * does not have to relearn it because they switched on an accessibility
 * setting. Every value below was checked to stay within 6° of the hue it
 * replaces. This is the one documented exception to BR-14: an accent choice
 * still cannot touch these palettes; only this mode may, and only along
 * lightness.
 *
 * Unlike the base palettes, these ARE mode-specific, and they have to be. The
 * base hues are mid-tones picked to survive on either surface, which works
 * because neither normal surface is extreme. High contrast pushes the surfaces
 * to pure white and pure black, and no single mid-tone clears 4.5:1 against
 * both — the base neutral measures 2.64:1 on white. So light gets darkened
 * hues and dark gets lightened ones.
 *
 * The near-collisions BR-14 warns about are preserved too: BLOCKED stays
 * distinguishable from HIGH, and DECLINED from CRITICAL (measured ΔE 9.4–24.8,
 * against the ΔE 8 floor this file uses elsewhere), so a priority chip still
 * never reads as a status chip.
 */
const HC_LIGHT = {
  neutralIdle: '#5d5a58',
  neutralHeld: '#3b3a39',
  blue: '#00508f',
  indigo: '#2A1A80', // V25 WAITING family, one step darker than #3B2596
  orange: '#a32c00',
  berry: '#8A0079', // V25 OPEN family, one step darker than #B4009E
  red: '#a4262c',
  green: '#0a5c0a',
} as const;

const HC_DARK = {
  neutralIdle: '#b8b5b2',
  neutralHeld: '#d6d3d1',
  blue: '#7cc0f0',
  indigo: '#B3A8FF',
  orange: '#ff8a4d',
  berry: '#F59BE3',
  red: '#ff9ba0',
  green: '#6fd070',
} as const;

function highContrastStatusMap(mode: 'light' | 'dark'): Record<StatusToken, string> {
  const c = mode === 'dark' ? HC_DARK : HC_LIGHT;
  const byFamily: Record<StatusFamily, string> = {
    moving: c.blue,
    waiting: c.indigo,
    blocked: c.orange,
    open: c.berry,
    declined: c.red,
    done: c.green,
    idle: c.neutralIdle,
    held: c.neutralHeld,
  };
  return Object.fromEntries(
    Object.entries(STATUS_FAMILY).map(([token, family]) => [token, byFamily[family]]),
  ) as Record<StatusToken, string>;
}

function highContrastPriorityMap(mode: 'light' | 'dark'): Record<PriorityToken, string> {
  // HIGH and CRITICAL are deliberately a different step from the BLOCKED and
  // DECLINED hues above — same reason the base palettes use #ca5010 vs #d83b01.
  return mode === 'dark'
    ? { LOW: HC_DARK.neutralIdle, MEDIUM: '#e0b544', HIGH: '#ffb083', CRITICAL: '#ff6b73' }
    : { LOW: HC_LIGHT.neutralIdle, MEDIUM: '#7a5600', HIGH: '#a3400c', CRITICAL: '#8a1f24' };
}

/**
 * The "no state" colour for a token neither palette knows — a backend enum added
 * before this build learned about it. Contrast-aware for the same reason
 * everything else here is: the base neutral is 2.64:1 on white, which under high
 * contrast would make the one unlabelled case the least legible thing on screen.
 */
function fallbackHue(mode: 'light' | 'dark', highContrast: boolean): string {
  if (!highContrast) {
    return mode === 'dark' ? statusTones.dark.idle.fill : neutralFallback;
  }
  return mode === 'dark' ? HC_DARK.neutralIdle : HC_LIGHT.neutralIdle;
}

/** The status hue in effect, honouring high contrast (BR-34). */
export function statusColor(token: StatusToken, mode: 'light' | 'dark' = 'light', highContrast = false): string {
  const base = mode === 'dark' ? statusColorsDark : statusColors;
  const palette = highContrast ? highContrastStatusMap(mode) : base;
  return palette[token] ?? fallbackHue(mode, highContrast);
}

/** The priority hue in effect, honouring high contrast (BR-34). */
export function priorityColor(token: PriorityToken, mode: 'light' | 'dark' = 'light', highContrast = false): string {
  const palette = highContrast ? highContrastPriorityMap(mode) : priorityColors;
  return palette[token] ?? fallbackHue(mode, highContrast);
}

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
 * BRD 25: the status palette is mode-specific (lighter fills in dark mode, so
 * every fill keeps 3:1 on its surface); the meaning family never changes with
 * the mode. The priority palette is still shared by both modes.
 */
export type AppThemeOptions = {
  mode: 'light' | 'dark';
  style?: InterfaceStyleId;
  accent?: AccentId;
  font?: FontFamilyId;
  density?: Density;
  highContrast?: boolean;
  tone?: BackgroundToneId;
};

/**
 * IS-FR-06 — builds the MUI theme. Named options replace the old positional
 * `buildTheme(mode, accent, density, highContrast, tone, font, style)`.
 */
export function createAppTheme({
  mode,
  style = 'modern',
  accent = 'blue',
  font = 'system',
  density = 'comfortable',
  highContrast = false,
  tone = 'neutral',
}: AppThemeOptions) {
  const dark = mode === 'dark';
  // BRD 26: the style's numbers, for the one place MUI needs a number
  // (theme.shape.borderRadius). Everything else reads the --style-* variables.
  const shape = styleTokens[style][mode];
  // FR-40.5: high contrast outranks the tone. Applying the tone only when it is
  // off is what keeps that true here as well as in the stylesheet — and because
  // the tone is never written to the stored settings by this, turning high
  // contrast back off restores the user's choice untouched.
  const toned = highContrast ? null : toneSurfaces(mode, tone);
  const tokens = surfaceTokens(mode, highContrast);
  const baseBrand = accentOptions[accent][mode];
  // FR-39.3: high contrast takes the accent one step further along its own ramp
  // rather than substituting a different colour — darker in light mode, lighter
  // in dark mode. The accent the user picked is still recognisably theirs.
  const brand = highContrast
    ? { ...baseBrand, main: dark ? baseBrand.hover : baseBrand.pressed }
    : baseBrand;
  const border = toned?.border ?? tokens.border;

  return createTheme({
    palette: {
      mode,
      background: {
        default: toned?.background ?? tokens.background, // --background
        paper: toned?.card ?? tokens.card, // --card / --popover
      },
      text: {
        primary: tokens.foreground, // --foreground
        secondary: tokens.mutedForeground, // --muted-foreground
        // FR-45: grey-500. Disabled text is explicitly exempt from the 4.5:1
        // rule (WCAG 1.4.3), and looking disabled is the point — it measures
        // 2.73:1 on white, which reads as unavailable rather than as low-quality
        // body text.
        disabled: dark ? grey[600] : grey[500],
      },
      primary: {
        main: brand.main, // --primary (the chosen accent's ramp, FR-18.3)
        dark: brand.pressed,
        light: brand.hover,
        contrastText: brand.contrastText, // --primary-foreground
      },
      secondary: {
        main: tokens.secondary, // --secondary / --muted (Fluent neutralBackground3)
        contrastText: tokens.foreground, // --secondary-foreground
      },
      error: {
        main: tokens.error, // --destructive
        light: palette.error.light,
        dark: palette.error.darker,
        // Unlike success/warning/info, `error.main` here is the ramp's *dark*
        // shade in light mode — a proper button colour that carries white text
        // at 6.56:1. Dark mode uses a lightened shade, which needs the dark text
        // instead. Same split as the accents.
        contrastText: dark ? PALETTE_CONTRAST_TEXT : '#ffffff',
      },
      // FR-45: MUI's own semantic slots, which the theme previously left unset —
      // so `<Alert severity="success">` and `<Chip color="warning">` fell back to
      // MUI's stock colours rather than this app's palette.
      //
      // contrastText is set explicitly rather than left to MUI's automatic
      // threshold. These mains are fills, not text backgrounds: measured against
      // white they run 1.90:1 (warning) to 3.17:1 (error), so a filled variant
      // with white text would be unreadable. grey-800 clears 4.90:1 on the worst
      // of them.
      success: {
        main: palette.success.main,
        light: palette.success.light,
        dark: palette.success.dark,
        contrastText: PALETTE_CONTRAST_TEXT,
      },
      warning: {
        main: palette.warning.main,
        light: palette.warning.light,
        dark: palette.warning.dark,
        contrastText: PALETTE_CONTRAST_TEXT,
      },
      info: {
        main: palette.info.main,
        light: palette.info.light,
        dark: palette.info.dark,
        contrastText: PALETTE_CONTRAST_TEXT,
      },
      // The ramp in MUI's standard slot, so components that reach for
      // `palette.grey[n]` get this app's greys instead of MUI's defaults.
      grey,
      // FR-45: grey-400/500 are the ramp's "unavailable" steps. MUI paints
      // disabled controls and their fills from here.
      action: {
        disabled: dark ? grey[600] : grey[500],
        disabledBackground: dark ? '#2d2c2b' : grey[400],
      },
      divider: border, // --border / --input
    },
    shape: {
      // BRD 26: the control radius (--style-radius-control). Components never
      // multiply it: D-26-2 bans numeric `borderRadius: n` in sx.
      borderRadius: shape.radiusControl,
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
      // BRD 25 §8: fast 150 / base 200 / slow 250 (design/tokens.ts `motion`).
      // BRD 26 §3.5: each style has its own fast / base / slow and easing.
      duration: {
        shortest: shape.motionFast,
        shorter: shape.motionFast,
        short: shape.motionBase,
        standard: shape.motionBase,
        complex: shape.motionSlow,
        enteringScreen: shape.motionBase,
        leavingScreen: shape.motionFast,
      },
      easing: {
        easeInOut: shape.ease,
        easeOut: shape.ease,
        easeIn: 'cubic-bezier(0.4, 0, 1, 1)',
        sharp: 'cubic-bezier(0.4, 0, 0.6, 1)',
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
      // FR-42: must match the --font-family variable ThemeModeProvider sets on
      // :root. global.css's unlayered rule is what actually renders text, so if
      // these two disagree, MUI components and plain elements use different
      // faces.
      fontFamily: fontStack(font),
      // FR-48: only the two display sizes take the tighter tracking. Tightening
      // body text costs legibility for nothing — the effect reads as deliberate
      // at 1.25rem and up, and as a rendering fault below it.
      h1: { fontSize: '1.5rem', fontWeight: 600, lineHeight: 1.3, letterSpacing: headingLetterSpacing(style) },
      h2: { fontSize: '1.25rem', fontWeight: 600, lineHeight: 1.35, letterSpacing: headingLetterSpacing(style) },
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
      // Button design system (docs/Optimize Appearance/button.md): a pill
      // shape and four states (normal, hover, pressed, disabled) for each of
      // the filled, outline and text variants. A left icon is the filled
      // variant with `startIcon`. Colours come from the user's accent, not a
      // fixed blue, so the accent picker and the contrast floors still apply.
      MuiButton: {
        defaultProps: { disableElevation: true },
        styleOverrides: {
          root: ({ ownerState }) => {
            const tone =
              ownerState.color === 'error'
                ? { main: tokens.error, pressed: palette.error.darker, contrast: dark ? PALETTE_CONTRAST_TEXT : '#ffffff' }
                : { main: brand.main, pressed: brand.pressed, contrast: brand.contrastText };
            // Outline and text buttons in a neutral colour use body text, not
            // the pale `secondary` fill, which is unreadable as text.
            const neutral = ownerState.color !== 'primary' && ownerState.color !== 'error';
            const ring = `0 0 0 4px ${alpha(tone.main, dark ? 0.35 : 0.22)}`;
            const disabledFill = dark ? grey[800] : grey[200];
            const disabledText = dark ? grey[600] : grey[400];
            const base = {
              textTransform: 'none' as const,
              fontWeight: 500,
              borderRadius: BUTTON_RADIUS,
              paddingInline: 16,
              transition: 'background-color 150ms ease, box-shadow 150ms ease, color 150ms ease, border-color 150ms ease',
              '&:focus-visible': { outline: `2px solid ${tone.main}`, outlineOffset: 2 },
            };
            if (ownerState.variant === 'contained') {
              return {
                ...base,
                backgroundColor: tone.main,
                color: tone.contrast,
                '&:hover': { backgroundColor: tone.main, boxShadow: ring },
                '&:active': { backgroundColor: tone.pressed, boxShadow: 'none' },
                '&.Mui-disabled': { backgroundColor: disabledFill, color: disabledText, boxShadow: 'none' },
              };
            }
            if (ownerState.variant === 'outlined') {
              const text = neutral ? tokens.foreground : tone.main;
              return {
                ...base,
                backgroundColor: toned?.card ?? tokens.card,
                color: text,
                border: `1px solid ${border}`,
                '&:hover': {
                  backgroundColor: neutral ? (dark ? grey[800] : grey[200]) : alpha(tone.main, 0.08),
                  borderColor: border,
                },
                '&:active': {
                  backgroundColor: neutral ? grey[600] : tone.pressed,
                  borderColor: neutral ? grey[600] : tone.pressed,
                  color: neutral ? '#ffffff' : tone.contrast,
                },
                '&.Mui-disabled': { backgroundColor: disabledFill, color: disabledText, border: '1px solid transparent' },
              };
            }
            // Text: no fill until hover, then a soft tint of the button colour.
            const textColour = neutral ? tokens.foreground : tone.main;
            return {
              ...base,
              paddingInline: 12,
              color: textColour,
              '&:hover': { backgroundColor: alpha(textColour, 0.1) },
              '&:active': { backgroundColor: alpha(textColour, 0.2) },
              '&.Mui-disabled': { color: alpha(disabledText, 0.9) },
            };
          },
          // The left icon reads as a small badge beside the label.
          startIcon: { marginRight: 6, '& > *:nth-of-type(1)': { fontSize: '1.05em' } },
          sizeSmall: { minHeight: 32, paddingBlock: 4 },
          sizeMedium: { minHeight: 38, paddingBlock: 6 },
          sizeLarge: { minHeight: 46, paddingBlock: 8, fontSize: '0.95rem' },
        },
      },
      MuiOutlinedInput: {
        styleOverrides: {
          root: {
            borderRadius: 'var(--style-radius-control)',
            // Fluent's outer focus ring. Classic keeps the 1px offset; Modern
            // and Glass use 2px (BRD 26 §4). Never blurred.
            '&.Mui-focused': {
              outline: `2px solid ${brand.main}`,
              outlineOffset: style === 'classic' ? '1px' : '2px',
            },
          },
        },
      },
      // Skeletons appear wherever Loading.tsx mirrors a page's real layout.
      // `wave` reads as activity rather than a static grey block; the blanket
      // reduce-motion rules in global.css suppress the sweep when asked.
      MuiSkeleton: {
        defaultProps: { animation: 'wave' },
        styleOverrides: {
          root: { borderRadius: 'var(--radius-sm)' },
        },
      },
      // Tooltips are a floating layer (BRD 26 §4): raised solid in Modern and
      // Classic, glass in Liquid Glass. Text is the normal foreground on the
      // surface, so it keeps 4.5:1 in every style (D-26-5 floors).
      MuiTooltip: {
        styleOverrides: {
          tooltip: {
            borderRadius: 'var(--style-radius-menu)',
            fontSize: '0.75rem',
            color: 'var(--text-primary)',
            backgroundColor: 'var(--style-floating-bg)',
            backdropFilter: 'var(--style-blur)',
            WebkitBackdropFilter: 'var(--style-blur)',
            border: 'var(--style-floating-border)',
            boxShadow: 'var(--style-highlight), var(--style-shadow-overlay)',
          },
          // The arrow is a solid triangle: it cannot blur, so it takes the
          // raised surface and the floating border colour.
          arrow: {
            color: 'var(--surface-raised)',
          },
        },
      },
      // Flyout rows read as soft inset rows rather than full-bleed strips.
      MuiMenuItem: {
        styleOverrides: {
          root: { borderRadius: 'var(--radius-sm)', marginInline: 4 },
        },
      },
      MuiListItemButton: {
        styleOverrides: {
          root: { borderRadius: 'var(--radius-sm)' },
        },
      },
      // MUI's Chip has its own pill radius that ignores theme.shape, so the
      // style's chip radius is stated explicitly. Chips are always solid.
      MuiChip: {
        styleOverrides: {
          root: { fontWeight: 700, borderRadius: 'var(--style-radius-chip)' },
          label: { fontWeight: 700 },
        },
      },
      // Paper is content by default: solid, never glass. Overlays below opt in.
      MuiPaper: {
        styleOverrides: {
          root: { backgroundImage: 'none' },
          rounded: { borderRadius: 'var(--style-radius-card)' },
        },
      },
      // Cards are content, so they stay solid in every style (D-26-3): the
      // surface, hairline border, rest shadow and radius come from the tokens.
      // `backgroundImage: none` keeps MUI's dark elevation overlay out.
      MuiCard: {
        defaultProps: { elevation: 0 },
        styleOverrides: {
          root: {
            backgroundColor: 'var(--style-card-bg)',
            border: 'var(--style-card-border)',
            borderRadius: 'var(--style-radius-card)',
            boxShadow: 'var(--style-shadow-rest)',
            backgroundImage: 'none',
          },
        },
      },
      // MUI's default unselected toggle text (54% black) measures 4.49:1 on the
      // canvas, just under WCAG 1.4.3. The secondary text token clears 4.5:1 on
      // every surface (found by the BRD 26 axe matrix).
      MuiToggleButton: {
        styleOverrides: {
          root: { color: 'var(--text-secondary)' },
        },
      },
      // BRD 26 §3.4: card padding follows the style's layout density — 20px in
      // Modern and Liquid Glass (16px on phones), 12px in Classic.
      MuiCardContent: {
        styleOverrides: {
          root: {
            padding: 'var(--style-card-pad)',
            '&:last-child': { paddingBottom: 'var(--style-card-pad)' },
            '@media (max-width: 599.95px)': {
              padding: 'var(--style-card-pad-phone)',
              '&:last-child': { paddingBottom: 'var(--style-card-pad-phone)' },
            },
          },
        },
      },
      // Dialogs are a floating layer: glass in Liquid Glass over a 30% scrim,
      // raised solid over a 40% scrim otherwise (BRD 26 §4). The fallbacks in
      // global.css turn the glass variables solid when D-26-6 applies.
      MuiDialog: {
        styleOverrides: {
          root: {
            '& .MuiBackdrop-root:not(.MuiBackdrop-invisible)': {
              backgroundColor: style === 'glass' ? 'rgba(0, 0, 0, 0.30)' : 'rgba(0, 0, 0, 0.40)',
            },
          },
          paper: {
            borderRadius: 'var(--style-radius-dialog)',
            backgroundColor: 'var(--style-floating-bg)',
            backdropFilter: 'var(--style-blur)',
            WebkitBackdropFilter: 'var(--style-blur)',
            border: 'var(--style-floating-border)',
            boxShadow: 'var(--style-highlight), var(--style-shadow-overlay)',
            backgroundImage: 'none',
            // Inputs inside a glass dialog sit on a solid inset surface (D-26-4).
            ...(style === 'glass'
              ? { '& .MuiOutlinedInput-root, & .MuiFilledInput-root': { backgroundColor: 'var(--style-card-bg)' } }
              : {}),
          },
        },
      },
      // Menus, selects and popovers: a floating layer.
      MuiPopover: {
        styleOverrides: {
          paper: {
            borderRadius: 'var(--style-radius-menu)',
            backgroundColor: 'var(--style-floating-bg)',
            backdropFilter: 'var(--style-blur)',
            WebkitBackdropFilter: 'var(--style-blur)',
            border: 'var(--style-floating-border)',
            boxShadow: 'var(--style-highlight), var(--style-shadow-overlay)',
            backgroundImage: 'none',
          },
        },
      },
    },
  });
}

const theme = createAppTheme({ mode: 'light' });

export default theme;
