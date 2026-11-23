/**
 * BRD 25 §6 — the level-1 card: surface, hairline border, soft shadow (none in
 * dark mode, where --shadow-2 is heavier but the border carries the edge),
 * 12px radius, 20px padding (16px on phones). One definition, so every V25
 * card has the same edges and padding.
 */
export const cardSurface = {
  bgcolor: 'var(--surface)',
  color: 'var(--text-primary)',
  border: '1px solid var(--border)',
  borderRadius: 'var(--radius-lg)',
  boxShadow: 'var(--shadow-2)',
  p: { xs: '16px', sm: '20px' },
  minWidth: 0,
  position: 'relative',
} as const;

/** Hover lift for clickable cards (level 2): shadow and border only, no movement. */
export const cardHover = {
  transition: 'box-shadow var(--motion-fast) var(--motion-ease), border-color var(--motion-fast) var(--motion-ease)',
  '&:hover': { boxShadow: 'var(--shadow-4)', borderColor: 'var(--border-strong)' },
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
