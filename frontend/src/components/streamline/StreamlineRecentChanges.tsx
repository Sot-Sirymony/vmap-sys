import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import { listStreamlineBatches } from '../../api/streamlineApi';
import { tr } from '../../i18n/translate';
import type { StreamlineBatchView } from '../../types/streamline';
import { Button } from '../common/Button';

function changeLabel(action: StreamlineBatchView['changes'][number]['action']): string {
  switch (action) {
    case 'DELETE': return tr('deleted');
    case 'MERGE': return tr('merged');
    case 'SIMPLIFY': return tr('simplified');
    case 'REPEAT': return tr('set to repeat');
    default: return tr('updated');
  }
}

function timeOf(value: string): string {
  try {
    return new Date(value).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  } catch {
    return '';
  }
}

/**
 * V32 SF-FR-07: the batches applied on this stage, newest first, each with
 * Undo while it can still be undone (D-32-5). refreshKey changes after a batch
 * or an undo, so the list reloads.
 */
export function StreamlineRecentChanges({ token, reviewId, stage, refreshKey, disabled, onUndo }: {
  token: string;
  reviewId: number;
  stage: number;
  refreshKey: number;
  disabled: boolean;
  onUndo: (batchId: number) => void;
}) {
  const [batches, setBatches] = useState<StreamlineBatchView[]>([]);

  useEffect(() => {
    let cancelled = false;
    listStreamlineBatches(token, reviewId, stage)
      .then((list) => !cancelled && setBatches(list ?? []))
      .catch(() => !cancelled && setBatches([]));
    return () => {
      cancelled = true;
    };
  }, [token, reviewId, stage, refreshKey]);

  if (batches.length === 0) return null;

  return (
    <Box component="section" aria-label={tr('Recent changes')} sx={{ display: 'grid', gap: 0.75 }} data-testid="recent-changes">
      <Typography variant="subtitle2" component="h3">{tr('Recent changes')}</Typography>
      {batches.map((batch) => (
        <Box key={batch.id} sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1, flexWrap: 'wrap' }}>
          <Box sx={{ minWidth: 0 }}>
            <Typography variant="body2">
              {tr('{count} change(s) at {time}', { count: batch.applied, time: timeOf(batch.createdAt) })}
              {batch.undoneAt ? ` · ${tr('Undone')}` : ''}
            </Typography>
            <Typography variant="caption" sx={{ color: 'var(--text-secondary)', overflowWrap: 'anywhere' }}>
              {batch.changes.map((change) => `${change.code} ${changeLabel(change.action)}`).join(', ')}
            </Typography>
          </Box>
          {batch.canUndo && (
            <Button variant="secondary" disabled={disabled} onClick={() => onUndo(batch.id)}
              aria-label={tr('Undo these {count} changes', { count: batch.applied })}>
              {tr('Undo')}
            </Button>
          )}
        </Box>
      ))}
    </Box>
  );
}
