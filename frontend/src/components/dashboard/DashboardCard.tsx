import { Link } from 'react-router';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import type { LucideIcon } from 'lucide-react';
import { semanticTints } from '../../theme';

export type DashboardCardTone = 'neutral' | 'positive' | 'warning' | 'critical';

// Fluent's semantic trio (Success/Warning/Danger) — shared via theme.ts so a
// tile's accent means the same thing everywhere (BR-15).
const TONE_STYLES: Record<DashboardCardTone, { bg: string; fg: string }> = semanticTints;

type DashboardCardProps = {
  label: string;
  value: string | number;
  icon: LucideIcon;
  tone?: DashboardCardTone;
  /**
   * Where clicking the tile goes — a list view filtered to exactly the rows this
   * number counted. Only set it when a filter reproduces the count precisely; a
   * tile that lands on a different number is worse than one that doesn't link.
   */
  to?: string;
};

export function DashboardCard({ label, value, icon: Icon, tone = 'neutral', to }: DashboardCardProps) {
  const { bg, fg } = TONE_STYLES[tone];

  const card = (
    <Card
      sx={
        to
          ? {
              height: '100%',
              transition: (theme) => theme.transitions.create(['border-color', 'box-shadow'], { duration: 120 }),
              '&:hover': {
                borderColor: 'primary.main',
                boxShadow: '0 1.6px 3.6px rgba(0,0,0,0.13), 0 0.3px 0.9px rgba(0,0,0,0.10)',
              },
            }
          : { height: '100%' }
      }
    >
      <CardContent>
        <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
          <Typography variant="body2" color="text.secondary">{label}</Typography>
          <Box sx={{ width: 28, height: 28, borderRadius: 'var(--radius-sm)', display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: bg, color: fg }}>
            <Icon size={16} />
          </Box>
        </Stack>
        {/* A stat value, not a document heading (axe heading-order) — page-title size via the ramp. */}
        <Typography variant="h1" component="p">{value}</Typography>
      </CardContent>
    </Card>
  );

  if (!to) {
    return card;
  }

  return (
    <Link to={to} aria-label={`${label}: ${value}. View these records.`} style={{ textDecoration: 'none', display: 'block', height: '100%' }}>
      {card}
    </Link>
  );
}
