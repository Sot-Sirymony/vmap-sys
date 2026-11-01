import { FormEvent, useCallback, useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Stack from '@mui/material/Stack';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';
import { getChainAssessments, saveChainAssessment } from '../api/chainAssessmentApi';
import { Button } from '../components/common/Button';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Loading } from '../components/common/Loading';
import { useAuth } from '../context/AuthContext';
import type { ChainAssessmentSummary, ChainScoreKey } from '../types/vision';
import { localDateIso } from '../utils/localDate';
import { useI18n } from '../i18n/I18nContext';
import { PageSection } from './PageSection';

const SCORES = [1, 2, 3, 4, 5];

const CHAINS: { key: ChainScoreKey; label: string; question: string }[] = [
  { key: 'settlingScore', label: 'Settling for average', question: 'I expect more of myself than what is merely comfortable.' },
  { key: 'fearScore', label: 'Fear of failure', question: 'I try things even when they might not work.' },
  { key: 'criticismScore', label: 'Avoiding criticism', question: 'What others may say does not stop me from acting.' },
  { key: 'visionScore', label: 'Unclear vision', question: 'I can describe exactly what I want and when I want it.' },
  { key: 'knowledgeScore', label: 'Not knowing how', question: 'When I do not know how, I find out instead of stopping.' },
  { key: 'resourcesScore', label: 'Lack of resources', question: 'When I lack something, I look for who could supply it.' },
];

/** FR-71: the six-chain self-assessment, its retake, and progress from the baseline. */
export function AssessmentPage() {
  const { t } = useI18n();
  const { token } = useAuth();
  const [summary, setSummary] = useState<ChainAssessmentSummary | null>(null);
  const [scores, setScores] = useState<Partial<Record<ChainScoreKey, number>>>({});
  const [loadError, setLoadError] = useState('');
  const [saveError, setSaveError] = useState('');
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    if (!token) {
      return;
    }
    setLoadError('');
    try {
      setSummary(await getChainAssessments(token));
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : t('Unable to load your assessment.'));
    }
  }, [token]);

  useEffect(() => {
    void load();
  }, [load]);

  const complete = CHAINS.every((chain) => scores[chain.key] !== undefined);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!token || !complete) {
      return;
    }
    setSaving(true);
    setSaveError('');
    try {
      await saveChainAssessment(token, {
        takenOn: localDateIso(),
        settlingScore: scores.settlingScore as number,
        fearScore: scores.fearScore as number,
        criticismScore: scores.criticismScore as number,
        visionScore: scores.visionScore as number,
        knowledgeScore: scores.knowledgeScore as number,
        resourcesScore: scores.resourcesScore as number,
      });
      setScores({});
      await load();
    } catch (error) {
      setSaveError(error instanceof Error ? error.message : t('Unable to save your assessment.'));
    } finally {
      setSaving(false);
    }
  }

  const assessments = summary?.assessments ?? [];
  const baseline = assessments[0];
  const latest = assessments[assessments.length - 1];

  return (
    <PageSection title={t('Assessment')} subtitle={t('See how much each of six common barriers holds you back')}>
      {loadError && <ErrorMessage message={loadError} onRetry={() => void load()} />}
      {!summary && !loadError && <Loading />}
      {summary && (
        <Stack spacing={2}>
          {summary.retakeSuggested && (
            <Alert severity="info">{t('It has been 12 weeks or more since your last assessment. Take it again to see what has changed.')}</Alert>
          )}

          {baseline && (
            <Card>
              <CardHeader
                title={t('Your results')}
                subheader={summary.improvementPercent === null || summary.improvementPercent === undefined
                  ? t('Baseline taken {date}, averaging {score} of 5', { date: baseline.takenOn, score: baseline.averageScore })
                  : t('{percent}% since your baseline on {date}', { percent: `${summary.improvementPercent > 0 ? '+' : ''}${summary.improvementPercent}`, date: baseline.takenOn })}
              />
              <CardContent>
                <Box component="table" sx={{ width: '100%', borderCollapse: 'collapse', '& th, & td': { textAlign: 'left', py: 0.5 } }}>
                  <thead>
                    <tr>
                      <th scope="col">{t('Barrier')}</th>
                      <th scope="col">{t('Baseline')}</th>
                      <th scope="col">{t('Latest')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {CHAINS.map((chain) => (
                      <tr key={chain.key}>
                        <td>{t(chain.label)}</td>
                        <td>{baseline[chain.key]}</td>
                        <td>{latest[chain.key]}</td>
                      </tr>
                    ))}
                  </tbody>
                </Box>
              </CardContent>
            </Card>
          )}

          <Card>
            <CardHeader
              title={baseline ? t('Take it again') : t('Take your baseline')}
              subheader={t('1 means this is rarely true for you, 5 means it is almost always true. Saving twice in one day replaces that day.')}
            />
            <CardContent>
              <Box component="form" onSubmit={(event) => void handleSubmit(event)} sx={{ display: 'grid', gap: 2 }}>
                {CHAINS.map((chain) => (
                  <Box key={chain.key}>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>{t(chain.label)}</Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 0.5 }}>{t(chain.question)}</Typography>
                    <ToggleButtonGroup
                      exclusive
                      size="small"
                      aria-label={t(chain.label)}
                      value={scores[chain.key] ?? null}
                      onChange={(_event, score: number | null) => {
                        if (score !== null) {
                          setScores((current) => ({ ...current, [chain.key]: score }));
                        }
                      }}
                    >
                      {SCORES.map((score) => (
                        <ToggleButton value={score} key={score} sx={{ minWidth: 44 }}>{score}</ToggleButton>
                      ))}
                    </ToggleButtonGroup>
                  </Box>
                ))}
                {saveError && <Alert severity="error">{saveError}</Alert>}
                <Box>
                  <Button type="submit" disabled={saving || !complete}>{saving ? t('Saving…') : t('Save assessment')}</Button>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Stack>
      )}
    </PageSection>
  );
}
