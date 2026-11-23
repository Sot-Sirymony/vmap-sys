import Box from '@mui/material/Box';
import { keyframes } from '@mui/material/styles';
import { useCountUp } from '../../hooks/useCountUp';
import { useReducedMotion } from '../../hooks/useReducedMotion';
import { formatNumber } from '../../utils/format';
import { tabular } from './surface';

const ringIn = keyframes`
  from { stroke-dashoffset: var(--ring-circumference); }
`;

type ProgressRingProps = {
  /** 0–100. */
  value: number;
  /** Names the ring for screen readers, e.g. "Overall progress". */
  label: string;
  /** Outer size in px. 120–140 on desktop, 64–72 on phones. */
  size?: number;
  thickness?: number;
};

/**
 * BRD 25 §7.2 — a single proportion as a radial ring: brand fill on a sunken
 * track, rounded end, the number in the middle. Grows in once and counts up,
 * neither under reduced motion.
 */
export function ProgressRing({ value, label, size = 120, thickness = 10 }: ProgressRingProps) {
  const reduced = useReducedMotion();
  const percent = Math.min(100, Math.max(0, Math.round(value)));
  const shown = useCountUp(percent, reduced);
  const radius = 50 - thickness / 2;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference * (1 - percent / 100);

  return (
    <Box role="img" aria-label={`${label}: ${formatNumber(percent)}%`} sx={{ position: 'relative', width: size, height: size, flex: 'none' }}>
      <Box component="svg" viewBox="0 0 100 100" sx={{ width: '100%', height: '100%', transform: 'rotate(-90deg)', display: 'block' }} aria-hidden="true">
        <circle cx="50" cy="50" r={radius} fill="none" stroke="var(--surface-sunken)" strokeWidth={thickness} />
        <Box
          component="circle"
          data-testid="ring-fill"
          cx="50"
          cy="50"
          r={radius}
          fill="none"
          stroke="var(--primary)"
          strokeWidth={thickness}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          style={{ ['--ring-circumference' as string]: `${circumference}` }}
          sx={{ animation: reduced ? 'none' : `${ringIn} 600ms var(--motion-ease) both`, transition: reduced ? 'none' : 'stroke-dashoffset 600ms var(--motion-ease)' }}
        />
      </Box>
      <Box
        aria-hidden="true"
        sx={{ ...tabular, position: 'absolute', inset: 0, display: 'grid', placeItems: 'center', fontWeight: 600, letterSpacing: '-0.5px', fontSize: size >= 100 ? '2.5rem' : '1.25rem', lineHeight: 1 }}
      >
        <span>
          {formatNumber(shown)}
          <Box component="small" sx={{ fontSize: '0.45em', fontWeight: 600, color: 'var(--text-secondary)', ml: '1px' }}>%</Box>
        </span>
      </Box>
    </Box>
  );
}
