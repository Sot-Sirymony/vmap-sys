import { Link } from 'react-router';
import Box from '@mui/material/Box';
import { ArrowDown, ArrowUp, CircleCheck } from 'lucide-react';
import { useCountUp } from '../../hooks/useCountUp';
import { useReducedMotion } from '../../hooks/useReducedMotion';
import { tr } from '../../i18n/translate';
import { formatNumber } from '../../utils/format';
import { cardHover, cardSurface, srOnly, tabular } from './surface';

export type KpiDelta = {
  /** The text beside the arrow, e.g. "1 more than last week" or "+8 pts". */
  text: string;
  direction: 'up' | 'down';
  /** Whether this change is good news. Colour is used only when it is meaningful. */
  tone: 'good' | 'bad' | 'neutral';
};

type KpiCardProps = {
  label: string;
  value: number;
  format?: (value: number) => string;
  /** One quiet line under the number. */
  caption?: string;
  delta?: KpiDelta;
  /** Show a calm "All clear" instead of 0 (Overdue, Blocked — DA-FR-05). */
  clearWhenZero?: boolean;
  /** A list filtered to exactly the rows this number counts. */
  to?: string;
};

const DELTA_COLOR: Record<KpiDelta['tone'], string> = {
  good: 'var(--st-done-fg)',
  bad: 'var(--st-declined-fg)',
  neutral: 'var(--text-secondary)',
};

/**
 * BRD 25 §11 — a KPI: quiet label above, big tabular number, optional delta and
 * caption. The number counts up (not under reduced motion); screen readers get
 * the final value only.
 */
export function KpiCard({ label, value, format = formatNumber, caption, delta, clearWhenZero = false, to }: KpiCardProps) {
  const reduced = useReducedMotion();
  const shown = useCountUp(value, reduced);
  const clear = clearWhenZero && value === 0;
  const DeltaIcon = delta?.direction === 'down' ? ArrowDown : ArrowUp;

  const body = (
    <>
      <Box component="span" sx={{ fontSize: '0.8125rem', lineHeight: 1.3, color: 'var(--text-secondary)' }}>
        {label}
      </Box>
      <Box component="span" sx={{ display: 'flex', alignItems: 'baseline', flexWrap: 'wrap', columnGap: 1.25 }}>
        {clear ? (
          <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.75, color: 'var(--st-done-fg)', fontSize: '1.25rem', fontWeight: 600, lineHeight: '44px' }}>
            <CircleCheck size={24} strokeWidth={1.75} aria-hidden="true" />
            {tr('All clear')}
          </Box>
        ) : (
          <>
            <Box component="span" sx={srOnly}>{format(value)}</Box>
            <Box
              component="span"
              aria-hidden="true"
              data-testid="kpi-number"
              sx={{ ...tabular, fontSize: { xs: '1.75rem', sm: '2.5rem' }, lineHeight: { xs: '34px', sm: '44px' }, fontWeight: 600, letterSpacing: '-0.5px' }}
            >
              {format(shown)}
            </Box>
          </>
        )}
        {delta && !clear && (
          <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.25, fontSize: 'var(--font-caption)', fontWeight: 600, color: DELTA_COLOR[delta.tone] }}>
            <DeltaIcon size={14} strokeWidth={2} aria-hidden="true" />
            {delta.text}
          </Box>
        )}
      </Box>
      {caption && (
        <Box component="span" sx={{ fontSize: 'var(--font-caption)', lineHeight: 1.35, color: 'var(--text-tertiary)' }}>
          {caption}
        </Box>
      )}
    </>
  );

  const layout = { ...cardSurface, display: 'flex', flexDirection: 'column', gap: 0.5, textDecoration: 'none' } as const;
  if (to) {
    return (
      <Box component={Link} to={to} sx={{ ...layout, ...cardHover, color: 'inherit' }}>
        {body}
      </Box>
    );
  }
  return <Box sx={layout}>{body}</Box>;
}
