import { useEffect, useState } from 'react';
import { aiOnFor } from '../ai/aiFocus';
import { useNavigate } from 'react-router';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { getAiStatus } from '../../api/aiApi';
import { getAssessmentPractice, getWhatChanged } from '../../api/aiModuleApi';
import { useI18n } from '../../i18n/I18nContext';
import { tr } from '../../i18n/translate';
import type { ChainAssessment, ChainScoreKey, WhatChanged } from '../../types/vision';
import { PRACTICE_ACTIONS, PRACTICE_PROGRAM } from '../../utils/assessmentContent';
import { Button } from '../common/Button';
import { SuggestionRating } from '../ai/SuggestionRating';

const CHAIN_LABELS: Record<ChainScoreKey, string> = {
  settlingScore: 'Settling for average',
  fearScore: 'Fear of failure',
  criticismScore: 'Avoiding criticism',
  visionScore: 'Unclear vision',
  knowledgeScore: 'Not knowing how',
  resourcesScore: 'Lack of resources',
};

type Practice = { chainKey: ChainScoreKey; score: number; practice: string; habit: string };

/**
 * V15 (AH-FR-26, AH-FR-27): one small practice for the next seven days for
 * each barrier scored 3 or less, tied to an app action. With AI the practice
 * is worded for the participant; without AI it comes from the program's list.
 * Do it now opens the page; Add as a habit opens the Today page's habit box,
 * filled. Nothing is saved here.
 */
export function PracticeCard({ token, latest }: Readonly<{ token: string; latest: ChainAssessment }>) {
  const { language } = useI18n();
  const navigate = useNavigate();
  const [aiOn, setAiOn] = useState(false);
  const [practices, setPractices] = useState<Practice[] | null>(null);
  const [requestId, setRequestId] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    getAiStatus(token).then((status) => setAiOn(aiOnFor(status, 'ASSESSMENT'))).catch(() => setAiOn(false));
  }, [token]);

  const weak = (Object.keys(CHAIN_LABELS) as ChainScoreKey[]).filter((key) => latest[key] <= 3);
  if (weak.length === 0) {
    return null;
  }
  const fromProgram = (): Practice[] => weak.map((key) => ({ chainKey: key, score: latest[key], ...PRACTICE_PROGRAM[key] }));

  async function load() {
    setBusy(true);
    setError('');
    try {
      if (aiOn) {
        try {
          const answer = await getAssessmentPractice(token, language);
          // A barrier Claude left out still gets the program's practice.
          setPractices(weak.map((key) => {
            const found = answer.practices.find((item) => item.chainKey === key);
            return found ? { chainKey: key, score: found.score, practice: found.practice, habit: found.habit || PRACTICE_PROGRAM[key].habit }
              : { chainKey: key, score: latest[key], ...PRACTICE_PROGRAM[key] };
          }));
          setRequestId(answer.requestId);
          return;
        } catch {
          // Fall back to the program's list below.
        }
      }
      setPractices(fromProgram());
      setRequestId(null);
    } catch (loadError) {
      setError(loadError instanceof Error ? tr(loadError.message) : tr('Unable to load practices.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card component="section" aria-label={tr('Practise this week')}>
      <CardHeader title={tr('Practise this week')} subheader={tr('One small practice for each barrier you scored 3 or less.')} />
      <CardContent>
        {!practices && (
          <Button variant="secondary" disabled={busy} onClick={() => void load()}>
            {busy ? tr('The Dream Coach is thinking…') : tr('What should I practise this week?')}
          </Button>
        )}
        {error && <Alert severity="error" sx={{ mt: 1 }}>{error}</Alert>}
        {practices && (
          <Stack spacing={1.5} aria-live="polite">
            {practices.map((item) => {
              const action = PRACTICE_ACTIONS[item.chainKey];
              return (
                <Card key={item.chainKey} variant="outlined" component="section" aria-label={tr(CHAIN_LABELS[item.chainKey])}>
                  <CardContent sx={{ display: 'grid', gap: 1 }}>
                    <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                      {requestId && <Chip size="small" label={tr('AI suggestion')} />}
                      <Typography variant="subtitle2" component="h4">
                        {tr('{label}: {score} of 5', { label: tr(CHAIN_LABELS[item.chainKey]), score: item.score })}
                      </Typography>
                    </Box>
                    <Typography variant="body2">{requestId ? item.practice : tr(item.practice)}</Typography>
                    <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                      <Button onClick={() => navigate(action.path)}>{tr('Do it now')}</Button>
                      <Button variant="secondary" onClick={() => navigate(`/today?habit=${encodeURIComponent(requestId ? item.habit : tr(item.habit))}`)}>
                        {tr('Add as a habit')}
                      </Button>
                    </Box>
                  </CardContent>
                </Card>
              );
            })}
            <SuggestionRating token={token} requestId={requestId} />
          </Stack>
        )}
      </CardContent>
    </Card>
  );
}

/** V15 (AH-FR-29): after a retake, a short comparison with the previous take. AI only. */
export function WhatChangedCard({ token }: Readonly<{ token: string }>) {
  const { language } = useI18n();
  const [aiOn, setAiOn] = useState(false);
  const [answer, setAnswer] = useState<WhatChanged | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    getAiStatus(token).then((status) => setAiOn(aiOnFor(status, 'ASSESSMENT'))).catch(() => setAiOn(false));
  }, [token]);

  if (!aiOn) {
    return null;
  }

  async function ask() {
    setBusy(true);
    setError('');
    try {
      setAnswer(await getWhatChanged(token, language));
    } catch (askError) {
      setError(askError instanceof Error ? tr(askError.message) : tr('AI help is temporarily unavailable.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Box component="section" aria-label={tr('What changed?')}>
      <Button variant="secondary" disabled={busy} onClick={() => void ask()}>
        {busy ? tr('The Dream Coach is thinking…') : tr('What changed since last time?')}
      </Button>
      {error && <Alert severity="error" sx={{ mt: 1 }}>{error}</Alert>}
      {answer && (
        <Box aria-live="polite" sx={{ mt: 1, display: 'grid', gap: 1 }}>
          <Chip size="small" label={tr('AI suggestion')} sx={{ justifySelf: 'start' }} />
          <Typography variant="body2">{answer.summary}</Typography>
          {answer.modulesBetween.length > 0 && (
            <Typography variant="caption" color="text.secondary">
              {tr('Modules completed in between: {modules}', { modules: answer.modulesBetween.join(', ') })}
            </Typography>
          )}
          <SuggestionRating token={token} requestId={answer.requestId} />
        </Box>
      )}
    </Box>
  );
}
