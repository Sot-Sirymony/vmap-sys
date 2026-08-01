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

  // IssueReportStatus (FR-38) — OPEN/IN_PROGRESS/RESOLVED/CLOSED reuse the
  // tokens above; these three are the states unique to the triage lifecycle.
  IN_REVIEW: PURPLE_WAITING,
  PLANNED: BLUE_MOVING,
  WONT_FIX: NEUTRAL_HELD,
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

/**
 * FR-41 — the categorical pie palette.
 *
 * Four distinct hues for charts whose categories carry **no** inherent meaning
 * (Vision Areas, obstacle types). It is deliberately NOT used for status or
 * priority charts: those encode meaning in colour, and BR-14 forbids a slice
 * that could read as "Completed" when it means "Health".
 *
 * Mode-specific, because the labels sit *on* the slices. Light mode uses white
 * labels, so the slices must be dark enough to carry them; dark mode uses near
 * black labels, so the slices must be light. A single palette cannot do both —
 * the same reason the accents ship two ramps.
 *
 * Every value was searched rather than picked, against four simultaneous
 * constraints: the label clears 4.5:1 on its slice, the slice clears 3:1 on its
 * own card, no two slices collapse into each other under simulated colour
 * vision deficiency, and none is within ΔE 12 of a status or priority hue.
 *
 * Two of those constraints are worth knowing about, because they explain why
 * this looks deeper than a typical marketing palette:
 *   - White-on-slice is expensive. A bright gold cannot carry white text at any
 *     size (#F2D56F measures 1.45:1), so the gold here is a dark ochre.
 *   - Gold and coral collapse into each other for a deuteranope. Lighter corals
 *     measured ΔE 4.8 against this gold, so the coral is a deep brick — the same
 *     problem the status palette solved by moving WAITING off the warm ramp.
 *
 * Slot 0 used to be a green, and that was the bug this palette shipped with: the
 * separation floor was measured as CIELAB ΔE against a crude deuteranopia
 * matrix, which is far more forgiving than what people actually see. Re-measured
 * in OKLab against Machado–Oliveira–Fernandes 2009, green↔gold came out at ΔE
 * 5.4 for a protanope and only 12.6 for full colour vision — two slices of the
 * same pie that nobody could reliably tell apart. In dark mode the mint was
 * worse still, colliding with both the gold and the sky. Green is the hue that
 * cannot coexist with a gold under CVD, so slot 0 is now a magenta, and every
 * pair in every one of the four variants clears protan/deutan ΔE 8 and the
 * normal-vision floor of 15. `theme.test.ts` enforces exactly that.
 *
 * Slot order is fixed and identical across the four variants, so a category
 * keeps its identity when the user switches mode or turns on high contrast.
 */
export const categoricalPieColors = {
  light: ['#ae3078', '#8a6d00', '#2e6b9e', '#6e1b16'],
  dark: ['#d254ae', '#eed492', '#8cc3e8', '#e57a63'],
  // FR-39.3: the accessibility mode reaches charts too. Pushed to the extremes
  // of each hue so the slices separate maximally against pure white / black.
  // These two carried collisions of their own that the old ΔE floor missed —
  // gold↔brick at ΔE 13.1 in light, sky↔gold at 11.3 in dark — so they are
  // re-stepped rather than merely re-seeded.
  highContrastLight: ['#a02a94', '#644e08', '#123a5c', '#5c1410'],
  highContrastDark: ['#eeb4f8', '#ccd62c', '#0098e8', '#fa5e00'],
} as const;

/**
 * How many categories the pie palette can name. A chart with more categories
 * than this must roll the tail into an "Other" slice rather than reach for a
 * fifth colour — see `CategoryBreakdownChart`.
 */
export const CATEGORICAL_PIE_SLOTS = categoricalPieColors.light.length;

/** The label colour that sits on a slice — white in light mode, near-black in dark. */
export const pieLabelColor = { light: '#ffffff', dark: '#1b1a19' } as const;

/**
 * The pie palette for the active mode, honouring high contrast. Index by
 * position.
 *
 * It deliberately does NOT cycle. Wrapping used to be the documented behaviour,
 * and it meant a sixth vision area was painted the same colour as the first —
 * and at exactly five categories the repeat landed on the slice adjacent to it,
 * so with `stroke="none"` the two merged into one wedge with no boundary. A
 * repeated fill is a false identity claim, so an out-of-range slot returns the
 * last colour and callers are expected never to ask: `CATEGORICAL_PIE_SLOTS`
 * is the cap, and charts fold anything beyond it into a single "Other" slice.
 */
export function categoricalPieColor(index: number, mode: 'light' | 'dark' = 'light', highContrast = false): string {
  const highContrastKey = mode === 'dark' ? 'highContrastDark' : 'highContrastLight';
  const palette = categoricalPieColors[highContrast ? highContrastKey : mode];
  return palette[Math.min(Math.abs(index), palette.length - 1)];
}

// FR-41 removed `obstacleTypeColors`, the blue depth ramp the obstacle chart
// used to index by type. It was a static map, so it would have quietly bypassed
// the mode/high-contrast resolvers every other chart fill now goes through —
// the same trap the dead enum palettes fell into. Obstacle types are
// non-semantic categories, so they use `categoricalPieColor` instead.

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
  | 'violet';

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
    light: { main: '#0078d4', hover: '#006abb', pressed: '#005a9f', contrastText: '#ffffff', tint: '#deecf9', tintForeground: '#005a9e' },
    dark: { main: '#4ba0e1', hover: '#6bb1e6', pressed: '#408ac1', contrastText: '#1b1a19', tint: '#0e3a5c', tintForeground: '#9ec9ec' },
  },
  teal: {
    label: 'Teal',
    light: { main: '#038387', hover: '#037377', pressed: '#026265', contrastText: '#ffffff', tint: '#d5f0f0', tintForeground: '#026d70' },
    dark: { main: '#58c2c6', hover: '#76cdd0', pressed: '#419093', contrastText: '#1b1a19', tint: '#0c3536', tintForeground: '#9be0e2' },
  },
  purple: {
    label: 'Purple',
    light: { main: '#8764b8', hover: '#7758a2', pressed: '#654b8a', contrastText: '#ffffff', tint: '#ece5f6', tintForeground: '#5a4180' },
    dark: { main: '#a68ccc', hover: '#b6a1d5', pressed: '#927bb4', contrastText: '#1b1a19', tint: '#2b2140', tintForeground: '#cdbce6' },
  },
  green: {
    label: 'Green',
    light: { main: '#107c10', hover: '#0e6d0e', pressed: '#0c5d0c', contrastText: '#ffffff', tint: '#dff0df', tintForeground: '#0b5a0b' },
    dark: { main: '#6bbf6c', hover: '#86cb86', pressed: '#539554', contrastText: '#1b1a19', tint: '#16301c', tintForeground: '#a4d7a6' },
  },
  orange: {
    label: 'Orange',
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
    label: 'Magenta',
    light: { main: '#b4009e', hover: '#9c0089', pressed: '#8a0079', contrastText: '#ffffff', tint: '#f8e0f4', tintForeground: '#8a0079' },
    dark: { main: '#e06ecd', hover: '#e98fd8', pressed: '#cf5cbd', contrastText: '#1b1a19', tint: '#3d0f36', tintForeground: '#f3b6e8' },
  },
  red: {
    label: 'Red',
    light: { main: '#c4314b', hover: '#ad2942', pressed: '#9b2033', contrastText: '#ffffff', tint: '#fde7e9', tintForeground: '#9b2033' },
    dark: { main: '#e8737f', hover: '#ef9199', pressed: '#d96370', contrastText: '#1b1a19', tint: '#3f1418', tintForeground: '#f5b8be' },
  },
  brass: {
    label: 'Brass',
    light: { main: '#8e6a0b', hover: '#7d5d0a', pressed: '#6b5008', contrastText: '#ffffff', tint: '#f7ecd0', tintForeground: '#6b5008' },
    dark: { main: '#d4b158', hover: '#e0c37b', pressed: '#b8963f', contrastText: '#1b1a19', tint: '#332708', tintForeground: '#e8d69b' },
  },
  steel: {
    label: 'Steel',
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
    label: 'Vermilion',
    light: { main: '#B71833', hover: '#9c142c', pressed: '#7A0930', contrastText: '#ffffff', tint: '#FFE3D5', tintForeground: '#7A0930' },
    dark: { main: '#FFC1AC', hover: '#FFE3D5', pressed: '#FF8A7A', contrastText: '#1b1a19', tint: '#4a1220', tintForeground: '#FFC1AC' },
  },
  violet: {
    label: 'Violet',
    light: { main: '#8E33FF', hover: '#7522e0', pressed: '#5119B7', contrastText: '#ffffff', tint: '#EFD8FF', tintForeground: '#27097A' },
    dark: { main: '#C884FF', hover: '#EFD8FF', pressed: '#A85CFF', contrastText: '#1b1a19', tint: '#27097A', tintForeground: '#EFD8FF' },
  },
  pink: {
    label: 'Pink',
    light: { main: '#b8306e', hover: '#a32860', pressed: '#931f56', contrastText: '#ffffff', tint: '#fce4f1', tintForeground: '#931f56' },
    dark: { main: '#eb84b6', hover: '#f2a2c8', pressed: '#d1699b', contrastText: '#1b1a19', tint: '#3d1229', tintForeground: '#f7bdd8' },
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
  | 'midnight';

/** The wire value stored per account; `CUSTOM` is computed, never selected. */
export type StoredThemePreset = 'FLUENT_SYSTEM' | 'FLUENT_LIGHT' | 'FLUENT_DARK' | 'OCEAN' | 'FOREST' | 'SLATE' | 'MIDNIGHT' | 'CUSTOM';

export type ThemePresetDefinition = {
  id: ThemePresetId;
  stored: StoredThemePreset;
  label: string;
  description: string;
  mode: 'light' | 'dark' | 'system';
  accent: AccentId;
};

export const themePresets: ThemePresetDefinition[] = [
  { id: 'fluentSystem', stored: 'FLUENT_SYSTEM', label: 'Fluent System', description: 'Follows your device', mode: 'system', accent: 'blue' },
  { id: 'fluentLight', stored: 'FLUENT_LIGHT', label: 'Fluent Light', description: 'Always light, brand blue', mode: 'light', accent: 'blue' },
  { id: 'fluentDark', stored: 'FLUENT_DARK', label: 'Fluent Dark', description: 'Always dark, brand blue', mode: 'dark', accent: 'blue' },
  { id: 'ocean', stored: 'OCEAN', label: 'Ocean', description: 'Light and teal', mode: 'light', accent: 'teal' },
  { id: 'forest', stored: 'FOREST', label: 'Forest', description: 'Light and green', mode: 'light', accent: 'green' },
  { id: 'slate', stored: 'SLATE', label: 'Slate', description: 'Light and steel blue', mode: 'light', accent: 'steel' },
  { id: 'midnight', stored: 'MIDNIGHT', label: 'Midnight', description: 'Dark and purple', mode: 'dark', accent: 'purple' },
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
export type FontFamilyId = 'system' | 'publicSans' | 'inter' | 'dmSans' | 'nunitoSans';

/** The tail every stack shares — Fluent's face first, then each platform's own. */
const SYSTEM_STACK = '"Segoe UI Variable", "Segoe UI", -apple-system, BlinkMacSystemFont, Roboto, Helvetica, Arial, sans-serif';

export type FontFamilyDefinition = {
  id: FontFamilyId;
  /** Backend enum name. */
  stored: 'SYSTEM' | 'PUBLIC_SANS' | 'INTER' | 'DM_SANS' | 'NUNITO_SANS';
  label: string;
  description: string;
  stack: string;
};

export const fontFamilies: FontFamilyDefinition[] = [
  {
    id: 'system',
    stored: 'SYSTEM',
    label: 'System default',
    description: 'Matches your device — loads instantly',
    stack: SYSTEM_STACK,
  },
  {
    id: 'publicSans',
    stored: 'PUBLIC_SANS',
    label: 'Public Sans',
    description: 'Neutral and highly legible',
    stack: `"Public Sans Variable", ${SYSTEM_STACK}`,
  },
  {
    id: 'inter',
    stored: 'INTER',
    label: 'Inter',
    description: 'Designed for screens',
    stack: `"Inter Variable", ${SYSTEM_STACK}`,
  },
  {
    id: 'dmSans',
    stored: 'DM_SANS',
    label: 'DM Sans',
    description: 'Geometric and low-contrast',
    stack: `"DM Sans Variable", ${SYSTEM_STACK}`,
  },
  {
    id: 'nunitoSans',
    stored: 'NUNITO_SANS',
    label: 'Nunito Sans',
    description: 'Rounded and friendly',
    stack: `"Nunito Sans Variable", ${SYSTEM_STACK}`,
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
export type BackgroundToneId = 'neutral' | 'warm' | 'cool' | 'soft' | 'tinted' | 'flat';

export type BackgroundToneDefinition = {
  id: BackgroundToneId;
  /** Backend enum name. */
  stored: 'NEUTRAL' | 'WARM' | 'COOL' | 'SOFT' | 'TINTED' | 'FLAT';
  label: string;
  description: string;
  /** Swatch pair for the picker: [canvas, card]. `tinted` resolves at render time. */
  preview: { light: [string, string]; dark: [string, string] };
};

export const backgroundTones: BackgroundToneDefinition[] = [
  {
    id: 'neutral',
    stored: 'NEUTRAL',
    label: 'Neutral',
    description: 'The default',
    preview: { light: ['#fafafa', '#ffffff'], dark: ['#1b1a19', '#252423'] },
  },
  {
    id: 'warm',
    stored: 'WARM',
    label: 'Warm',
    description: 'Softer, cream-tinted',
    preview: { light: ['#faf7f2', '#fffdfa'], dark: ['#1f1b17', '#2a2521'] },
  },
  {
    id: 'cool',
    stored: 'COOL',
    label: 'Cool',
    description: 'Slight blue cast',
    preview: { light: ['#f6f8fa', '#ffffff'], dark: ['#171a1d', '#212529'] },
  },
  {
    id: 'soft',
    stored: 'SOFT',
    label: 'Soft',
    description: 'More separation',
    preview: { light: ['#eef0f2', '#ffffff'], dark: ['#141414', '#242424'] },
  },
  {
    id: 'tinted',
    stored: 'TINTED',
    label: 'Tinted',
    description: 'Follows your accent',
    // Resolved from the accent at render time; these stand in for the swatch.
    preview: { light: ['#f7fafd', '#ffffff'], dark: ['#16191c', '#202427'] },
  },
  {
    id: 'flat',
    stored: 'FLAT',
    label: 'Flat',
    description: 'No canvas step',
    preview: { light: ['#ffffff', '#ffffff'], dark: ['#1f1e1d', '#1f1e1d'] },
  },
];

export function toneFromStored(stored: string): BackgroundToneId {
  return backgroundTones.find((tone) => tone.stored === stored)?.id ?? 'neutral';
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
          background: '#1b1a19',
          card: '#252423',
          foreground: '#f3f2f1',
          mutedForeground: '#a19f9d',
          secondary: '#323130',
          border: '#3b3a39',
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
        background: grey[100],
        card: '#ffffff',
        foreground: grey[800],
        // FR-44: grey-700, not grey-600. Secondary text sits on the page canvas
        // as well as on cards (a page subtitle is not inside a card), and
        // grey-600 measures only 4.88:1 on white — it drops under 4.5:1 the
        // moment a background tone tints the canvas, failing on Cool (4.37) and
        // Soft (4.27). grey-700 holds 7.29:1 in the worst case while keeping a
        // clear step below the grey-800 body text.
        mutedForeground: grey[700],
        secondary: grey[200],
        border: grey[300],
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
  purple: '#5c3d8f',
  orange: '#a32c00',
  red: '#a4262c',
  green: '#0a5c0a',
} as const;

const HC_DARK = {
  neutralIdle: '#b8b5b2',
  neutralHeld: '#d6d3d1',
  blue: '#7cc0f0',
  purple: '#c4a5e8',
  orange: '#ff8a4d',
  red: '#ff9ba0',
  green: '#6fd070',
} as const;

function highContrastStatusMap(mode: 'light' | 'dark'): Record<StatusToken, string> {
  const c = mode === 'dark' ? HC_DARK : HC_LIGHT;
  return {
    NOT_STARTED: c.neutralIdle,
    IN_PROGRESS: c.blue,
    WAITING: c.purple,
    BLOCKED: c.orange,
    PAUSED: c.neutralHeld,
    COMPLETED: c.green,

    IDEA: c.neutralIdle,
    ACTIVE: c.blue,
    ARCHIVED: c.neutralHeld,

    TO_CONTACT: c.neutralIdle,
    CONTACTED: c.blue,
    DECLINED: c.red,

    OPEN: c.orange,
    RESOLVED: c.green,
    ACCEPTED: c.neutralHeld,

    DRAFT: c.neutralIdle,
    SENT: c.blue,
    FOLLOWED_UP: c.purple,
    REPLIED: c.blue,
    CLOSED: c.green,

    IN_REVIEW: c.purple,
    PLANNED: c.blue,
    WONT_FIX: c.neutralHeld,
  };
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
    return neutralFallback;
  }
  return mode === 'dark' ? HC_DARK.neutralIdle : HC_LIGHT.neutralIdle;
}

/** The status hue in effect, honouring high contrast (BR-34). */
export function statusColor(token: StatusToken, mode: 'light' | 'dark' = 'light', highContrast = false): string {
  const palette = highContrast ? highContrastStatusMap(mode) : statusColors;
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
 * The status/priority palettes above are shared with charts and badges and
 * stay identical in both modes: they are mid-tone hues chosen to survive on
 * either surface, and a "Completed" green that changed between modes would
 * break the one-source-of-truth rule they exist for.
 */
export function buildTheme(
  mode: 'light' | 'dark',
  accent: AccentId = 'blue',
  density: Density = 'comfortable',
  highContrast = false,
  tone: BackgroundToneId = 'neutral',
  font: FontFamilyId = 'system',
) {
  const dark = mode === 'dark';
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
      // FR-42: must match the --font-family variable ThemeModeProvider sets on
      // :root. global.css's unlayered rule is what actually renders text, so if
      // these two disagree, MUI components and plain elements use different
      // faces.
      fontFamily: fontStack(font),
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
      // Flyouts (Select/autocomplete dropdowns, the row-actions Menu) render
      // through MUI's Popover, which defaults to a generic Material elevation-8
      // drop-shadow. Replace it with Fluent's flyout tier (--shadow-8 from
      // global.css) so menus sit on the same elevation scale as Card (shadow-4)
      // and Dialog (shadow-16) — a menu reads as raised above the page but
      // below a modal. backgroundImage:none drops MUI dark-mode's overlay
      // gradient, matching the flat Fluent surfaces used by Card/Dialog.
      MuiPopover: {
        styleOverrides: {
          paper: {
            boxShadow: '0 3.2px 7.2px rgba(0,0,0,0.13), 0 0.6px 1.8px rgba(0,0,0,0.08)',
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
