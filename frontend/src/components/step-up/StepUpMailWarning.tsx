import { useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import { getStepUpStatus } from '../../api/stepUpApi';
import { tr } from '../../i18n/translate';
import type { StepUpStatus } from '../../types/vision';

/**
 * V20 (SV-FR-34): on the admin pages, a warning while "Verify it's you" is on
 * but no code can be emailed, so risky actions are protected by passwords only
 * (D-20-4). Shows nothing otherwise, or if the status cannot be read.
 */
export function StepUpMailWarning({ token }: Readonly<{ token: string | null }>) {
  const [status, setStatus] = useState<StepUpStatus | null>(null);

  useEffect(() => {
    if (!token) return undefined;
    let active = true;
    getStepUpStatus(token).then((next) => active && setStatus(next)).catch(() => undefined);
    return () => {
      active = false;
    };
  }, [token]);

  if (!status?.enabled || status.delivery !== 'NONE') return null;
  return (
    <Alert severity="warning" sx={{ mb: 2 }}>
      {tr('Verification codes cannot be sent. Set up mail (MAIL_HOST) so risky changes are confirmed by email.')}
    </Alert>
  );
}
