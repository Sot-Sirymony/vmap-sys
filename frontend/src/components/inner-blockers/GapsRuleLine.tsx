import type { ReactNode } from 'react';
import Alert from '@mui/material/Alert';
import { tr } from '../../i18n/translate';

/** V36 (D-36-7): the one rule that tells a gap from an inner blocker, shown on both pages. */
export const GAPS_RULE = 'A gap is something someone or something can cover. An inner blocker is something you change by practising.';

export function GapsRuleLine({ link }: { link: ReactNode }) {
  return (
    <Alert severity="info" icon={false} sx={{ '& .MuiAlert-message': { display: 'grid', gap: 0.5 } }}>
      <span>{tr(GAPS_RULE)}</span>
      <span>{link}</span>
    </Alert>
  );
}
