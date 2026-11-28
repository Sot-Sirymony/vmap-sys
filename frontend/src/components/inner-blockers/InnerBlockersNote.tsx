import { useEffect, useState } from 'react';
import Typography from '@mui/material/Typography';
import { listInnerBlockers } from '../../api/innerBlockerApi';
import { tr } from '../../i18n/translate';
import type { InnerBlocker } from '../../types/vision';

/**
 * V34 (D-34-9): one line in Check and add naming this dream's active inner
 * blockers, and those for all dreams. Information only: it never blocks saving,
 * and a failed load shows nothing.
 */
export function InnerBlockersNote({ token, dreamId }: { token: string; dreamId: number }) {
  const [blockers, setBlockers] = useState<InnerBlocker[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    listInnerBlockers(token, { status: 'ACTIVE', dreamId, includeAllDreams: true })
      .then((list) => !cancelled && setBlockers(list))
      .catch(() => !cancelled && setBlockers(null));
    return () => {
      cancelled = true;
    };
  }, [token, dreamId]);

  if (!blockers || blockers.length === 0) {
    return null;
  }
  return (
    <Typography variant="body2" color="text.secondary">
      {tr('Inner blockers for this dream: {count}', { count: blockers.length })}
      {' — '}
      {blockers.map((blocker) => blocker.blocker).join(', ')}
    </Typography>
  );
}
