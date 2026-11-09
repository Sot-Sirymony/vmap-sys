import { useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { getAiStatus } from '../../api/aiApi';
import { getModuleHelp } from '../../api/aiModuleApi';
import { useI18n } from '../../i18n/I18nContext';
import { tr } from '../../i18n/translate';
import type { LearningModuleDetail, ModuleHelp, ModuleHelpExtras, ModuleHelpMode, ModuleRefine, ModuleRow } from '../../types/vision';
import { Button } from '../common/Button';

type Props = {
  token: string;
  module: LearningModuleDetail;
  rows: ModuleRow[];
  onUseDraft: (rows: ModuleRow[]) => void;
};

/**
 * V9 Phase 7 (AIB-01 to AIB-15): optional AI help inside a module. Questions
 * come one at a time (AI-FR-03); a draft only fills the form on screen, and the
 * module counts, and unlocks the next one, only when the participant saves it
 * (AI-FR-04, AC-09). Hidden while AI is switched off for everyone.
 */
const REFINES: { value: ModuleRefine; label: string }[] = [
  { value: 'SHORTER', label: 'Shorter' },
  { value: 'SPECIFIC', label: 'More specific' },
  { value: 'MY_WORDS', label: 'More in my words' },
  { value: 'ANOTHER', label: 'Another version' },
];

/** Two rows are the same when every field holds the same text. */
function sameRow(left: ModuleRow | undefined, right: ModuleRow | undefined) {
  const keys = new Set([...Object.keys(left ?? {}), ...Object.keys(right ?? {})]);
  return [...keys].every((key) => (left?.[key] ?? '').trim() === (right?.[key] ?? '').trim());
}

/*
 * V15 Phase 4: Explain this module (AH-FR-18); each Help me start question has
 * an answer box, and the answers are used by Draft my answer without being
 * saved (AH-FR-16); after a draft is in the form it can be refined, and rows
 * the participant has changed since are kept as they are (AH-FR-17).
 */
export function ModuleHelpCard({ token, module, rows, onUseDraft }: Readonly<Props>) {
  const { language } = useI18n();
  const [visible, setVisible] = useState(false);
  const [help, setHelp] = useState<ModuleHelp | null>(null);
  const [questionIndex, setQuestionIndex] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [questions, setQuestions] = useState<string[]>([]);
  const [answers, setAnswers] = useState<string[]>([]);
  const [appliedDraft, setAppliedDraft] = useState<ModuleRow[] | null>(null);
  const [note, setNote] = useState('');

  useEffect(() => {
    getAiStatus(token).then((status) => setVisible(status.reason !== 'OFF' && !(status.pagesOff ?? []).includes('MODULE'))).catch(() => setVisible(false));
  }, [token]);

  if (!visible) {
    return null;
  }

  const hasForm = module.fields.length > 0;
  const hasWriting = rows.some((row) => Object.values(row).some((value) => value && value.trim()));

  // AH-FR-16: the questions answered so far, sent with a draft; kept only while the page is open.
  const answered = questions.map((question, index) => ({ question, answer: (answers[index] ?? '').trim() }))
    .filter((item) => item.answer);

  async function ask(mode: ModuleHelpMode, extras: ModuleHelpExtras = {}) {
    setBusy(true);
    setError('');
    setNotice('');
    setHelp(null);
    try {
      const sendRows = mode === 'START' || mode === 'EXPLAIN' ? [] : rows;
      const withAnswers = mode === 'DRAFT' || mode === 'REFINE' ? { ...extras, answers: answered } : extras;
      const next = await getModuleHelp(token, module.number, mode, sendRows, language, withAnswers);
      setHelp(next);
      if (mode === 'START') {
        setQuestionIndex(0);
        setQuestions(next.questions);
        setAnswers(next.questions.map(() => ''));
      }
    } catch (askError) {
      setError(askError instanceof Error ? tr(askError.message) : tr('AI help is temporarily unavailable.'));
    } finally {
      setBusy(false);
    }
  }

  function refine(choice?: ModuleRefine) {
    // AH-FR-17: rows the participant changed since the draft went in are theirs, and stay as they are.
    const keepRows = rows.map((row, index) => (appliedDraft && !sameRow(row, appliedDraft[index]) ? index : -1))
      .filter((index) => index >= 0 && index < 3);
    void ask('REFINE', { refine: choice, note: note.trim() || undefined, keepRows });
  }

  function applyDraft() {
    if (!help) {
      return;
    }
    onUseDraft(help.rows);
    setAppliedDraft(help.rows);
    setHelp(null);
    setNotice(tr('The draft is in the form below. Edit it until it is truly yours, then save the exercise.'));
  }

  return (
    <Card>
      <CardHeader title={tr('Module helper')} subheader={tr('Optional AI help. Your exercise only counts when you save it yourself.')} />
      <CardContent>
        <Stack spacing={1.5}>
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
            <Button variant="secondary" disabled={busy} onClick={() => void ask('EXPLAIN')}>{tr('Explain this module')}</Button>
            <Button variant="secondary" disabled={busy} onClick={() => void ask('START')}>{tr('Help me start')}</Button>
            {hasForm && <Button variant="secondary" disabled={busy} onClick={() => void ask('DRAFT')}>{tr('Draft my answer')}</Button>}
            {hasForm && (
              <Button variant="secondary" disabled={busy || !hasWriting} onClick={() => void ask('FEEDBACK')}>{tr('Feedback on my answer')}</Button>
            )}
          </Box>
          {busy && <Typography variant="body2" color="text.secondary">{tr('The Dream Coach is thinking…')}</Typography>}
          {error && <Alert severity="error">{error}</Alert>}
          {notice && <Alert severity="success">{notice}</Alert>}
          {help && <Chip size="small" label={tr('AI suggestion')} sx={{ alignSelf: 'flex-start' }} />}
          {help?.mode === 'EXPLAIN' && (
            <Stack spacing={1}>
              {help.explanation && <Typography variant="body2">{help.explanation}</Typography>}
              {help.whyItMatters && (
                <Box>
                  <Typography variant="subtitle2">{tr('Why it matters')}</Typography>
                  <Typography variant="body2">{help.whyItMatters}</Typography>
                </Box>
              )}
              {help.example && (
                <Box>
                  <Typography variant="subtitle2">{tr('An example (about someone else)')}</Typography>
                  <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{help.example}</Typography>
                </Box>
              )}
            </Stack>
          )}
          {help?.mode === 'START' && (
            <Stack spacing={1}>
              {help.questions.length > 0 && (
                <Box sx={{ p: 1.5, borderRadius: 2, bgcolor: 'action.hover' }}>
                  <Typography variant="caption" color="text.secondary">
                    {tr('Question {number} of {total}', { number: questionIndex + 1, total: help.questions.length })}
                  </Typography>
                  <Typography variant="body1">{help.questions[questionIndex]}</Typography>
                  {/* AH-FR-16: answer in place; used by Draft my answer, never saved on its own. */}
                  <TextField size="small" fullWidth multiline label={tr('Your answer')} sx={{ mt: 1 }}
                    value={answers[questionIndex] ?? ''}
                    onChange={(event) => setAnswers((current) => current.map((item, index) => (index === questionIndex ? event.target.value : item)))}
                    slotProps={{ htmlInput: { maxLength: 2000, style: { fontSize: 16 } } }} />
                  {questionIndex < help.questions.length - 1 && (
                    <Box sx={{ mt: 1 }}>
                      <Button variant="secondary" onClick={() => setQuestionIndex(questionIndex + 1)}>{tr('Next question')}</Button>
                    </Box>
                  )}
                </Box>
              )}
              {help.example && (
                <Box>
                  <Typography variant="subtitle2">{tr('An example (about someone else)')}</Typography>
                  <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{help.example}</Typography>
                </Box>
              )}
            </Stack>
          )}
          {!help && answered.length > 0 && hasForm && (
            <Typography variant="caption" color="text.secondary">
              {tr('Draft my answer will use your {count} answer(s) above.', { count: answered.length })}
            </Typography>
          )}
          {(help?.mode === 'DRAFT' || help?.mode === 'REFINE') && (
            <Stack spacing={1}>
              {help.rows.length === 0 && <Typography variant="body2">{tr('No draft this time. Try Help me start instead.')}</Typography>}
              {help.mode === 'DRAFT' && answered.length > 0 && (
                <Typography variant="caption" color="text.secondary">{tr('Built on your answers to the questions.')}</Typography>
              )}
              {help.rows.map((row, index) => (
                <Box key={index} sx={{ p: 1.5, borderRadius: 2, border: 1, borderColor: 'divider' }}>
                  {module.fields.filter((field) => row[field.key]).map((field) => (
                    <Typography key={field.key} variant="body2"><strong>{field.label}:</strong> {row[field.key]}</Typography>
                  ))}
                </Box>
              ))}
              {help.rows.length > 0 && (
                <Box>
                  <Button onClick={applyDraft}>{tr('Put this draft in the form')}</Button>
                </Box>
              )}
            </Stack>
          )}
          {appliedDraft && hasForm && (
            <Box component="section" aria-label={tr('Refine the draft')}>
              <Typography variant="subtitle2">{tr('Refine the draft')}</Typography>
              <Typography variant="caption" color="text.secondary">{tr('Rows you have changed since are kept as you wrote them.')}</Typography>
              <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mt: 0.5 }}>
                {REFINES.map((item) => (
                  <Button key={item.value} variant="secondary" disabled={busy} onClick={() => refine(item.value)}>{tr(item.label)}</Button>
                ))}
              </Box>
              <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'flex-start', mt: 1 }}>
                <TextField size="small" label={tr('Or say what to change')} value={note} onChange={(event) => setNote(event.target.value)}
                  sx={{ flex: '1 1 240px' }} slotProps={{ htmlInput: { maxLength: 300, style: { fontSize: 16 } } }} />
                <Button variant="secondary" disabled={busy || !note.trim()} onClick={() => refine()}>{tr('Refine')}</Button>
              </Box>
            </Box>
          )}
          {help?.mode === 'FEEDBACK' && (
            <Stack spacing={1}>
              {help.feedback && <Typography variant="body2">{help.feedback}</Typography>}
              {help.improvements.map((item, index) => (
                <Typography key={index} variant="body2">• {item}</Typography>
              ))}
            </Stack>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}
