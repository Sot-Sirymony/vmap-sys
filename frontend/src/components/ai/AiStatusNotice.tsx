import Alert from '@mui/material/Alert';
import { tr } from '../../i18n/translate';
import type { AiStatus } from '../../types/vision';

/** AI-NFR-02: why the Dream Coach cannot answer right now, in words the participant can act on. */
export function AiStatusNotice({ status }: { status: AiStatus }) {
  if (status.reason === 'OFF' || status.reason === 'BUDGET_REACHED') {
    return <Alert severity="info">{tr('AI help is temporarily unavailable. Everything else in the app works as usual.')}</Alert>;
  }
  if (status.reason === 'DAILY_LIMIT') {
    return <Alert severity="warning">{tr('You have used today\'s Dream Coach messages. They reset tomorrow.')}</Alert>;
  }
  return null;
}
