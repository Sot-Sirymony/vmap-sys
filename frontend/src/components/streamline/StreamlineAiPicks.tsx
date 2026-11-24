import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import { getAiStatus } from '../../api/aiApi';
import { pickStreamlineCandidates } from '../../api/streamlineApi';
import { currentLanguage, tr } from '../../i18n/translate';
import { surface } from '../../design/surface';
import type { AiStatus } from '../../types/vision';
import type { StreamlinePick } from '../../types/streamline';
import { Button } from '../common/Button';
import { aiUnavailableMessage } from './StreamlineSuggestionsPanel';

/**
 * V30 (D-30-4, SA-FR-01): AI reads the user's open goals and steps and suggests
 * the ones a Streamline review would help most, best first. Suggestions only:
 * nothing starts until the user chooses Start review.
 */
export function StreamlineAiPicks({ token, onStart }: { token: string; onStart: (pick: StreamlinePick) => void }) {
  const [status, setStatus] = useState<AiStatus | null>(null);
  const [picks, setPicks] = useState<StreamlinePick[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    getAiStatus(token).then((value) => !cancelled && setStatus(value)).catch(() => !cancelled && setStatus(null));
    return () => {
      cancelled = true;
    };
  }, [token]);

  async function ask() {
    setBusy(true);
    setError('');
    try {
      setPicks((await pickStreamlineCandidates(token, currentLanguage())).candidates);
    } catch (askError) {
      setError(askError instanceof Error ? tr(askError.message) : tr('AI help is temporarily unavailable.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Box component="section" aria-label={tr('AI picks')} sx={{ ...surface('card'), display: 'grid', gap: 1 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1, flexWrap: 'wrap' }}>
        <Typography variant="h3">{tr('AI picks')}</Typography>
        {(!status || status.available) && (
          <Button variant="secondary" disabled={busy} onClick={() => void ask()}>
            {busy ? tr('Thinking…') : picks ? tr('Ask again') : tr('Ask AI which to streamline')}
          </Button>
        )}
      </Box>
      {status && !status.available && <Alert severity="info">{aiUnavailableMessage(status.reason)}</Alert>}
      {error && <Alert severity="warning">{error}</Alert>}
      {picks && picks.length === 0 && (
        <Typography variant="body2">{tr('Nothing stands out right now. Your open goals and steps look lean.')}</Typography>
      )}
      {picks?.map((pick, index) => (
        <Box key={`${pick.anchorType}-${pick.anchorId}`} sx={{ ...surface('inset'), p: 1.25, display: 'grid', gap: 0.5 }}
          aria-label={tr('AI pick {rank}: {title}', { rank: index + 1, title: pick.title })}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
            <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 700 }}>#{index + 1}</Typography>
            <Chip size="small" variant="outlined" label={pick.anchorType === 'GOAL' ? tr('Goal') : tr('Step')} />
            <Typography variant="body2" sx={{ fontWeight: 600 }}>{pick.code} · {pick.title}</Typography>
          </Box>
          {pick.reason && <Typography variant="body2">{pick.reason}</Typography>}
          <Box>
            <Button onClick={() => onStart(pick)}>{tr('Start review')}</Button>
          </Box>
        </Box>
      ))}
    </Box>
  );
}
