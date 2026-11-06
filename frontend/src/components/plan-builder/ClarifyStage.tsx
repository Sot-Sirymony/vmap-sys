import { useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Checkbox from '@mui/material/Checkbox';
import FormControlLabel from '@mui/material/FormControlLabel';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { Link as RouterLink } from 'react-router';
import { clarifyDream, saveClarifiedField } from '../../api/aiPlanApi';
import { formatDate } from '../../i18n/formatDate';
import { tr } from '../../i18n/translate';
import type { ClarifyField, ClarifyQuestion, Dream, PlanDraftContent } from '../../types/vision';
import { Button } from '../common/Button';
import { Loading } from '../common/Loading';
import { type DraftUpdate, addClarification, missingDreamParts } from './draftModel';
import { Prompts } from './parts';

const FIELD_LABELS: Record<ClarifyField, string> = {
  WHY_IMPORTANT: 'Why it matters',
  SUCCESS_DEFINITION: 'What success looks like',
  TARGET_DATE: 'Target date',
};

type Props = {
  dream: Dream;
  content: PlanDraftContent;
  onChange: (update: DraftUpdate) => void;
  onDreamChange: (dream: Dream) => void;
  token: string;
  language: string;
  aiReady: boolean;
  onDone: () => void;
};

/**
 * Stage 1: is the dream clear enough to plan? Shows what the dream says and
 * what is missing. With AI, Claude asks up to three questions, one at a time,
 * answered in place (PB-FR-08). Answers stay in the draft for later
 * suggestions (PB-FR-09); one can also be saved into the dream, only when the
 * participant ticks it (PB-FR-10). Without AI, the program's questions guide.
 */
export function ClarifyStage({ dream, content, onChange, onDreamChange, token, language, aiReady, onDone }: Props) {
  const missing = missingDreamParts(dream);
  const answers = content.clarifications ?? [];
  const [current, setCurrent] = useState<ClarifyQuestion | null>(null);
  const [answer, setAnswer] = useState('');
  const [saveToDream, setSaveToDream] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function ask(given = answers) {
    setBusy(true);
    setError('');
    try {
      const next = await clarifyDream(token, dream.id, language, given);
      setCurrent(next);
      setAnswer('');
      setSaveToDream(false);
    } catch (askError) {
      setError(askError instanceof Error ? tr(askError.message) : tr('AI help is temporarily unavailable.'));
    } finally {
      setBusy(false);
    }
  }

  async function submit() {
    if (!current?.question || !answer.trim()) {
      return;
    }
    const item = { question: current.question, answer: answer.trim() };
    setBusy(true);
    setError('');
    try {
      if (saveToDream && current.field) {
        onDreamChange(await saveClarifiedField(token, dream.id, current.field, item.answer));
      }
    } catch (saveError) {
      setBusy(false);
      setError(saveError instanceof Error ? tr(saveError.message) : tr('Unable to save to your dream.'));
      return;
    }
    const given = addClarification(content, item).clarifications ?? [];
    onChange((draft) => addClarification(draft, item));
    await ask(given);
  }

  return (
    <Stack spacing={2}>
      <Box sx={{ display: 'grid', gap: 1 }}>
        <Typography variant="h6">{dream.title}</Typography>
        <Field label="Why it matters" value={dream.whyImportant} />
        <Field label="What success looks like" value={dream.successDefinition} />
        <Field label="Target date" value={dream.targetDate ? formatDate(new Date(`${dream.targetDate}T00:00:00`), { year: true }) : null} />
      </Box>
      {missing.length > 0 ? (
        <Alert severity="info">
          {tr('A clear dream is easier to plan. Still missing: {parts}.', { parts: missing.map((part) => tr(part)).join(', ') })}{' '}
          <Link component={RouterLink} to={`/dreams/${dream.id}`}>{tr('Edit the dream')}</Link>
        </Alert>
      ) : (
        <Alert severity="success">{tr('Your dream says why it matters, what success looks like and when. You are ready to plan.')}</Alert>
      )}

      {answers.length > 0 && (
        <Box sx={{ display: 'grid', gap: 1 }}>
          <Typography variant="subtitle2">{tr('Your answers')}</Typography>
          {answers.map((item, index) => (
            <Box key={index}>
              <Typography variant="body2" color="text.secondary">{item.question}</Typography>
              <Typography variant="body2">{item.answer}</Typography>
            </Box>
          ))}
          <Typography variant="caption" color="text.secondary">{tr('These answers are used for the suggestions that follow.')}</Typography>
        </Box>
      )}

      {aiReady && !current && (
        <Box>
          <Button disabled={busy} onClick={() => void ask()}>
            {answers.length > 0 ? tr('Ask me another question') : tr('Ask me questions first')}
          </Button>
        </Box>
      )}
      {busy && <Loading />}
      {error && <Alert severity="error">{error}</Alert>}
      {current?.done && (
        <Alert severity="success" action={<Button onClick={onDone}>{tr('Go to goals')}</Button>}>
          {tr('Your dream is clear enough to plan.')}
        </Alert>
      )}
      {current && !current.done && current.question && (
        <Alert severity="info" icon={false}>
          <Stack spacing={1}>
            <Typography variant="caption" color="text.secondary">
              {tr('Question {number} of up to {max}', { number: Math.min(answers.length + 1, 3), max: 3 })}
            </Typography>
            <Typography variant="body2" sx={{ fontWeight: 600 }}>{current.question}</Typography>
            {current.field === 'TARGET_DATE' ? (
              <TextField size="small" type="date" label={tr('Your answer')} value={answer}
                onChange={(event) => setAnswer(event.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
            ) : (
              <TextField size="small" label={tr('Your answer')} value={answer} multiline minRows={2}
                slotProps={{ htmlInput: { maxLength: 1000 } }} onChange={(event) => setAnswer(event.target.value)} />
            )}
            {current.field && (
              <FormControlLabel
                control={<Checkbox checked={saveToDream} onChange={(event) => setSaveToDream(event.target.checked)} />}
                label={tr('Also save this to my dream as "{field}"', { field: tr(FIELD_LABELS[current.field]) })}
              />
            )}
            <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
              <Button disabled={busy || !answer.trim()} onClick={() => void submit()}>{tr('Answer')}</Button>
              <Button variant="secondary" disabled={busy} onClick={() => setCurrent({ done: true })}>{tr('Stop asking')}</Button>
            </Box>
          </Stack>
        </Alert>
      )}

      <Box>
        <Typography variant="subtitle2" sx={{ mb: 0.5 }}>{tr('Questions to think about')}</Typography>
        <Prompts questions={[
          'What exactly do you want to achieve?',
          'Why does this matter to you?',
          'What will success look like?',
          'When do you want to achieve it?',
        ]} />
      </Box>
    </Stack>
  );
}

function Field({ label, value }: { label: string; value?: string | null }) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary">{tr(label)}</Typography>
      <Typography variant="body2" color={value ? 'text.primary' : 'text.disabled'}>{value || tr('Not written yet')}</Typography>
    </Box>
  );
}
