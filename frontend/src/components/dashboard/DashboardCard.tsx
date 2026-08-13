import type { ReactNode } from 'react';
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
  /** Watermark glyph on the rail variant; the plain tile carries no icon. */
  icon?: LucideIcon;
  tone?: DashboardCardTone;
  /**
   * 'rail' is the comp's pressure tile: a colour bar on the left edge, the
   * count tinted to match, the icon as a low-opacity watermark on the right.
   * 'plain' (default) is the quiet portfolio tile: caps label over the number.
   */
  variant?: 'rail' | 'plain';
  /** Rendered beside the number — the Average Progress tile's inline bar. */
  extra?: ReactNode;
  /**
   * Where clicking the tile goes — a list view filtered to exactly the rows this
   * number counted. Only set it when a filter reproduces the count precisely; a
   * tile that lands on a different number is worse than one that doesn't link.
   */
  to?: string;
};

export function DashboardCard({ label, value, icon: Icon, tone = 'neutral', variant = 'plain', extra, to }: DashboardCardProps) {
  const { fg } = TONE_STYLES[tone];
  const tinted = tone !== 'neutral';

  const card = (
    <Card
      sx={{
        height: '100%',
        position: 'relative',
        overflow: 'hidden',
        ...(to
          ? {
              transition: (theme) => theme.transitions.create(['border-color', 'box-shadow'], { duration: 120 }),
              '&:hover': {
                borderColor: 'primary.main',
                boxShadow: '0 1.6px 3.6px rgba(0,0,0,0.13), 0 0.3px 0.9px rgba(0,0,0,0.10)',
              },
            }
          : {}),
      }}
    >
      {variant === 'rail' && (
        <Box aria-hidden sx={{ position: 'absolute', left: 0, top: 0, bottom: 0, width: 4, bgcolor: tinted ? fg : 'primary.main' }} />
      )}
      <CardContent sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1.5 }}>
        <Box sx={{ minWidth: 0, flex: 1 }}>
          <Typography
            variant="overline"
            component="p"
            sx={{ color: 'text.secondary', fontWeight: 600, letterSpacing: '0.06em', lineHeight: 1.4, display: 'block', mb: 0.5 }}
          >
            {label}
          </Typography>
          <Stack direction="row" sx={{ alignItems: 'center', gap: 1.5 }}>
            {/* A stat value, not a document heading (axe heading-order) — page-title size via the ramp. */}
            <Typography variant="h1" component="p" sx={{ lineHeight: 1, ...(tinted && { color: fg }) }}>
              {value}
            </Typography>
            {extra}
          </Stack>
        </Box>
        {variant === 'rail' && Icon && (
          <Box aria-hidden sx={{ color: tinted ? fg : 'primary.main', opacity: 0.2, display: 'flex', flexShrink: 0 }}>
            <Icon size={40} strokeWidth={1.6} />
          </Box>
        )}
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
