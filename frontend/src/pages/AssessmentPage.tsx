import { FormEvent, useCallback, useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Stack from '@mui/material/Stack';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';
import { useNavigate } from 'react-router';
import { getChainAssessments, saveChainAssessment } from '../api/chainAssessmentApi';
import { Button } from '../components/common/Button';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Loading } from '../components/common/Loading';
import { useAuth } from '../context/AuthContext';
import { AssessmentExplainCard } from '../components/ai/AssessmentExplainCard';
import type { ChainAssessmentSummary, ChainScoreKey } from '../types/vision';
import { localDateIso } from '../utils/localDate';
import { useI18n } from '../i18n/I18nContext';
import { PageSection } from './PageSection';
import { HelpWithThis, PageAssistantProvider, PageHelpButton } from '../components/ai/page-assistant/PageAssistant';
import { PracticeCard, WhatChangedCard } from '../components/assessment/AssessmentHelpers';
import { MyInnerBlockersCard } from '../components/inner-blockers/MyInnerBlockersCard';
import { STATEMENT_GUIDE } from '../utils/assessmentContent';
import { CHAINS, chainScore, fearLinkSuggested, retiredChainsIn } from '../utils/chains';

const SCORES = [1, 2, 3, 4, 5];


/** FR-71: the six-chain self-assessment, its retake, and progress from the baseline. */
export function AssessmentPage() {
  const { t } = useI18n();
  const { token } = useAuth();
  const navigate = useNavigate();
  const [summary, setSummary] = useState<ChainAssessmentSummary | null>(null);
  const [scores, setScores] = useState<Partial<Record<ChainScoreKey, number>>>({});
  const [loadError, setLoadError] = useState('');
  const [saveError, setSaveError] = useState('');
  const [saving, setSaving] = useState(false);
  // V15 (AH-FR-23): which statements have their plain explanation open.
  const [explained, setExplained] = useState<Partial<Record<ChainScoreKey, boolean>>>({});

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
      // V17 BL-FR-30: the six current chains, all answered (complete is checked above).
      await saveChainAssessment(token, {
        takenOn: localDateIso(),
        settlingScore: scores.settlingScore as number,
        fearScore: scores.fearScore as number,
        criticismScore: scores.criticismScore as number,
        prioritiesScore: scores.prioritiesScore as number,
        persistenceScore: scores.persistenceScore as number,
        visionScore: scores.visionScore as number,
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
  // V17 BL-FR-31: a chain is compared from the first take that measured it; retired chains are labelled.
  const firstMeasured = (key: ChainScoreKey) => assessments.find((take) => chainScore(take, key) !== null);
  const retired = retiredChainsIn(assessments);

  return (
    // V15 (AH-FR-01): the page assistant on the Assessment page. It never suggests a score (AH-FR-24).
    <PageAssistantProvider page="ASSESSMENT">
    <PageSection
      title={t('Assessment')}
      subtitle={t('See how much each of six common barriers holds you back')}
      actions={(
        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
          <PageHelpButton />
          <Button variant="secondary" onClick={() => navigate('/strengths')}>{t('Strengths and Gaps')}</Button>
        </Box>
      )}
    >
      {loadError && <ErrorMessage message={loadError} onRetry={() => void load()} />}
      {!summary && !loadError && <Loading />}
      {summary && (
        <Stack spacing={2}>
          {summary.retakeAfterModule && !summary.retakeSuggested && (
            // V15 (AH-FR-28): a rule, no AI.
            <Alert severity="info">
              {t('It has been 4 weeks or more since your last assessment, and you have finished a module since. Take it again to see what has changed.')}
            </Alert>
          )}

          {summary.retakeSuggested && (
            <Alert
              severity="info"
              action={<Button variant="secondary" onClick={() => navigate('/strengths')}>{t('Strengths and Gaps')}</Button>}
            >
              {t('It has been 12 weeks or more since your last assessment. Take it again to see what has changed.')}
              {' '}{t('Look again at your strengths and gaps too.')}
            </Alert>
          )}

          {baseline && (
            <Card>
              <CardHeader
                action={<HelpWithThis area="result" label={t('your result')} />}
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
                        <td>{chainScore(firstMeasured(chain.key), chain.key) ?? '—'}</td>
                        <td>{chainScore(latest, chain.key) ?? t('Not measured yet')}</td>
                      </tr>
                    ))}
                    {retired.map((chain) => (
                      <tr key={chain.key} className="chain-retired">
                        <td>{t(chain.label)}</td>
                        <td>{chainScore(assessments.find((take) => chainScore(take, chain.key) !== null), chain.key)}</td>
                        <td>{t('No longer measured (moved to step gaps)')}</td>
                      </tr>
                    ))}
                  </tbody>
                </Box>
                {fearLinkSuggested(latest) && (
                  // V17 BL-FR-34: a low fear or criticism score links to the fear journal.
                  <Alert severity="info" sx={{ mt: 2 }}
                    action={<Button variant="secondary" onClick={() => navigate('/journal?newFear=1')}>{t('Write it down in the fear journal')}</Button>}>
                    {t('Fear of failure or criticism is holding you back right now. Writing the fear down and planning one small test makes it smaller.')}
                  </Alert>
                )}
              </CardContent>
            </Card>
          )}

          {baseline && token && <AssessmentExplainCard token={token} />}
          {latest && token && <PracticeCard token={token} latest={latest} />}
          {assessments.length > 1 && token && <WhatChangedCard token={token} />}
          {token && <MyInnerBlockersCard token={token} />}

          <Card>
            <CardHeader
              title={baseline ? t('Take it again') : t('Take your baseline')}
              subheader={t('1 means this is rarely true for you, 5 means it is almost always true. Saving twice in one day replaces that day.')}
            />
            <CardContent>
              <Box component="form" onSubmit={(event) => void handleSubmit(event)} sx={{ display: 'grid', gap: 2 }}>
                {CHAINS.map((chain) => (
                  <Box key={chain.key}>
                    <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1, flexWrap: 'wrap' }}>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>{t(chain.label)}</Typography>
                      <HelpWithThis area={`statement:${chain.key}`} title={t(chain.label)} />
                    </Box>
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 0.5 }}>{t(chain.question)}</Typography>
                    <Link component="button" type="button" variant="body2" aria-expanded={Boolean(explained[chain.key])}
                      onClick={() => setExplained((current) => ({ ...current, [chain.key]: !current[chain.key] }))} sx={{ mb: 0.5 }}>
                      {t('What does this mean?')}
                    </Link>
                    {explained[chain.key] && (
                      <Box sx={{ p: 1, mb: 1, borderRadius: 'var(--style-radius-control)', bgcolor: 'action.hover' }}>
                        <Typography variant="body2">{t(STATEMENT_GUIDE[chain.key].meaning)}</Typography>
                        <Typography variant="body2" color="text.secondary">{t(STATEMENT_GUIDE[chain.key].low)}</Typography>
                        <Typography variant="body2" color="text.secondary">{t(STATEMENT_GUIDE[chain.key].high)}</Typography>
                        <Typography variant="caption" color="text.secondary">{t('The score is your own judgement.')}</Typography>
                      </Box>
                    )}
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
    </PageAssistantProvider>
  );
}
