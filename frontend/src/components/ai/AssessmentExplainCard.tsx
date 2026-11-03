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
import Typography from '@mui/material/Typography';
import { getAiStatus } from '../../api/aiApi';
import { explainAssessment } from '../../api/aiModuleApi';
import { useI18n } from '../../i18n/I18nContext';
import { tr } from '../../i18n/translate';
import type { AssessmentExplanation } from '../../types/vision';
import { Button } from '../common/Button';

/** V9 (v0.1 FR-02 change): the latest assessment in plain words, and the one module to focus on next. */
export function AssessmentExplainCard({ token }: { token: string }) {
  const { language } = useI18n();
  const [visible, setVisible] = useState(false);
  const [answer, setAnswer] = useState<AssessmentExplanation | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    getAiStatus(token).then((status) => setVisible(status.reason !== 'OFF')).catch(() => setVisible(false));
  }, [token]);

  if (!visible) {
    return null;
  }

  async function ask() {
    setBusy(true);
    setError('');
    try {
      setAnswer(await explainAssessment(token, language));
    } catch (askError) {
      setError(askError instanceof Error ? tr(askError.message) : tr('AI help is temporarily unavailable.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card>
      <CardHeader title={tr('What does my result mean?')} subheader={tr('A plain explanation of your latest scores, and where to focus next.')} />
      <CardContent>
        <Stack spacing={1.5}>
          <Box><Button variant="secondary" disabled={busy} onClick={() => void ask()}>{busy ? tr('The Dream Coach is thinking…') : tr('Explain my result')}</Button></Box>
          {error && <Alert severity="error">{error}</Alert>}
          {answer && (
            <Stack spacing={1}>
              <Chip size="small" label={tr('AI suggestion')} sx={{ alignSelf: 'flex-start' }} />
              {answer.explanation && <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{answer.explanation}</Typography>}
              {answer.focusModule && (
                <Alert severity="info">
                  {tr('Focus next:')}{' '}
                  <Link component={RouterLink} to={`/modules/${answer.focusModule}`}>{answer.focusTitle}</Link>
                  {answer.reason ? ` · ${answer.reason}` : ''}
                </Alert>
              )}
            </Stack>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}
