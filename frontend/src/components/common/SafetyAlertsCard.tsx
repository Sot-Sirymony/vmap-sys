import { useCallback, useEffect, useState } from 'react';
import { Link as RouterLink } from 'react-router';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { acknowledgeAiSafetyAlert, listAiSafetyAlerts } from '../../api/aiApi';
import { formatDate } from '../../i18n/formatDate';
import { tr } from '../../i18n/translate';
import type { AiSafetyAlert } from '../../types/vision';
import { Button } from './Button';

/**
 * V9 (AI-NFR-09, D-5): participants whose Dream Coach message looked like a
 * crisis. Shows who and when, never what they wrote. Hidden when there are none.
 */
export function SafetyAlertsCard({ token }: { token: string | null }) {
  const [alerts, setAlerts] = useState<AiSafetyAlert[]>([]);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    if (!token) {
      return;
    }
    try {
      setAlerts(await listAiSafetyAlerts(token));
    } catch {
      setAlerts([]);
    }
  }, [token]);

  useEffect(() => {
    void load();
  }, [load]);

  async function handleAcknowledge(id: number) {
    if (!token) {
      return;
    }
    setError('');
    try {
      await acknowledgeAiSafetyAlert(token, id);
      await load();
    } catch (ackError) {
      setError(ackError instanceof Error ? tr(ackError.message) : tr('Unable to save this.'));
    }
  }

  const open = alerts.filter((alert) => !alert.acknowledgedAt);
  if (alerts.length === 0) {
    return null;
  }

  return (
    <Card sx={{ borderLeft: 4, borderColor: open.length > 0 ? 'error.main' : 'divider' }}>
      <CardHeader
        title={tr('Wellbeing alerts')}
        subheader={tr('Someone wrote something in the Dream Coach that may mean they are in crisis. They were shown help lines. Please check in with them soon. The message itself stays private.')}
      />
      <CardContent>
        <Stack spacing={1}>
          {error && <Alert severity="error">{error}</Alert>}
          {alerts.slice(0, 10).map((alert) => (
            <Box key={alert.id} sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
              <Link component={RouterLink} to={`/coach/participants/${alert.participantId}`} sx={{ fontWeight: 600 }}>
                {alert.participantName}
              </Link>
              <Typography variant="body2" color="text.secondary">
                {alert.cohortName ? `${alert.cohortName} · ` : ''}{formatDate(new Date(alert.createdAt), { weekday: true })}
              </Typography>
              {alert.acknowledgedAt
                ? <Typography variant="caption" color="text.secondary">{tr('Checked in')}</Typography>
                : <Button variant="secondary" onClick={() => void handleAcknowledge(alert.id)}>{tr('I have checked in')}</Button>}
            </Box>
          ))}
        </Stack>
      </CardContent>
    </Card>
  );
}
