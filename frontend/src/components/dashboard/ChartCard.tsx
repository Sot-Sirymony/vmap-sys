import { useId, useState, type ReactNode } from 'react';
import Box from '@mui/material/Box';
import Skeleton from '@mui/material/Skeleton';
import { ChartColumn, Table2, TriangleAlert } from 'lucide-react';
import { tr } from '../../i18n/translate';
import { cardSurface } from './surface';

export type ChartCardState = 'ready' | 'loading' | 'empty' | 'error';

type ChartCardProps = {
  title: string;
  caption?: string;
  /** Right-aligned header action, e.g. "View all". */
  action?: ReactNode;
  state?: ChartCardState;
  /** One friendly line for the empty state (BRD 25 §9). */
  emptyMessage?: string;
  /** Shown under the error line, for support. */
  errorCode?: string;
  onRetry?: () => void;
  /** The same data as an accessible table; adds the "View as table" toggle (DA-FR-12). */
  table?: ReactNode;
  children?: ReactNode;
};

/**
 * BRD 25 §11 — the card shell every dashboard chart sits in: title, caption,
 * action, a chart/table toggle, and its own loading, empty and error states, so
 * one failing card never takes the page down (DA-FR-13).
 */
export function ChartCard({ title, caption, action, state = 'ready', emptyMessage, errorCode, onRetry, table, children }: ChartCardProps) {
  const headingId = useId();
  const [asTable, setAsTable] = useState(false);
  const canToggle = Boolean(table) && state === 'ready';

  let content: ReactNode;
  if (state === 'loading') {
    content = (
      <Box aria-hidden="true" sx={{ display: 'grid', gap: 1.5 }}>
        <Skeleton variant="rounded" height={14} width="60%" />
        <Skeleton variant="rounded" height={120} />
      </Box>
    );
  } else if (state === 'error') {
    content = (
      <Box role="alert" sx={{ display: 'flex', gap: 1.5, alignItems: 'flex-start', p: 1.75, borderRadius: 'var(--radius-md)', bgcolor: 'var(--surface-sunken)' }}>
        <Box component="span" sx={{ color: 'var(--st-declined-fg)', display: 'inline-flex', mt: '1px' }}>
          <TriangleAlert size={20} strokeWidth={1.75} aria-hidden="true" />
        </Box>
        <Box sx={{ minWidth: 0 }}>
          <Box>{tr("We couldn't load this part of your dashboard.")}</Box>
          {errorCode && <Box sx={{ fontSize: 'var(--font-caption)', color: 'var(--text-secondary)', mt: 0.25 }}>{errorCode}</Box>}
          {onRetry && (
            <Box
              component="button"
              type="button"
              onClick={onRetry}
              sx={{ mt: 1.25, height: 32, px: 1.5, borderRadius: 'var(--radius-md)', border: '1px solid var(--border-strong)', bgcolor: 'var(--surface)', color: 'inherit', font: 'inherit', fontWeight: 500, cursor: 'pointer' }}
            >
              {tr('Try again')}
            </Box>
          )}
        </Box>
      </Box>
    );
  } else if (state === 'empty') {
    content = <Box sx={{ py: 3, color: 'var(--text-secondary)', textAlign: 'center' }}>{emptyMessage ?? tr('Nothing to show yet.')}</Box>;
  } else {
    content = asTable && table ? <Box sx={{ overflowX: 'auto' }}>{table}</Box> : children;
  }

  return (
    <Box component="section" aria-labelledby={headingId} aria-busy={state === 'loading' || undefined} sx={cardSurface}>
      <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 1.5, mb: 2 }}>
        <Box sx={{ minWidth: 0 }}>
          <Box component="h3" id={headingId} sx={{ m: 0, fontSize: '1rem', lineHeight: 1.5, fontWeight: 600 }}>
            {title}
          </Box>
          {caption && <Box component="p" sx={{ m: 0, mt: 0.25, fontSize: 'var(--font-caption)', color: 'var(--text-secondary)' }}>{caption}</Box>}
        </Box>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flex: 'none' }}>
          {action}
          {canToggle && (
            <Box
              component="button"
              type="button"
              aria-pressed={asTable}
              onClick={() => setAsTable((value) => !value)}
              sx={{
                display: 'inline-flex', alignItems: 'center', gap: 0.5, height: 28, px: 1, borderRadius: 'var(--radius-md)',
                border: '1px solid', borderColor: asTable ? 'var(--border)' : 'transparent', bgcolor: asTable ? 'var(--surface-sunken)' : 'transparent',
                color: 'var(--text-secondary)', font: 'inherit', fontSize: 'var(--font-caption)', cursor: 'pointer',
                '&:hover': { bgcolor: 'var(--surface-sunken)' },
                '&:active': { transform: 'scale(0.98)' },
                transition: 'background var(--motion-fast) var(--motion-ease), transform var(--motion-fast) var(--motion-ease)',
              }}
            >
              {asTable ? <ChartColumn size={16} aria-hidden="true" /> : <Table2 size={16} aria-hidden="true" />}
              {asTable ? tr('View as chart') : tr('View as table')}
            </Box>
          )}
        </Box>
      </Box>
      {content}
    </Box>
  );
}
