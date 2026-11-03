import { useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import {
  generateCohortSummary,
  generateParticipantSummary,
  getCohortSummary,
  getParticipantSummary,
  rateSummary,
} from '../../api/aiCoachApi';
import { useI18n } from '../../i18n/I18nContext';
import { formatDate } from '../../i18n/formatDate';
import { tr } from '../../i18n/translate';
import type { AiCohortSummary, AiParticipantSummary } from '../../types/vision';
import { Button } from '../common/Button';

function Section({ title, text }: { title: string; text?: string | null }) {
  if (!text) {
    return null;
  }
  return (
    <Box>
      <Typography variant="subtitle2">{title}</Typography>
      <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{text}</Typography>
    </Box>
  );
}

/** AC-16: was this summary accurate? */
function AccuracyRating({ token, summaryId, initial }: { token: string; summaryId: number; initial?: boolean | null }) {
  const [accurate, setAccurate] = useState<boolean | null>(initial ?? null);
  async function rate(value: boolean) {
    await rateSummary(token, summaryId, value);
    setAccurate(value);
  }
  return (
    <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
      <Typography variant="caption" color="text.secondary">{tr('Was this accurate?')}</Typography>
      <Chip size="small" label={tr('Yes')} color={accurate === true ? 'primary' : 'default'} onClick={() => void rate(true)} />
      <Chip size="small" label={tr('No')} color={accurate === false ? 'primary' : 'default'} onClick={() => void rate(false)} />
    </Box>
  );
}

function useWeeklySummary<T>(load: () => Promise<T | undefined>, deps: unknown[]) {
  const [summary, setSummary] = useState<T | null>(null);
  const [loaded, setLoaded] = useState(false);
  useEffect(() => {
    load().then((found) => setSummary(found ?? null)).catch(() => setSummary(null)).finally(() => setLoaded(true));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  return { summary, setSummary, loaded };
}

/**
 * V9 (AI-FR-18, AI-FR-19): this week's one-page summary of a participant for
 * their coach, with the risk signals and their reasons. Kept for the week;
 * the coach makes it, and can remake it, on purpose.
 */
export function ParticipantSummaryCard({ token, userId, aiConsent }: { token: string; userId: number; aiConsent: boolean }) {
  const { language } = useI18n();
  const { summary, setSummary, loaded } = useWeeklySummary<AiParticipantSummary>(() => getParticipantSummary(token, userId), [token, userId]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function generate() {
    setBusy(true);
    setError('');
    try {
      setSummary(await generateParticipantSummary(token, userId, language));
    } catch (generateError) {
      setError(generateError instanceof Error ? tr(generateError.message) : tr('AI help is temporarily unavailable.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card>
      <CardHeader title={tr('Weekly AI summary')} subheader={tr('Progress, mood, risks and one suggested action, from what you may see of this participant.')} />
      <CardContent>
        <Stack spacing={1.5}>
          {!aiConsent && (
            <Typography variant="body2" color="text.secondary">
              {tr('This participant has not turned on AI consent, so AI cannot read their plan.')}
            </Typography>
          )}
          {aiConsent && loaded && (
            <Box>
              <Button variant={summary ? 'secondary' : 'primary'} disabled={busy} onClick={() => void generate()}>
                {busy ? tr('The Dream Coach is thinking…') : summary ? tr('Make it again') : tr('Make this week\'s summary')}
              </Button>
            </Box>
          )}
          {error && <Alert severity="error">{error}</Alert>}
          {summary && (
            <Stack spacing={1.25}>
              <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                <Chip size="small" label={tr('AI suggestion')} />
                <Typography variant="caption" color="text.secondary">
                  {tr('Made {date}', { date: formatDate(new Date(summary.generatedAt)) })}
                </Typography>
              </Box>
              <Section title={tr('Progress')} text={summary.progress} />
              <Section title={tr('Mood')} text={summary.mood ?? tr('Journals are not shared, so there is no mood trend.')} />
              <Section title={tr('Risks')} text={summary.risks} />
              {summary.riskSignals.length > 0 && (
                <Box>
                  <Typography variant="subtitle2">{tr('Risk signals')}</Typography>
                  {summary.riskSignals.map((reason) => <Typography key={reason} variant="body2">• {tr(reason)}</Typography>)}
                </Box>
              )}
              <Section title={tr('Suggested action')} text={summary.suggestedAction} />
              <AccuracyRating token={token} summaryId={summary.id} initial={summary.accurate} />
            </Stack>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}

/** V9 (AI-FR-21): this week's patterns across a cohort, from participants who gave AI consent. */
export function CohortSummaryCard({ token, cohortId, cohortName }: { token: string; cohortId: number; cohortName: string }) {
  const { language } = useI18n();
  const { summary, setSummary, loaded } = useWeeklySummary<AiCohortSummary>(() => getCohortSummary(token, cohortId), [token, cohortId]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function generate() {
    setBusy(true);
    setError('');
    try {
      setSummary(await generateCohortSummary(token, cohortId, language));
    } catch (generateError) {
      setError(generateError instanceof Error ? tr(generateError.message) : tr('AI help is temporarily unavailable.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card variant="outlined">
      <CardHeader title={tr('{name}: this week', { name: cohortName })} subheader={tr('Common blockers and modules where people are stuck.')} />
      <CardContent>
        <Stack spacing={1.25}>
          {loaded && (
            <Box>
              <Button variant="secondary" disabled={busy} onClick={() => void generate()}>
                {busy ? tr('The Dream Coach is thinking…') : summary ? tr('Make it again') : tr('Make the cohort summary')}
              </Button>
            </Box>
          )}
          {error && <Alert severity="error">{error}</Alert>}
          {summary && (
            <>
              <Chip size="small" label={tr('AI suggestion')} sx={{ alignSelf: 'flex-start' }} />
              <Section title={tr('Overview')} text={summary.overview} />
              <Section title={tr('Common blockers')} text={summary.commonBlockers} />
              <Section title={tr('Modules where people are stuck')} text={summary.strugglingModules} />
              <Section title={tr('Suggested actions')} text={summary.suggestedActions} />
              <AccuracyRating token={token} summaryId={summary.id} initial={summary.accurate} />
            </>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}
