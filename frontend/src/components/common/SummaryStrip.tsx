import Chip from '@mui/material/Chip';
import { semanticTints } from '../../theme';

export type SummaryChipTone = 'neutral' | 'warning' | 'critical' | 'positive';

export type SummaryChipItem = {
  key: string;
  label: string;
  count: number;
  tone?: SummaryChipTone;
  /** Toggles the matching filter; chips without onClick are informational. */
  onClick?: () => void;
  /** True while the matching filter is applied. */
  active?: boolean;
};

/**
 * FR-27.1 summary strip: the page's vital signs as clickable chips above the
 * table — "12 goals · 3 overdue · 2 blocked". Clicking a chip applies the
 * matching filter; clicking again clears it. Zero-count chips disappear
 * (except the total) so the strip only ever names real work.
 */
export function SummaryStrip({ chips }: { chips: SummaryChipItem[] }) {
  const shown = chips.filter((chip, index) => index === 0 || chip.count > 0 || chip.active);
  if (shown.length <= 1) {
    return null;
  }
  return (
    <div className="summary-strip">
      {shown.map((chip) => {
        const tint = chip.tone && chip.tone !== 'neutral' ? semanticTints[chip.tone] : undefined;
        return (
          <Chip
            key={chip.key}
            label={`${chip.count} ${chip.label}`}
            size="small"
            clickable={Boolean(chip.onClick)}
            onClick={chip.onClick}
            variant={chip.active ? 'filled' : 'outlined'}
            sx={{
              fontWeight: 600,
              ...(tint
                ? chip.active
                  ? { bgcolor: tint.fg, color: '#ffffff', '&:hover': { bgcolor: tint.fg } }
                  : { color: `color-mix(in srgb, ${tint.fg} 80%, var(--foreground))`, borderColor: tint.fg }
                : chip.active
                  ? { bgcolor: 'primary.main', color: 'primary.contrastText', '&:hover': { bgcolor: 'primary.main' } }
                  : {}),
            }}
          />
        );
      })}
    </div>
  );
}
