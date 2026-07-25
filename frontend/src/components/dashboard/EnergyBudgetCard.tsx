import { BatteryCharging, BatteryLow, Minus } from 'lucide-react';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';

import { EmptyState } from '../common/EmptyState';
import { semanticTints } from '../../theme';
import type { DashboardEnergyBudget } from '../../types/vision';

/**
 * FR-34.2: the weekly Energy Budget. Shows this week's tasks split by energy
 * demand and the net balance of charging vs. draining work. Colors come from
 * the theme's semantic tints (BR-15) — charge reads as positive, drain as a
 * warning — never a raw hue. Diagnostic only: it never changes any task.
 */
export function EnergyBudgetCard({ budget }: { budget?: DashboardEnergyBudget }) {
  const charge = budget?.charge ?? 0;
  const neutral = budget?.neutral ?? 0;
  const drain = budget?.drain ?? 0;
  const net = budget?.net ?? 0;
  const total = charge + neutral + drain;

  const cells = [
    { label: 'Charge', count: charge, tint: semanticTints.positive },
    { label: 'Neutral', count: neutral, tint: semanticTints.neutral },
    { label: 'Drain', count: drain, tint: semanticTints.warning },
  ];

  return (
    <Card>
      <CardHeader
        title="Energy budget"
        subheader="This week's tasks by energy demand — charging work weighed against draining work"
      />
      <CardContent>
        {total === 0 ? (
          <EmptyState>No tasks due this week — nothing to weigh yet.</EmptyState>
        ) : (
          <Stack spacing={2}>
            <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 1.5 }}>
              {cells.map((cell) => (
                <Box
                  key={cell.label}
                  sx={{ borderRadius: 'var(--radius)', bgcolor: cell.tint.bg, color: cell.tint.fg, py: 1.5, px: 1, textAlign: 'center' }}
                >
                  <Typography component="p" sx={{ fontSize: '1.5rem', fontWeight: 700, lineHeight: 1.1 }}>
                    {cell.count}
                  </Typography>
                  <Typography variant="caption" sx={{ fontWeight: 600 }}>{cell.label}</Typography>
                </Box>
              ))}
            </Box>
            <BalanceLine net={net} />
          </Stack>
        )}
      </CardContent>
    </Card>
  );
}

// One sentence naming which way the week leans, so the three counts add up to a
// takeaway rather than leaving the reader to do the subtraction.
function BalanceLine({ net }: { net: number }) {
  if (net === 0) {
    return (
      <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center', color: 'text.secondary' }}>
        <Minus size={16} />
        <Typography variant="body2">Balanced — charging and draining work are even this week.</Typography>
      </Stack>
    );
  }
  const draining = net < 0;
  const Icon = draining ? BatteryLow : BatteryCharging;
  const tone = draining ? semanticTints.warning.fg : semanticTints.positive.fg;
  return (
    <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center', color: tone }}>
      <Icon size={16} />
      <Typography variant="body2" sx={{ fontWeight: 600 }}>
        Net {net > 0 ? `+${net}` : net} — this week leans {draining ? 'draining' : 'energising'}
        {draining ? '. Consider what you could move.' : '.'}
      </Typography>
    </Stack>
  );
}
