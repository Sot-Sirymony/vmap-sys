import { useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { draftModuleContent, translateToKhmer } from '../../api/aiAdminApi';
import { listModuleContent } from '../../api/moduleContentApi';
import { tr } from '../../i18n/translate';
import type { ModuleContent, ModuleContentDraft } from '../../types/vision';
import { Button } from '../common/Button';

export type ModuleTextFields = {
  title?: string;
  summary?: string;
  guidance?: string;
  exerciseInstructions?: string;
  labels?: Record<string, string>;
};

type Props = {
  token: string;
  module: ModuleContent;
  language: string;
  onFill: (fields: ModuleTextFields) => void;
};

/**
 * V9 (AI-FR-22, AI-FR-23): AI drafts for an admin editing a module. In English,
 * a better version plus examples and quiz questions; in Khmer, a translation of
 * the English text. Either only fills the form: nothing is published until the
 * admin saves, ideally after a Khmer speaker has checked a translation (D-4).
 */
export function ModuleContentAiCard({ token, module, language, onFill }: Props) {
  const [instruction, setInstruction] = useState('');
  const [draft, setDraft] = useState<ModuleContentDraft | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await action();
    } catch (runError) {
      setError(runError instanceof Error ? tr(runError.message) : tr('AI help is temporarily unavailable.'));
    } finally {
      setBusy(false);
    }
  }

  const askDraft = () => run(async () => {
    setDraft(await draftModuleContent(token, module.number, instruction.trim()));
  });

  const askTranslation = () => run(async () => {
    const english = (await listModuleContent(token, 'en')).find((item) => item.number === module.number);
    if (!english) {
      return;
    }
    const texts = [english.title, english.summary, english.guidance, english.exerciseInstructions, ...english.fields.map((field) => field.label)];
    const { translations } = await translateToKhmer(token, texts);
    const labels: Record<string, string> = {};
    english.fields.forEach((field, index) => {
      const label = translations[4 + index];
      if (label) {
        labels[field.key] = label;
      }
    });
    onFill({
      title: translations[0] ?? undefined,
      summary: translations[1] ?? undefined,
      guidance: translations[2] ?? undefined,
      exerciseInstructions: translations[3] ?? undefined,
      labels,
    });
    setNotice(tr('The Khmer draft is in the form. Have a Khmer speaker check it before you save.'));
  });

  return (
    <Box sx={{ p: 1.5, borderRadius: 2, border: 1, borderColor: 'divider' }}>
      <Stack spacing={1}>
        <Typography variant="subtitle2">{tr('AI help for this text')}</Typography>
        {language === 'en' && (
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'center' }}>
            <TextField size="small" sx={{ flex: '1 1 240px' }} label={tr('What should change? (optional)')} value={instruction}
              onChange={(event) => setInstruction(event.target.value)} slotProps={{ htmlInput: { maxLength: 1000 } }} />
            <Button variant="secondary" disabled={busy} onClick={() => void askDraft()}>{busy ? tr('The Dream Coach is thinking…') : tr('Draft with AI')}</Button>
          </Box>
        )}
        {language === 'km' && (
          <Box>
            <Button variant="secondary" disabled={busy} onClick={() => void askTranslation()}>
              {busy ? tr('The Dream Coach is thinking…') : tr('Translate from English with AI')}
            </Button>
          </Box>
        )}
        {error && <Alert severity="error">{error}</Alert>}
        {notice && <Alert severity="info">{notice}</Alert>}
        {draft && (
          <Stack spacing={1}>
            <Chip size="small" label={tr('AI suggestion')} sx={{ alignSelf: 'flex-start' }} />
            {draft.title && <Typography variant="body2"><strong>{tr('Title')}:</strong> {draft.title}</Typography>}
            {draft.summary && <Typography variant="body2"><strong>{tr('Summary')}:</strong> {draft.summary}</Typography>}
            {draft.guidance && <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}><strong>{tr('Guidance')}:</strong> {draft.guidance}</Typography>}
            {draft.exerciseInstructions && (
              <Typography variant="body2"><strong>{tr('Exercise instructions')}:</strong> {draft.exerciseInstructions}</Typography>
            )}
            <Box>
              <Button onClick={() => {
                onFill({
                  title: draft.title ?? undefined,
                  summary: draft.summary ?? undefined,
                  guidance: draft.guidance ?? undefined,
                  exerciseInstructions: draft.exerciseInstructions ?? undefined,
                });
                setNotice(tr('The draft is in the form. Read it through, then save the module.'));
              }}>
                {tr('Put this text in the form')}
              </Button>
            </Box>
            {draft.examples && (
              <Box>
                <Typography variant="subtitle2">{tr('Examples')}</Typography>
                <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{draft.examples}</Typography>
              </Box>
            )}
            {draft.quiz.length > 0 && (
              <Box>
                <Typography variant="subtitle2">{tr('Quiz questions')}</Typography>
                {draft.quiz.map((item, index) => (
                  <Typography key={index} variant="body2">{index + 1}. {item.question} <em>{item.answer}</em></Typography>
                ))}
                <Typography variant="caption" color="text.secondary">{tr('The app has no quiz yet; copy these into your teaching material.')}</Typography>
              </Box>
            )}
          </Stack>
        )}
      </Stack>
    </Box>
  );
}
