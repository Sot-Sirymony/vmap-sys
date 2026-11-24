import { useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import Step from '@mui/material/Step';
import StepLabel from '@mui/material/StepLabel';
import Stepper from '@mui/material/Stepper';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useNavigate, useSearchParams } from 'react-router';
import { getAiStatus } from '../api/aiApi';
import { dreamIntake, suggestVisionStatement } from '../api/aiPlanApi';
import { createDream } from '../api/dreamApi';
import { createVisionArea, listVisionAreas, updateVisionArea } from '../api/visionAreaApi';
import { SuggestionRating } from '../components/ai/SuggestionRating';
import { Button } from '../components/common/Button';
import { Loading } from '../components/common/Loading';
import { STARTER_AREA_SUGGESTIONS } from '../components/forms/VisionAreaWizard';
import { DateField, PrioritySelect, Prompts, twoColumns } from '../components/plan-builder/parts';
import { useAuth } from '../context/AuthContext';
import { useI18n } from '../i18n/I18nContext';
import { tr } from '../i18n/translate';
import type { DreamIntake, DreamIntakeAnswer, DreamType, Priority, VisionArea } from '../types/vision';
import { dreamTypeLabels } from '../utils/enumLabels';
import { PageSection } from './PageSection';

const NEW_AREA = 'new';
const MAX_QUESTIONS = 3;

type Stage = 'AREA' | 'DREAM';

/** The dream as it will be saved; every field stays editable (DC-FR-17). */
type DreamFields = {
  title: string;
  whyImportant: string;
  successDefinition: string;
  targetDate: string | null;
  priority: Priority;
  dreamType: DreamType;
};

const errorText = (error: unknown, fallback: string) => (error instanceof Error ? tr(error.message) : tr(fallback));

/**
 * V31 v1 step 1 (BRD 31 stages 1 and 2, DC-FR-02, DC-FR-10 to DC-FR-17): the
 * Dream Coach's start, before a dream exists. Stage 1 picks or creates the
 * vision area and confirms its vision statement. Stage 2 takes the dream in
 * the participant's own words, asks up to three questions, and restates it.
 * Nothing is saved until "This is right": then the area (if new or edited)
 * and the dream (as an Idea) are saved through the normal endpoints, and Build
 * my plan opens at the goals (D-31-2 as amended). Every step works without AI.
 */
export function DreamCoachStartPage() {
  const { token } = useAuth();
  const { language } = useI18n();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [stage, setStage] = useState<Stage>('AREA');
  const [areas, setAreas] = useState<VisionArea[] | null>(null);
  const [aiReady, setAiReady] = useState(false);
  const [loadError, setLoadError] = useState('');

  // Stage 1.
  const [initialArea] = useState(() => params.get('areaId') ?? '');
  const [areaChoice, setAreaChoice] = useState<string>(initialArea);
  const [newAreaName, setNewAreaName] = useState('');
  const [statement, setStatement] = useState('');
  const [sentence, setSentence] = useState('');
  const [statementRequest, setStatementRequest] = useState<number | null>(null);

  // Stage 2.
  const [words, setWords] = useState('');
  const [answers, setAnswers] = useState<DreamIntakeAnswer[]>([]);
  const [question, setQuestion] = useState<DreamIntake | null>(null);
  const [answer, setAnswer] = useState('');
  const [fields, setFields] = useState<DreamFields | null>(null);
  const [intakeRequest, setIntakeRequest] = useState<number | null>(null);
  // An area made by an earlier attempt, so trying again never makes a second one.
  const [createdAreaId, setCreatedAreaId] = useState<number | null>(null);

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!token) {
      return undefined;
    }
    let cancelled = false;
    listVisionAreas(token)
      .then((list) => {
        if (cancelled) {
          return;
        }
        const active = list.filter((area) => !area.archived);
        setAreas(active);
        // DC-FR-02: "Plan a dream in this area" opens with that area chosen.
        const chosen = active.find((area) => String(area.id) === initialArea);
        setAreaChoice(chosen ? initialArea : active.length === 0 ? NEW_AREA : '');
        setStatement(chosen?.visionStatement ?? '');
      })
      .catch((listError) => !cancelled && setLoadError(errorText(listError, 'Unable to load your vision areas.')));
    getAiStatus(token).then((status) => !cancelled && setAiReady(status.available)).catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [token, initialArea]);

  const area = areas?.find((item) => String(item.id) === areaChoice) ?? null;
  const areaName = areaChoice === NEW_AREA ? newAreaName.trim() : area?.name ?? '';
  const areaReady = areaChoice === NEW_AREA ? newAreaName.trim().length > 0 : area !== null;

  function chooseArea(value: string) {
    setAreaChoice(value);
    setStatement(areas?.find((item) => String(item.id) === value)?.visionStatement ?? '');
    setStatementRequest(null);
  }

  async function wordStatement() {
    if (!token || !sentence.trim() || !areaName) {
      return;
    }
    setBusy(true);
    setError('');
    try {
      const draft = await suggestVisionStatement(token, language, { areaName, sentence: sentence.trim() });
      setStatement(draft.statement ?? sentence.trim());
      setStatementRequest(draft.requestId ?? null);
    } catch (wordError) {
      setError(errorText(wordError, 'AI help is temporarily unavailable.'));
    } finally {
      setBusy(false);
    }
  }

  /** DC-FR-15, DC-FR-16: the next question, or the restatement. */
  async function askCoach(given: DreamIntakeAnswer[], suggestAnyway = false) {
    if (!token) {
      return;
    }
    setBusy(true);
    setError('');
    try {
      const result = await dreamIntake(token, language, { areaName: areaName || undefined, words: words.trim(), answers: given, suggestAnyway });
      setIntakeRequest(result.requestId ?? null);
      if (result.done) {
        setQuestion(null);
        setFields({
          title: result.title ?? words.trim().slice(0, 220),
          whyImportant: result.whyImportant ?? '',
          successDefinition: result.successDefinition ?? '',
          targetDate: result.targetDate ?? null,
          priority: 'MEDIUM',
          dreamType: 'LONG_TERM',
        });
      } else {
        setQuestion(result);
        setAnswer('');
      }
    } catch (askError) {
      setError(errorText(askError, 'AI help is temporarily unavailable.'));
    } finally {
      setBusy(false);
    }
  }

  async function submitAnswer() {
    if (!question?.question || !answer.trim()) {
      return;
    }
    const given = [...answers, { question: question.question, answer: answer.trim(), field: question.field ?? null }];
    setAnswers(given);
    await askCoach(given);
  }

  /** Without AI, or when the participant prefers: the fields, filled by hand. */
  function writeMyself() {
    setQuestion(null);
    setFields({ title: words.trim().slice(0, 220), whyImportant: '', successDefinition: '', targetDate: null, priority: 'MEDIUM',
      dreamType: 'LONG_TERM' });
  }

  /** "This is right": save the area if needed, then the dream, then open Build my plan. */
  async function saveAndPlan() {
    if (!token || !fields || !fields.title.trim() || !areaReady) {
      return;
    }
    setBusy(true);
    setError('');
    try {
      let areaId = createdAreaId ?? area?.id ?? null;
      if (areaId === null) {
        const created = await createVisionArea(token, {
          name: newAreaName.trim(), visionStatement: statement.trim() || undefined, priority: 'MEDIUM', status: 'ACTIVE',
        });
        areaId = created.id;
        setCreatedAreaId(created.id);
      } else if (area && (area.visionStatement ?? '') !== statement.trim()) {
        await updateVisionArea(token, area.id, {
          name: area.name, description: area.description, visionStatement: statement.trim() || undefined,
          priority: area.priority, letterRank: area.letterRank ?? null, status: area.status,
        });
      }
      const dream = await createDream(token, {
        visionAreaId: areaId,
        title: fields.title.trim(),
        description: words.trim() || undefined,
        whyImportant: fields.whyImportant.trim() || undefined,
        successDefinition: fields.successDefinition.trim() || undefined,
        dreamType: fields.dreamType,
        priority: fields.priority,
        targetDate: fields.targetDate ?? undefined,
        status: 'IDEA',
        moonshot: false,
        scheduleMode: 'BOTTOM_UP',
      });
      navigate(`/dreams/${dream.id}/build`, { state: { fromCoach: true } });
    } catch (saveError) {
      setError(errorText(saveError, 'Unable to save your dream.'));
      setBusy(false);
    }
  }

  const update = (patch: Partial<DreamFields>) => setFields((current) => (current ? { ...current, ...patch } : current));

  if (!areas && !loadError) {
    return <PageSection title={tr('Dream Coach')} subtitle=""><Loading /></PageSection>;
  }

  return (
    <PageSection title={tr('Dream Coach')} subtitle={tr('From a dream in your own words to a plan you can follow.')}>
      <Stack spacing={2}>
        {loadError && <Alert severity="error">{loadError}</Alert>}
        {!aiReady && (
          <Alert severity="info">{tr('AI suggestions are off. You can still write your dream with the questions on each step.')}</Alert>
        )}
        <Box sx={{ overflowX: 'auto', pb: 1 }}>
          <Stepper activeStep={stage === 'AREA' ? 0 : 1} sx={{ minWidth: 420 }}>
            <Step><StepLabel>{tr('Vision area')}</StepLabel></Step>
            <Step><StepLabel>{tr('Dream')}</StepLabel></Step>
            <Step><StepLabel>{tr('Plan')}</StepLabel></Step>
          </Stepper>
        </Box>

        <Card>
          <CardContent>
            {stage === 'AREA' && (
              <Stack spacing={2}>
                <Typography variant="h6" component="h2">{tr('Which part of your life or work is this dream about?')}</Typography>
                <TextField select size="small" label={tr('Vision area')} value={areaChoice} sx={{ maxWidth: 420 }}
                  onChange={(event) => chooseArea(event.target.value)}>
                  {(areas ?? []).map((item) => <MenuItem key={item.id} value={String(item.id)}>{item.name}</MenuItem>)}
                  <MenuItem value={NEW_AREA}>{tr('A new vision area')}</MenuItem>
                </TextField>
                {areaChoice === NEW_AREA && (
                  <Stack spacing={1}>
                    <TextField size="small" label={tr('Name')} value={newAreaName} required sx={{ maxWidth: 420 }}
                      slotProps={{ htmlInput: { maxLength: 120 } }} onChange={(event) => setNewAreaName(event.target.value)} />
                    {(areas ?? []).length === 0 && (
                      <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }} aria-label={tr('Starter areas')}>
                        {STARTER_AREA_SUGGESTIONS.map((name) => (
                          <Chip key={name} label={tr(name)} onClick={() => setNewAreaName(tr(name))} variant="outlined" />
                        ))}
                      </Box>
                    )}
                  </Stack>
                )}
                {areaReady && (
                  <Stack spacing={1}>
                    <TextField size="small" label={tr('Vision statement')} value={statement} multiline minRows={2}
                      helperText={statement.trim() ? tr('Keep it, edit it, or clear it to leave it empty for now.')
                        : tr('Optional. What this part of your life looks like when it is going well.')}
                      slotProps={{ htmlInput: { maxLength: 3000 } }} onChange={(event) => setStatement(event.target.value)} />
                    {!statement.trim() && aiReady && (
                      <Box sx={{ display: 'grid', gap: 1, p: 1.5, border: 1, borderColor: 'divider', borderRadius: 'var(--style-radius-control)' }}>
                        <TextField size="small" label={tr('In one sentence, what do you want this part of your life to look like in a few years?')}
                          value={sentence} multiline slotProps={{ htmlInput: { maxLength: 500 } }}
                          onChange={(event) => setSentence(event.target.value)} />
                        <Box>
                          <Button variant="secondary" disabled={busy || !sentence.trim()} onClick={() => void wordStatement()}>
                            {tr('Word it for me')}
                          </Button>
                        </Box>
                      </Box>
                    )}
                    {statementRequest !== null && (
                      <Typography variant="caption" color="text.secondary">
                        {tr('AI suggestion, worded from your sentence. Edit it until it sounds like you.')}
                      </Typography>
                    )}
                    {statementRequest !== null && <SuggestionRating key={statementRequest} token={token ?? ''} requestId={statementRequest} />}
                  </Stack>
                )}
                <Box>
                  <Button disabled={!areaReady || busy} onClick={() => setStage('DREAM')}>{tr('Next')}</Button>
                </Box>
              </Stack>
            )}

            {stage === 'DREAM' && (
              <Stack spacing={2}>
                <Typography variant="h6" component="h2">{tr('What is your dream?')}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {tr('Describe what success looks like. Leave out what might stop you; you will deal with that in the steps.')}
                </Typography>
                <TextField label={tr('Your dream, in your own words')} value={words} multiline minRows={3} required
                  disabled={fields !== null || question !== null || answers.length > 0}
                  slotProps={{ htmlInput: { maxLength: 1000 } }} onChange={(event) => setWords(event.target.value)} />

                {answers.length > 0 && (
                  <Box sx={{ display: 'grid', gap: 1 }}>
                    {answers.map((item, index) => (
                      <Box key={index}>
                        <Typography variant="body2" color="text.secondary">{item.question}</Typography>
                        <Typography variant="body2">{item.answer}</Typography>
                      </Box>
                    ))}
                  </Box>
                )}

                {!fields && !question && (
                  <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                    {aiReady && (
                      <Button disabled={busy || !words.trim()} onClick={() => void askCoach(answers)}>{tr('Help me make it clear')}</Button>
                    )}
                    <Button variant={aiReady ? 'secondary' : 'primary'} disabled={busy || !words.trim()} onClick={writeMyself}>
                      {tr('Fill in the details myself')}
                    </Button>
                  </Box>
                )}

                {question?.question && (
                  <Alert severity="info" icon={false}>
                    <Stack spacing={1}>
                      <Typography variant="caption" color="text.secondary">
                        {tr('Question {number} of up to {max}', { number: Math.min(answers.length + 1, MAX_QUESTIONS), max: MAX_QUESTIONS })}
                      </Typography>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>{question.question}</Typography>
                      {question.field === 'TARGET_DATE' ? (
                        <TextField size="small" type="date" label={tr('Your answer')} value={answer}
                          onChange={(event) => setAnswer(event.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
                      ) : (
                        <TextField size="small" label={tr('Your answer')} value={answer} multiline minRows={2}
                          slotProps={{ htmlInput: { maxLength: 1000 } }} onChange={(event) => setAnswer(event.target.value)} />
                      )}
                      {question.field === 'TARGET_DATE' && (
                        <Typography variant="caption" color="text.secondary">
                          {tr('Not sure yet? Skip the questions; you can set a date before saving your plan.')}
                        </Typography>
                      )}
                      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                        <Button disabled={busy || !answer.trim()} onClick={() => void submitAnswer()}>{tr('Answer')}</Button>
                        <Button variant="secondary" disabled={busy} onClick={() => void askCoach(answers, true)}>{tr('Skip the questions')}</Button>
                      </Box>
                    </Stack>
                  </Alert>
                )}

                {fields && (
                  <Stack spacing={1.5}>
                    <Alert severity="info" icon={false}>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>{tr('Here is your dream as I understand it. Is that right?')}</Typography>
                      <Typography variant="body2">{tr('Change any word. Nothing is saved until you say it is right.')}</Typography>
                    </Alert>
                    <TextField size="small" label={tr('Dream')} value={fields.title} required
                      slotProps={{ htmlInput: { maxLength: 220 } }} onChange={(event) => update({ title: event.target.value })} />
                    <TextField size="small" label={tr('Why it matters')} value={fields.whyImportant} multiline
                      onChange={(event) => update({ whyImportant: event.target.value })} />
                    <TextField size="small" label={tr('What success looks like')} value={fields.successDefinition} multiline
                      onChange={(event) => update({ successDefinition: event.target.value })} />
                    <Box sx={twoColumns}>
                      <DateField label="Target date" value={fields.targetDate} onChange={(value) => update({ targetDate: value })} />
                      <PrioritySelect value={fields.priority} onChange={(value) => update({ priority: value })} />
                      <TextField select size="small" label={tr('Dream type')} value={fields.dreamType}
                        onChange={(event) => update({ dreamType: event.target.value as DreamType })}>
                        {(Object.keys(dreamTypeLabels) as DreamType[]).map((type) => (
                          <MenuItem key={type} value={type}>{dreamTypeLabels[type]}</MenuItem>
                        ))}
                      </TextField>
                    </Box>
                    {intakeRequest !== null && <SuggestionRating key={intakeRequest} token={token ?? ''} requestId={intakeRequest} />}
                    <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                      <Button disabled={busy || !fields.title.trim()} onClick={() => void saveAndPlan()}>{tr('This is right, save it and plan')}</Button>
                    </Box>
                    <Typography variant="caption" color="text.secondary">
                      {tr('Your dream is saved as an idea, then Build my plan opens at the goals.')}
                    </Typography>
                  </Stack>
                )}

                {busy && <Loading />}
                <Box>
                  <Typography variant="subtitle2" sx={{ mb: 0.5 }}>{tr('Questions to think about')}</Typography>
                  <Prompts questions={[
                    'What exactly do you want to achieve?',
                    'Why does this matter to you?',
                    'What will success look like?',
                    'When do you want to achieve it?',
                  ]} />
                </Box>
                <Box>
                  <Button variant="secondary" disabled={busy} onClick={() => setStage('AREA')}>{tr('Back')}</Button>
                </Box>
              </Stack>
            )}
            {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
          </CardContent>
        </Card>
      </Stack>
    </PageSection>
  );
}
