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
    <PageSection
      title={tr('Work Style')}
      subtitle={tr('A short, retakeable self-assessment (FR-49) — diagnostic only, it never affects any other part of the system.')}
    >
      {error && <ErrorMessage message={error} />}
      {!taking && profile && profile.dominant && (
        <Card sx={{ maxWidth: 640 }}>
          <CardHeader title={tr('Your result')} subheader={tr('Retake at any time — a new result replaces this one.')} />
          <CardContent>
            <Stack spacing={2}>
              <div>
                <Typography variant="subtitle1">Dominant: {workStyleArchetypeLabels[profile.dominant]}</Typography>
                <Typography variant="body2" color="text.secondary">{ARCHETYPE_GUIDANCE[profile.dominant].strengths}</Typography>
                <Typography variant="body2" color="text.secondary">{tr('Likely blind spot: {text}', { text: ARCHETYPE_GUIDANCE[profile.dominant].blindSpot })}</Typography>
                <Typography variant="body2" color="text.secondary">{tr('Working with them: {text}', { text: ARCHETYPE_GUIDANCE[profile.dominant].tip })}</Typography>
              </div>
              {profile.secondary && (
                <div>
                  <Typography variant="subtitle1">Secondary: {workStyleArchetypeLabels[profile.secondary]}</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {tr('Your answers were close enough that this style shows up too — most people are a blend.')}
                  </Typography>
                </div>
              )}
              <Button type="button" variant="secondary" onClick={startAssessment}>{tr('Retake the assessment')}</Button>
            </Stack>
          </CardContent>
        </Card>
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
