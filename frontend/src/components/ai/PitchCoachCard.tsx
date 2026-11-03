import { useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Chip from '@mui/material/Chip';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { getPitchFeedback } from '../../api/aiDailyApi';
import { useI18n } from '../../i18n/I18nContext';
import { tr } from '../../i18n/translate';
import type { PitchAudience, PitchFeedback } from '../../types/vision';
import { Button } from '../common/Button';

const AUDIENCES: { value: PitchAudience; label: string }[] = [
  { value: 'PARTNER', label: 'A possible partner' },
  { value: 'BUYER', label: 'A possible buyer' },
  { value: 'SPONSOR', label: 'A possible sponsor' },
];

/**
 * V9 (AI-FR-16): practise a pitch. The AI plays a partner, buyer or sponsor,
 * reacts in their voice, scores clarity, benefit and the ask from 1 to 5, and
 * offers better wording the participant may copy. Nothing is saved.
 */
export function PitchCoachCard({ token, initialPitch = '' }: { token: string; initialPitch?: string }) {
  const { language } = useI18n();
  const [audience, setAudience] = useState<PitchAudience>('PARTNER');
  const [pitch, setPitch] = useState(initialPitch);
  const [feedback, setFeedback] = useState<PitchFeedback | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [copied, setCopied] = useState(false);

  async function practise() {
    setBusy(true);
    setError('');
    setFeedback(null);
    setCopied(false);
    try {
      setFeedback(await getPitchFeedback(token, audience, pitch.trim(), language));
    } catch (practiseError) {
      setError(practiseError instanceof Error ? tr(practiseError.message) : tr('AI help is temporarily unavailable.'));
    } finally {
      setBusy(false);
    }
  }

  async function copyImproved() {
    try {
      await navigator.clipboard.writeText(feedback?.improvedPitch ?? '');
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }

  return (
    <Card>
      <CardHeader title={tr('Pitch coach')} subheader={tr('Practise your ask. The AI plays your listener, then scores it and suggests better wording.')} />
      <CardContent>
        <Stack spacing={1.5}>
          <Select size="small" value={audience} onChange={(event) => setAudience(event.target.value as PitchAudience)}
            SelectDisplayProps={{ 'aria-label': tr('Who is listening?') }} sx={{ maxWidth: 320 }}>
            {AUDIENCES.map((option) => <MenuItem key={option.value} value={option.value}>{tr(option.label)}</MenuItem>)}
          </Select>
          <TextField multiline minRows={3} size="small" label={tr('Your pitch')} value={pitch}
            onChange={(event) => setPitch(event.target.value)} slotProps={{ htmlInput: { maxLength: 3000 } }} />
          <Box><Button disabled={busy || !pitch.trim()} onClick={() => void practise()}>{busy ? tr('The Dream Coach is thinking…') : tr('Practise')}</Button></Box>
          {error && <Alert severity="error">{error}</Alert>}
          {feedback && (
            <Stack spacing={1}>
              <Chip size="small" label={tr('AI suggestion')} sx={{ alignSelf: 'flex-start' }} />
              {feedback.reaction && (
                <Typography variant="body2" sx={{ fontStyle: 'italic', whiteSpace: 'pre-wrap' }}>“{feedback.reaction}”</Typography>
              )}
              <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                <Chip label={tr('Clarity {score} of 5', { score: feedback.clarity })} />
                <Chip label={tr('Benefit {score} of 5', { score: feedback.benefit })} />
                <Chip label={tr('Ask {score} of 5', { score: feedback.ask })} />
              </Box>
              {feedback.tip && <Alert severity="info">{feedback.tip}</Alert>}
              {feedback.improvedPitch && (
                <Box sx={{ p: 1.5, borderRadius: 2, bgcolor: 'action.hover' }}>
                  <Typography variant="subtitle2">{tr('Suggested wording')}</Typography>
                  <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{feedback.improvedPitch}</Typography>
                  <Box sx={{ mt: 1, display: 'flex', gap: 1 }}>
                    <Button variant="secondary" onClick={() => void copyImproved()}>{copied ? tr('Copied') : tr('Copy')}</Button>
                    <Button variant="secondary" onClick={() => setPitch(feedback.improvedPitch ?? pitch)}>{tr('Use as my pitch')}</Button>
                  </Box>
                </Box>
              )}
            </Stack>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}
