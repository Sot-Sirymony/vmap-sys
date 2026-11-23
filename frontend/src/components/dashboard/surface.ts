import { surface } from '../../design/surface';

/**
 * BRD 25 §6 — the level-1 card, now from the shared BRD 26 surface helper, so
 * every V25 card follows the interface style (radius, shadow, padding).
 * Cards stay solid in every style, Liquid Glass included (D-26-3).
 */
export const cardSurface = surface('card');

/** Hover lift for clickable cards (level 2): shadow and border only, no movement. */
export const cardHover = {
  transition: 'box-shadow var(--motion-fast) var(--motion-ease), border-color var(--motion-fast) var(--motion-ease)',
  '&:hover': { boxShadow: 'var(--style-shadow-hover)', borderColor: 'var(--style-hover-border)' },
} as const;

/** Visually hidden but read by screen readers. */
export const srOnly = {
  position: 'absolute',
  width: 1,
  height: 1,
  overflow: 'hidden',
  clip: 'rect(0 0 0 0)',
  whiteSpace: 'nowrap',
} as const;

/** Tabular figures, so digits line up and count-ups do not jitter (BRD 25 §4). */
export const tabular = { fontVariantNumeric: 'tabular-nums' };
