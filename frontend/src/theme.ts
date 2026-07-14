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

const theme = createTheme({
  palette: {
    mode: 'light',
    background: {
      default: '#ffffff', // --background
      paper: '#ffffff', // --card / --popover
    },
    text: {
      primary: '#242424', // --foreground (Fluent neutralForeground1)
      secondary: '#616161', // --muted-foreground (Fluent neutralForeground2)
    },
    primary: {
      main: '#0078d4', // --primary (Fluent Communication Blue)
      dark: '#005a9e', // pressed
      light: '#106ebe', // hover
      contrastText: '#ffffff', // --primary-foreground
    },
    secondary: {
      main: '#f5f5f5', // --secondary / --muted (Fluent neutralBackground3)
      contrastText: '#242424', // --secondary-foreground
    },
    error: {
      main: '#d13438', // --destructive (Fluent Danger)
    },
    divider: '#e1e1e1', // --border / --input (Fluent neutralStroke)
  },
  shape: {
    borderRadius: 4, // --radius: Fluent's 4px corner radius
  },
  typography: {
    fontFamily: '"Segoe UI Variable", "Segoe UI", -apple-system, BlinkMacSystemFont, Roboto, Helvetica, Arial, sans-serif',
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
          border: '1px solid #e1e1e1',
          boxShadow: '0 1.6px 3.6px rgba(0,0,0,0.10), 0 0.3px 0.9px rgba(0,0,0,0.06)',
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
            outline: '2px solid #0078d4',
            outlineOffset: '1px',
          },
        },
      },
    },
  },
});

export default theme;
