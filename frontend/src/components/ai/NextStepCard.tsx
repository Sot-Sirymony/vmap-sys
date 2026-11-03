import { useEffect, useState } from 'react';
import { Link as RouterLink } from 'react-router';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Chip from '@mui/material/Chip';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';
import { getAiStatus } from '../../api/aiApi';
import { suggestNextStep } from '../../api/aiDailyApi';
import { useI18n } from '../../i18n/I18nContext';
import { tr } from '../../i18n/translate';
import type { NextStep } from '../../types/vision';
import { Button } from '../common/Button';

const TIMES = [15, 30, 60, 120];

/**
 * V9 (AI-FR-12): the participant says how much time they have and gets the
 * best next step from their own plan, with up to two alternatives. Only a
 * suggestion; it opens the item, it never changes it. Hidden while AI is off.
 */
export function NextStepCard({ token }: { token: string | null }) {
  const { language } = useI18n();
  const [visible, setVisible] = useState(false);
  const [minutes, setMinutes] = useState(30);
  const [answer, setAnswer] = useState<NextStep | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!token) {
      return;
    }
    getAiStatus(token).then((status) => setVisible(status.reason !== 'OFF')).catch(() => setVisible(false));
  }, [token]);

  if (!token || !visible) {
    return null;
  }

  async function ask() {
    if (!token) {
      return;
    }
    setBusy(true);
    setError('');
    setAnswer(null);
    try {
      setAnswer(await suggestNextStep(token, minutes, language));
    } catch (askError) {
      setError(askError instanceof Error ? tr(askError.message) : tr('AI help is temporarily unavailable.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card>
      <CardHeader title={tr('What should I do now?')} subheader={tr('Say how much time you have, and get the best next step from your plan.')} />
      <CardContent>
        <Stack spacing={1.5}>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
            <ToggleButtonGroup
              size="small"
              exclusive
              value={minutes}
              onChange={(_event, value: number | null) => value && setMinutes(value)}
              aria-label={tr('Time I have')}
            >
              {TIMES.map((time) => (
                <ToggleButton key={time} value={time}>{time < 60 ? tr('{count} min', { count: time }) : tr('{count} h', { count: time / 60 })}</ToggleButton>
              ))}
            </ToggleButtonGroup>
            <Button disabled={busy} onClick={() => void ask()}>{busy ? tr('The Dream Coach is thinking…') : tr('Suggest')}</Button>
          </Box>
          {error && <Alert severity="error">{error}</Alert>}
          {answer && answer.picks.length === 0 && answer.note && <Alert severity="info">{tr(answer.note)}</Alert>}
          {answer && answer.picks.map((pick, index) => (
            <Box key={`${pick.itemType}-${pick.itemId}`} sx={{ borderLeft: 3, borderColor: index === 0 ? 'primary.main' : 'divider', pl: 1.5 }}>
              <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                {index === 0 && <Chip size="small" color="primary" label={tr('Best next step')} />}
                <Chip size="small" variant="outlined" label={pick.itemType === 'TASK' ? tr('Task') : tr('Step')} />
                <Link component={RouterLink} to={pick.itemType === 'TASK' ? '/tasks' : '/steps'} sx={{ fontWeight: 600 }}>{pick.title}</Link>
                {pick.minutes && <Typography variant="caption" color="text.secondary">{tr('about {count} min', { count: pick.minutes })}</Typography>}
              </Box>
              {pick.reason && <Typography variant="body2" color="text.secondary">{pick.reason}</Typography>}
            </Box>
          ))}
          {answer && answer.picks.length > 0 && (
            <Typography variant="caption" color="text.secondary">
              {tr('AI suggestion')}{answer.note ? ` · ${answer.note}` : ''}
            </Typography>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}
