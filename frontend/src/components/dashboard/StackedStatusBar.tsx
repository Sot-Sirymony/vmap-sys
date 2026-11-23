import Box from '@mui/material/Box';
import { keyframes } from '@mui/material/styles';
import { familyOf, STATUS_ICONS, statusLabel, useStatusFill } from '../common/StatusChip';
import { useReducedMotion } from '../../hooks/useReducedMotion';
import { formatNumber } from '../../utils/format';
import { tabular } from './surface';

const grow = keyframes`
  from { transform: scaleX(0); }
  to { transform: scaleX(1); }
`;

/** BRD 25 §7.2 — Completed last, so the bar fills towards green. */
export const WORK_STATUS_ORDER = ['NOT_STARTED', 'IN_PROGRESS', 'WAITING', 'BLOCKED', 'PAUSED', 'COMPLETED'] as const;

type StackedStatusBarProps = {
  /** "Goals", "Tasks". */
  label: string;
  counts: Partial<Record<string, number>>;
  order?: readonly string[];
  /** The per-status counts under the bar (default on). */
  showCounts?: boolean;
};

/**
 * BRD 25 §7.2 — a part-to-whole for one entity as a single 100% stacked bar
 * (length reads better than a donut's angles). Paused is striped so it never
 * merges with Not started. Counts sit under the bar with each status's icon,
 * rather than on the segments, so no count depends on text-on-fill contrast.
 */
export function StackedStatusBar({ label, counts, order = WORK_STATUS_ORDER, showCounts = true }: StackedStatusBarProps) {
  const reduced = useReducedMotion();
  const fill = useStatusFill();
  const present = order.filter((status) => (counts[status] ?? 0) > 0);
  const total = present.reduce((sum, status) => sum + (counts[status] ?? 0), 0);
  const summary = `${label}: ${formatNumber(total)}. ${order.map((status) => `${statusLabel(status)} ${formatNumber(counts[status] ?? 0)}`).join(', ')}`;

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.75, fontSize: '0.8125rem' }}>
        <span>{label}</span>
        <Box component="b" sx={{ ...tabular, fontWeight: 600 }}>{formatNumber(total)}</Box>
      </Box>
      <Box
        role="img"
        aria-label={summary}
        sx={{ display: 'flex', gap: '2px', height: 14, borderRadius: 'var(--radius-pill)', overflow: 'hidden', bgcolor: total === 0 ? 'var(--surface-sunken)' : 'transparent' }}
      >
        {present.map((status, index) => {
          const color = fill(status);
          const held = familyOf(status) === 'held';
          return (
            <Box
              key={status}
              data-status={status}
              sx={{
                flex: `${counts[status]} 0 0`,
                minWidth: 3,
                bgcolor: color,
                backgroundImage: held ? `repeating-linear-gradient(135deg, transparent 0 3px, color-mix(in srgb, var(--surface) 60%, transparent) 3px 6px)` : 'none',
                transformOrigin: 'left',
                animation: reduced ? 'none' : `${grow} 600ms var(--motion-ease) both`,
                animationDelay: `${index * 40}ms`,
              }}
            />
          );
        })}
      </Box>
      {showCounts && present.length > 0 && (
        <Box component="ul" sx={{ listStyle: 'none', m: 0, mt: 1, p: 0, display: 'flex', flexWrap: 'wrap', columnGap: 1.5, rowGap: 0.5, fontSize: 'var(--font-caption)', color: 'var(--text-secondary)' }}>
          {present.map((status) => {
            const Icon = STATUS_ICONS[familyOf(status)];
            return (
              <Box component="li" key={status} sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5 }}>
                <Box component="span" sx={{ display: 'inline-flex', color: fill(status) }}>
                  <Icon size={12} strokeWidth={2} aria-hidden="true" />
                </Box>
                <Box component="span" sx={{ ...tabular, color: 'var(--text-primary)', fontWeight: 600 }}>{formatNumber(counts[status] ?? 0)}</Box>
                {statusLabel(status)}
              </Box>
            );
          })}
        </Box>
      )}
    </Box>
  );
}
