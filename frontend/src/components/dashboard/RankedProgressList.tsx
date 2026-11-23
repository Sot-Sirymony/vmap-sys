import { useState } from 'react';
import { Link } from 'react-router';
import Box from '@mui/material/Box';
import { keyframes } from '@mui/material/styles';
import { ChevronRight } from 'lucide-react';
import { useReducedMotion } from '../../hooks/useReducedMotion';
import { tr } from '../../i18n/translate';
import { formatPercent } from '../../utils/format';
import { tabular } from './surface';

const grow = keyframes`
  from { transform: scaleX(0); }
  to { transform: scaleX(1); }
`;

export type RankedProgressItem = {
  id: string | number;
  label: string;
  /** 0–100. */
  value: number;
  /** Where the plan expected to be today, 0–100; draws a tick (BRD 25 Q-25-2). */
  target?: number;
  caption?: string;
  /** A CSS colour for the dot and bar, e.g. `var(--chart-1)`. Defaults to the brand. */
  color?: string;
  to?: string;
};

type RankedProgressListProps = {
  items: RankedProgressItem[];
  /** Show only the lowest N, with a "Show all" button (mobile). */
  limit?: number;
  /** Names the list for screen readers. */
  label: string;
};

/**
 * BRD 25 §7.2 — progress bars ranked lowest first, so the top row is the one
 * that needs attention (decision 3). Each bar can carry a target tick.
 */
export function RankedProgressList({ items, limit, label }: RankedProgressListProps) {
  const reduced = useReducedMotion();
  const [showAll, setShowAll] = useState(false);
  const ranked = [...items].sort((a, b) => a.value - b.value || a.label.localeCompare(b.label));
  const visible = limit && !showAll ? ranked.slice(0, limit) : ranked;

  return (
    <Box>
      <Box component="ul" aria-label={label} sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 0.5 }}>
        {visible.map((item) => {
          const value = Math.min(100, Math.max(0, item.value));
          const color = item.color ?? 'var(--primary)';
          const row = (
            <>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, minWidth: 0, fontWeight: 500, gridColumn: 1, gridRow: 1 }}>
                <Box component="span" sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: color, flex: 'none' }} />
                <Box component="span" sx={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{item.label}</Box>
              </Box>
              {item.caption && (
                <Box sx={{ gridColumn: 1, gridRow: 2, pl: 2, fontSize: 'var(--font-caption)', color: 'var(--text-tertiary)' }}>{item.caption}</Box>
              )}
              <Box sx={{ gridColumn: { xs: '1', sm: '2' }, gridRow: { xs: 3, sm: '1 / 3' }, alignSelf: 'center' }}>
                <Box
                  role="img"
                  aria-label={item.target === undefined ? `${item.label} ${formatPercent(value)}` : `${item.label} ${formatPercent(value)}, ${tr('Target {n}%', { n: Math.round(item.target) })}`}
                  sx={{ position: 'relative', height: 8, borderRadius: 'var(--radius-pill)', bgcolor: 'var(--surface-sunken)' }}
                >
                  <Box
                    sx={{
                      position: 'absolute', inset: 0, width: `${value}%`, borderRadius: 'inherit', bgcolor: color,
                      transformOrigin: 'left', animation: reduced ? 'none' : `${grow} 600ms var(--motion-ease) both`,
                    }}
                  />
                  {item.target !== undefined && (
                    <Box
                      data-testid="target-tick"
                      sx={{ position: 'absolute', top: -4, bottom: -4, width: 2, ml: '-1px', left: `${Math.min(100, Math.max(0, item.target))}%`, borderRadius: '1px', bgcolor: 'var(--text-primary)', opacity: 0.55 }}
                    />
                  )}
                </Box>
              </Box>
              <Box sx={{ ...tabular, gridColumn: { xs: 2, sm: 3 }, gridRow: { xs: '1 / 4', sm: '1 / 3' }, alignSelf: 'center', textAlign: 'right', fontWeight: 600 }}>
                {formatPercent(value)}
              </Box>
              <Box sx={{ gridColumn: { xs: 3, sm: 4 }, gridRow: { xs: '1 / 4', sm: '1 / 3' }, alignSelf: 'center', display: 'inline-flex', color: 'var(--text-tertiary)' }}>
                {item.to && <ChevronRight size={16} aria-hidden="true" />}
              </Box>
            </>
          );
          const rowSx = {
            display: 'grid',
            gridTemplateColumns: { xs: 'minmax(0, 1fr) 44px 16px', sm: 'minmax(96px, 1fr) minmax(0, 2fr) 44px 16px' },
            columnGap: 2,
            rowGap: 0.5,
            alignItems: 'center',
            px: 1,
            py: 1.25,
            mx: -1,
            borderRadius: 'var(--radius-md)',
            color: 'inherit',
            textDecoration: 'none',
          } as const;
          return (
            <li key={item.id}>
              {item.to ? (
                <Box component={Link} to={item.to} sx={{ ...rowSx, transition: 'background var(--motion-fast) var(--motion-ease)', '&:hover': { bgcolor: 'var(--surface-sunken)' } }}>
                  {row}
                </Box>
              ) : (
                <Box sx={rowSx}>{row}</Box>
              )}
            </li>
          );
        })}
      </Box>
      {limit && ranked.length > limit && (
        <Box
          component="button"
          type="button"
          aria-expanded={showAll}
          onClick={() => setShowAll((value) => !value)}
          sx={{ mt: 1, border: 0, bgcolor: 'transparent', p: '2px 0', color: 'var(--primary)', font: 'inherit', fontSize: '0.8125rem', fontWeight: 500, cursor: 'pointer', '&:hover': { textDecoration: 'underline' } }}
        >
          {showAll ? tr('Show fewer') : tr('Show all')}
        </Box>
      )}
    </Box>
  );
}
