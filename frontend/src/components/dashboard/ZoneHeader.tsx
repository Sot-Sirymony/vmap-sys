import type { ReactNode } from 'react';
import Box from '@mui/material/Box';

type ZoneHeaderProps = {
  label: string;
  /** Optional right-aligned action, e.g. a "Customize" link. */
  action?: ReactNode;
  id?: string;
};

/**
 * BRD 25 §3 — a zone label (Today, Plan health, Insights). Zones are not boxed;
 * they start with this overline and 32px of space. Khmer is never upper-cased
 * or letter-spaced.
 */
export function ZoneHeader({ label, action, id }: ZoneHeaderProps) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 2 }}>
      <Box
        component="h2"
        id={id}
        sx={{
          m: 0,
          fontSize: 'var(--font-caption)',
          lineHeight: 1.4,
          fontWeight: 600,
          color: 'var(--text-tertiary)',
          textTransform: 'uppercase',
          letterSpacing: '0.06em',
          ':root:lang(km) &': { textTransform: 'none', letterSpacing: 0 },
        }}
      >
        {label}
      </Box>
      {action}
    </Box>
  );
}
