import { Link } from 'react-router-dom';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import type { LucideIcon } from 'lucide-react';

export type DashboardCardTone = 'neutral' | 'positive' | 'warning' | 'critical';

// Fluent's semantic trio (Success/Warning/Danger) — the same three hex
// values used for the overdue/blocked accents on the Tasks Board and List
// view, so a tile's accent means the same thing here as it does there.
const TONE_STYLES: Record<DashboardCardTone, { bg: string; fg: string }> = {
  neutral: { bg: '#f5f5f5', fg: '#616161' },
  positive: { bg: '#dff6dd', fg: '#107c10' },
  warning: { bg: '#fdece3', fg: '#d83b01' },
  critical: { bg: '#fde7e9', fg: '#d13438' },
};

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
          <Box sx={{ width: 28, height: 28, borderRadius: '4px', display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: bg, color: fg }}>
            <Icon size={15} />
          </Box>
        </Stack>
        <Typography variant="h5" sx={{ fontWeight: 600 }}>{value}</Typography>
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
