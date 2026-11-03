import { useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { getAiStatus } from '../../api/aiApi';
import { getModuleHelp } from '../../api/aiModuleApi';
import { useI18n } from '../../i18n/I18nContext';
import { tr } from '../../i18n/translate';
import type { LearningModuleDetail, ModuleHelp, ModuleHelpMode, ModuleRow } from '../../types/vision';
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
export function ModuleHelpCard({ token, module, rows, onUseDraft }: Props) {
  const { language } = useI18n();
  const [visible, setVisible] = useState(false);
  const [help, setHelp] = useState<ModuleHelp | null>(null);
  const [questionIndex, setQuestionIndex] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  useEffect(() => {
    getAiStatus(token).then((status) => setVisible(status.reason !== 'OFF')).catch(() => setVisible(false));
  }, [token]);

  if (!visible) {
    return null;
  }

  const hasForm = module.fields.length > 0;
  const hasWriting = rows.some((row) => Object.values(row).some((value) => value && value.trim()));

  async function ask(mode: ModuleHelpMode) {
    setBusy(true);
    setError('');
    setNotice('');
    setHelp(null);
    setQuestionIndex(0);
    try {
      setHelp(await getModuleHelp(token, module.number, mode, mode === 'START' ? [] : rows, language));
    } catch (askError) {
      setError(askError instanceof Error ? tr(askError.message) : tr('AI help is temporarily unavailable.'));
    } finally {
      setBusy(false);
    }
  }

  function applyDraft() {
    if (!help) {
      return;
    }
    onUseDraft(help.rows);
    setHelp(null);
    setNotice(tr('The draft is in the form below. Edit it until it is truly yours, then save the exercise.'));
  }

  return (
    <Card>
      <CardHeader title={tr('Module helper')} subheader={tr('Optional AI help. Your exercise only counts when you save it yourself.')} />
      <CardContent>
        <Stack spacing={1.5}>
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
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
          {help?.mode === 'START' && (
            <Stack spacing={1}>
              {help.questions.length > 0 && (
                <Box sx={{ p: 1.5, borderRadius: 2, bgcolor: 'action.hover' }}>
                  <Typography variant="caption" color="text.secondary">
                    {tr('Question {number} of {total}', { number: questionIndex + 1, total: help.questions.length })}
                  </Typography>
                  <Typography variant="body1">{help.questions[questionIndex]}</Typography>
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
          {help?.mode === 'DRAFT' && (
            <Stack spacing={1}>
              {help.rows.length === 0 && <Typography variant="body2">{tr('No draft this time. Try Help me start instead.')}</Typography>}
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
