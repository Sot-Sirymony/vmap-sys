import { Link } from 'react-router';
import Box from '@mui/material/Box';
import { keyframes } from '@mui/material/styles';
import { useReducedMotion } from '../../hooks/useReducedMotion';
import { formatNumber } from '../../utils/format';
import { tabular } from './surface';

const grow = keyframes`
  from { transform: scaleX(0); }
  to { transform: scaleX(1); }
`;

export type RankedBarItem = { id: string | number; label: string; value: number; to?: string };

type RankedBarsProps = {
  items: RankedBarItem[];
  /** A CSS colour for the bars, e.g. `var(--st-open)`. */
  color: string;
  /** Names the list for screen readers. */
  label: string;
};

/**
 * BRD 25 §7.2 — counts as ranked horizontal bars, largest first, each labelled
 * directly with its name and number (no legend). Bars are relative to the
 * largest count.
 */
export function RankedBars({ items, color, label }: RankedBarsProps) {
  const reduced = useReducedMotion();
  const ranked = [...items].sort((a, b) => b.value - a.value || a.label.localeCompare(b.label));
  const max = Math.max(1, ...ranked.map((item) => item.value));
  return (
    <Box component="ul" aria-label={label} sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 1.5 }}>
      {ranked.map((item) => {
        const name = item.to ? (
          <Box component={Link} to={item.to} sx={{ color: 'inherit', textDecoration: 'none', '&:hover': { textDecoration: 'underline' } }}>{item.label}</Box>
        ) : (
          <span>{item.label}</span>
        );
        return (
          <li key={item.id}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1, mb: 0.75, fontWeight: 500, minWidth: 0 }}>
              <Box sx={{ minWidth: 0, overflowWrap: 'anywhere' }}>{name}</Box>
              <Box component="span" sx={{ ...tabular, color: 'var(--text-secondary)', fontWeight: 400 }}>{formatNumber(item.value)}</Box>
            </Box>
            <Box aria-hidden="true" sx={{ position: 'relative', height: 6, borderRadius: 'var(--radius-pill)', bgcolor: 'var(--surface-sunken)' }}>
              <Box
                sx={{
                  position: 'absolute', inset: 0, width: `${(item.value / max) * 100}%`, borderRadius: 'inherit', bgcolor: color,
                  transformOrigin: 'left', animation: reduced ? 'none' : `${grow} 600ms var(--motion-ease) both`,
                }}
              />
            </Box>
          </li>
        );
      })}
    </Box>
  );
}
