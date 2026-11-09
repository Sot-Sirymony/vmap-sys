import { useEffect, useState } from 'react';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Stack from '@mui/material/Stack';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';
import { getWorkStyleProfile, submitWorkStyleAssessment } from '../api/workStyleProfileApi';
import { Button } from '../components/common/Button';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Loading } from '../components/common/Loading';
import { useAuth } from '../context/AuthContext';
import type { WorkStyleProfile } from '../types/vision';
import { ARCHETYPE_GUIDANCE, QUIZ_ITEMS, scoreAssessment, workStyleArchetypeLabels } from '../utils/workStyleAssessment';
import { PageSection } from './PageSection';
import { currentLanguage, tr } from '../i18n/translate';
import Box from '@mui/material/Box';
import { HelpWithThis, PageAssistantProvider, PageHelpButton } from '../components/ai/page-assistant/PageAssistant';
import { AiUseSwitch, PartnerBalanceCard, StyleResultHelpers } from '../components/workstyle/WorkStyleHelpers';

export function WorkStylePage() {
  const { token } = useAuth();
  const [profile, setProfile] = useState<WorkStyleProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  // FR-49.1: one-time, retakeable — taking the quiz is an explicit choice,
  // not something a returning user with a profile is dropped into.
  const [taking, setTaking] = useState(false);
  const [answers, setAnswers] = useState<('a' | 'b' | null)[]>(() => QUIZ_ITEMS.map(() => null));

  useEffect(() => {
    if (!token) {
      return;
    }
    setLoading(true);
    getWorkStyleProfile(token)
      .then((data) => {
        setProfile(data);
        setTaking(data.dominant === null);
      })
      .catch((loadError) => setError(loadError instanceof Error ? loadError.message : tr('Unable to load your work-style profile.')))
      .finally(() => setLoading(false));
  }, [token]);

  function startAssessment() {
    setAnswers(QUIZ_ITEMS.map(() => null));
    setError('');
    setTaking(true);
  }

  const answeredCount = answers.filter((answer) => answer !== null).length;

  async function handleSubmit() {
    if (!token || answeredCount < QUIZ_ITEMS.length) {
      return;
    }
    setSaving(true);
    setError('');
    try {
      const request = scoreAssessment(answers as ('a' | 'b')[]);
      const result = await submitWorkStyleAssessment(token, request);
      setProfile(result);
      setTaking(false);
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : tr('Unable to save your work-style profile.'));
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return (
      <PageSection title={tr('Work Style')} subtitle={tr('A short self-assessment to inform partner matching.')}>
        <Loading />
      </PageSection>
    );
  }

  return (
    // V15 (AH-FR-68): the page assistant on the Work Style page. It never picks an answer (AH-FR-69).
    <PageAssistantProvider page="WORK_STYLE">
    <PageSection
      title={tr('Work Style')}
      subtitle={tr('A short, retakeable self-assessment (FR-49) — diagnostic only, it never affects any other part of the system.')}
      actions={<PageHelpButton />}
    >
      {error && <ErrorMessage message={error} />}
      {!taking && profile && profile.dominant && (
        <Card sx={{ maxWidth: 640 }}>
          <CardHeader title={tr('Your result')} subheader={tr('Retake at any time — a new result replaces this one.')}
            action={<HelpWithThis area="result" label={tr('your work style')} />} />
          <CardContent>
            <Stack spacing={2}>
              <div>
                <Typography variant="subtitle1">{tr('Dominant: {style}', { style: tr(workStyleArchetypeLabels[profile.dominant]) })}</Typography>
                <Typography variant="body2" color="text.secondary">{tr(ARCHETYPE_GUIDANCE[profile.dominant].strengths)}</Typography>
                <Typography variant="body2" color="text.secondary">{tr('Likely blind spot: {text}', { text: tr(ARCHETYPE_GUIDANCE[profile.dominant].blindSpot) })}</Typography>
                <Typography variant="body2" color="text.secondary">{tr('Working with them: {text}', { text: tr(ARCHETYPE_GUIDANCE[profile.dominant].tip) })}</Typography>
              </div>
              {profile.secondary && (
                <div>
                  <Typography variant="subtitle1">{tr('Secondary: {style}', { style: tr(workStyleArchetypeLabels[profile.secondary]) })}</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {tr('Your answers were close enough that this style shows up too — most people are a blend.')}
                  </Typography>
                </div>
              )}
              {token && (
                // V15 Phase 5 (AH-FR-70 to AH-FR-72, AH-FR-76).
                <>
                  <StyleResultHelpers token={token} profile={profile} />
                  <AiUseSwitch token={token} profile={profile} onChange={setProfile} />
                </>
              )}
              <Button type="button" variant="secondary" onClick={startAssessment}>{tr('Retake the assessment')}</Button>
            </Stack>
          </CardContent>
        </Card>
      )}
      {!taking && profile?.dominant && token && (
        // V15 (AH-FR-73 to AH-FR-75): partners by style, estimates and working-with tips.
        <Box sx={{ mt: 2 }}><PartnerBalanceCard token={token} /></Box>
      )}
      {taking && (
        <Card sx={{ maxWidth: 640 }}>
          <CardHeader
            title={tr('16 quick picks')}
            subheader={tr("Pick whichever statement fits you better — there's no wrong answer. {done} of {total} answered.", { done: answeredCount, total: QUIZ_ITEMS.length })}
          />
          <CardContent>
            <Stack spacing={2}>
              {QUIZ_ITEMS.map((item, index) => (
                <div className="diligence-row" key={item.a}>
                  <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
                    <HelpWithThis area={`question:${index + 1}`} title={tr('Question {number}', { number: index + 1 })} />
                  </Box>
                  <ToggleButtonGroup
                    orientation="vertical"
                    exclusive
                    fullWidth
                    value={answers[index]}
                    onChange={(_event, value) => setAnswers((current) => {
                      const next = [...current];
                      next[index] = value;
                      return next;
                    })}
                    aria-label={tr('Question {number}', { number: index + 1 })}
                  >
                    <ToggleButton value="a" sx={{ textAlign: 'left', justifyContent: 'flex-start' }}>
                      <QuizStatement english={item.a} khmer={item.aKm} />
                    </ToggleButton>
                    <ToggleButton value="b" sx={{ textAlign: 'left', justifyContent: 'flex-start' }}>
                      <QuizStatement english={item.b} khmer={item.bKm} />
                    </ToggleButton>
                  </ToggleButtonGroup>
                </div>
              ))}
              <Button type="button" onClick={() => void handleSubmit()} disabled={saving || answeredCount < QUIZ_ITEMS.length}>
                {saving ? tr('Saving...') : tr('See my result')}
              </Button>
            </Stack>
          </CardContent>
        </Card>
      )}
    </PageSection>
    </PageAssistantProvider>
  );
}

/** One quiz statement: the English line with its Khmer translation underneath. */
function QuizStatement({ english, khmer }: { english: string; khmer: string }) {
  // In Khmer the stored translation is the statement itself; in English it sits underneath.
  if (currentLanguage() === 'km') {
    return <span lang="km" style={{ textTransform: 'none' }}>{khmer}</span>;
  }
  return (
    <Stack spacing={0.5} sx={{ textTransform: 'none' }}>
      <span>{english}</span>
      <Typography component="span" variant="body2" lang="km" sx={{ color: 'text.secondary', lineHeight: 1.8 }}>
        {khmer}
      </Typography>
    </Stack>
  );
}
