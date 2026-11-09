import { useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Checkbox from '@mui/material/Checkbox';
import FormControlLabel from '@mui/material/FormControlLabel';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { getAiStatus } from '../../api/aiApi';
import { recordPageOutcome } from '../../api/aiPageApi';
import { obstacleHelp } from '../../api/obstacleHelpApi';
import { useI18n } from '../../i18n/I18nContext';
import { tr } from '../../i18n/translate';
import type { ObstacleHelpAnswer, PageChange } from '../../types/vision';
import { Button } from '../common/Button';
import { SuggestionRating } from '../ai/SuggestionRating';
import { aiOnFor } from '../ai/aiFocus';
import { PageChangeCard } from '../ai/page-assistant/PageChangeCard';

const FIELD_LABELS: Record<string, string> = {
  criticismSubstance: 'The useful point',
  conflictOtherPerspective: 'Their likely view',
  conflictNextAction: 'One calm next action',
};

type Props = {
  token: string;
  obstacleId: number | null;
  /** The obstacle form as typed, without the private note. */
  read: () => Record<string, string>;
  /** The private "your own reaction" note; sent only when the participant ticks the box, for this request. */
  privateNote: string;
  apply: (field: string, value: string) => void;
};

/**
 * V15 (AH-FR-40): help with the conflict worksheet and criticism mining: the
 * useful point apart from the emotion, the other person's likely view and one
 * calm next action, as cards that only fill the form. The private reaction is
 * sent only when ticked, for that one request. Crisis wording gets the safety
 * response instead of AI help (AI-NFR-09).
 */
export function ConflictHelp({ token, obstacleId, read, privateNote, apply }: Readonly<Props>) {
  const { language } = useI18n();
  const [aiOn, setAiOn] = useState(false);
  const [includeNote, setIncludeNote] = useState(false);
  const [answer, setAnswer] = useState<ObstacleHelpAnswer | null>(null);
  const [cards, setCards] = useState<(PageChange & { id: number })[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [applied, setApplied] = useState('');

  useEffect(() => {
    getAiStatus(token).then((status) => setAiOn(aiOnFor(status, 'OBSTACLES'))).catch(() => setAiOn(false));
  }, [token]);

  if (!aiOn) {
    return null;
  }

  async function ask() {
    setBusy(true);
    setError('');
    setApplied('');
    try {
      const form = includeNote && privateNote.trim() ? { ...read(), conflictPrivateNote: privateNote } : read();
      const next = await obstacleHelp(token, language, { mode: 'CONFLICT', obstacleId, form, includePrivateNote: includeNote });
      setAnswer(next);
      setCards(next.changes.map((change, index) => ({ ...change, id: index })));
    } catch (askError) {
      setError(askError instanceof Error ? tr(askError.message) : tr('AI help is temporarily unavailable.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Box className="field-full" component="section" aria-label={tr('Help me think this through')}>
      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'center' }}>
        <Button variant="secondary" disabled={busy} onClick={() => void ask()}>
          {busy ? tr('The Dream Coach is thinking…') : tr('Help me think this through')}
        </Button>
        {privateNote.trim() && (
          <FormControlLabel
            control={<Checkbox size="small" checked={includeNote} onChange={(event) => setIncludeNote(event.target.checked)} />}
            label={<Typography variant="body2">{tr('Include my private reaction, for this request only')}</Typography>}
          />
        )}
      </Box>
      {error && <Alert severity="error" sx={{ mt: 1 }}>{error}</Alert>}
      <Box aria-live="polite" sx={{ mt: 1 }}>
        {answer?.mode === 'CRISIS' && <Alert severity="warning" sx={{ whiteSpace: 'pre-wrap' }}>{answer.note}</Alert>}
        {applied && <Alert severity="success" sx={{ mb: 1 }}>{applied}</Alert>}
        {cards.length > 0 && (
          <Stack spacing={1}>
            {cards.map((card) => (
              <PageChangeCard key={card.id} change={card} label={tr(FIELD_LABELS[card.field] ?? card.field)}
                onApply={(value, edited) => {
                  apply(card.field, value);
                  if (answer?.requestId) {
                    recordPageOutcome(token, answer.requestId, edited ? 0 : 1, edited ? 1 : 0).catch(() => undefined);
                  }
                  setApplied(tr('Added to the form. Nothing is saved until you press Save.'));
                  setCards((current) => current.filter((item) => item.id !== card.id));
                }}
                onDismiss={() => setCards((current) => current.filter((item) => item.id !== card.id))} />
            ))}
          </Stack>
        )}
        {answer && answer.mode === 'CONFLICT' && <SuggestionRating token={token} requestId={answer.requestId} />}
      </Box>
    </Box>
  );
}
