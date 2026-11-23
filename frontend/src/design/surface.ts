/**
 * BRD 26 (D-26-2) — the shared surface helper. Components take their radius,
 * shadow, surface, blur and border from here (or from the --style-* tokens
 * directly), never from literals, so every surface follows the interface style.
 *
 *   card      solid content surface: KPI, chart and insight cards, panels
 *   floating  a layer above content (sidebar, header, menus, dialogs, the
 *             Today hero card). Glass in Liquid Glass, raised solid otherwise
 *   dense     solid container for rows that scroll: tables, lists, timelines.
 *             Never glass (D-26-3), no padding
 *   inset     a child of a floating layer, or a sunken well inside a card.
 *             Always solid (D-26-4)
 *
 * The values are CSS variables, so a surface switches style, mode, tone and
 * the glass fallbacks (D-26-6) with no re-render.
 */
export type SurfaceKind = 'card' | 'floating' | 'dense' | 'inset';

export type SurfaceOptions = {
  /**
   * Floating only. `canvas` is for a layer that sits over the plain app
   * canvas, where the backdrop is known and glass may be thinner (D-26-5).
   * Anything else, including a sticky header with content under it, uses the
   * default floor.
   */
  over?: 'canvas' | 'unknown';
  /** Card only: adds the hover lift for clickable cards. */
  interactive?: boolean;
  /** Card only: leave out the padding (the caller lays out its own). */
  flush?: boolean;
};

const cardHoverSx = {
  transition: 'box-shadow var(--motion-fast) var(--motion-ease), border-color var(--motion-fast) var(--motion-ease)',
  '&:hover': { boxShadow: 'var(--style-shadow-hover)', borderColor: 'var(--style-hover-border)' },
} as const;

export function surface(kind: SurfaceKind, options: SurfaceOptions = {}): Record<string, unknown> {
  switch (kind) {
    case 'card':
      return {
        bgcolor: 'var(--style-card-bg)',
        color: 'var(--text-primary)',
        border: 'var(--style-card-border)',
        borderRadius: 'var(--style-radius-card)',
        boxShadow: 'var(--style-shadow-rest)',
        ...(options.flush ? {} : { p: { xs: 'var(--style-card-pad-phone)', sm: 'var(--style-card-pad)' } }),
        minWidth: 0,
        position: 'relative',
        ...(options.interactive ? cardHoverSx : {}),
      };
    case 'floating':
      return {
        bgcolor: options.over === 'canvas' ? 'var(--style-floating-bg-canvas)' : 'var(--style-floating-bg)',
        color: 'var(--text-primary)',
        backdropFilter: 'var(--style-blur)',
        WebkitBackdropFilter: 'var(--style-blur)',
        border: 'var(--style-floating-border)',
        boxShadow: 'var(--style-highlight), var(--style-shadow-overlay)',
        // D-26-5: tertiary text is promoted to secondary on glass.
        '--text-tertiary': 'var(--style-floating-text-tertiary)',
      };
    case 'dense':
      return {
        bgcolor: 'var(--style-card-bg)',
        border: 'var(--style-card-border)',
        borderRadius: 'var(--style-radius-card)',
      };
    case 'inset':
      return {
        bgcolor: 'var(--style-inset-bg)',
        borderRadius: 'var(--style-radius-control)',
      };
    default:
      return {};
  }
}

/** The bordered, unfilled box used for grouped content inside a card or form. */
export const outlinedGroup = {
  border: 'var(--style-card-border)',
  borderRadius: 'var(--style-radius-control)',
} as const;
