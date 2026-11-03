import { useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import FormControlLabel from '@mui/material/FormControlLabel';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import Typography from '@mui/material/Typography';
import { getAiStatus, setAiConsent } from '../../api/aiApi';
import { formatDate } from '../../i18n/formatDate';
import { tr } from '../../i18n/translate';
import type { AiStatus } from '../../types/vision';

/**
 * V9 (AI-NFR-03, D-2): the one AI consent. It covers dreams, plans and
 * journals; without it AI stays off, and it can be withdrawn at any time.
 */
export function AiConsentCard({ token, onChange }: { token: string | null; onChange?: (status: AiStatus) => void }) {
  const [status, setStatus] = useState<AiStatus | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!token) {
      return;
    }
    getAiStatus(token)
      .then(setStatus)
      .catch((loadError: unknown) => setError(loadError instanceof Error ? tr(loadError.message) : tr('Unable to load AI settings.')));
  }, [token]);

  async function handleToggle(consent: boolean) {
    if (!token) {
      return;
    }
    setBusy(true);
    setError('');
    try {
      const next = await setAiConsent(token, consent);
      setStatus(next);
      onChange?.(next);
    } catch (saveError) {
      setError(saveError instanceof Error ? tr(saveError.message) : tr('Unable to save this.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card>
      <CardHeader title={tr('AI help')} subheader={tr('The Dream Coach uses Claude, an AI assistant made by Anthropic.')} />
      <CardContent>
        <Stack spacing={1.5}>
          <Typography variant="body2">
            {tr('If you allow it, the AI may read your dreams, plans and journals (fears, criticism and attitude) to help you. It never saves anything without your action, and your data is not used to train AI. You can turn this off at any time, which stops all AI use of your data.')}
          </Typography>
          {status && (
            <FormControlLabel
              control={<Switch checked={status.consentGiven} disabled={busy} onChange={(event) => void handleToggle(event.target.checked)} />}
              label={status.consentGiven
                ? tr('AI may use my data (since {date})', { date: status.consentAt ? formatDate(new Date(status.consentAt), { year: true }) : '' })
                : tr('Allow AI to use my data')}
            />
          )}
          {error && <Alert severity="error">{error}</Alert>}
        </Stack>
      </CardContent>
    </Card>
  );
}
