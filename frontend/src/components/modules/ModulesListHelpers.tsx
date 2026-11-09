import { useEffect, useState } from 'react';
import { aiOnFor } from '../ai/aiFocus';
import { useNavigate } from 'react-router';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Typography from '@mui/material/Typography';
import { getAiStatus } from '../../api/aiApi';
import { getModuleJourney, getNextModuleByRule, suggestNextModule } from '../../api/aiModuleApi';
import { useI18n } from '../../i18n/I18nContext';
import { tr } from '../../i18n/translate';
import type { ModuleJourney, ModuleNext, ModuleStage } from '../../types/vision';
import { Button } from '../common/Button';
import { SuggestionRating } from '../ai/SuggestionRating';

const STAGE_LABELS: Record<ModuleStage, string> = { BREAK_CHAINS: 'Break barriers', BOOST: 'Build momentum', LAUNCH: 'Launch' };

/**
 * V15 (AH-FR-13, AH-FR-14): which module to do next. With AI, Claude picks
 * from the open modules and says why from the participant's own data; the
 * server never shows a pick that is locked or done. Without AI, the program's
 * rule answers and names itself.
 */
export function ModuleNextCard({ token }: Readonly<{ token: string }>) {
  const { language } = useI18n();
  const navigate = useNavigate();
  const [aiOn, setAiOn] = useState(false);
  const [next, setNext] = useState<ModuleNext | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    getAiStatus(token).then((status) => setAiOn(aiOnFor(status, 'MODULES'))).catch(() => setAiOn(false));
  }, [token]);

  async function ask() {
    setBusy(true);
    setError('');
    try {
      if (aiOn) {
        try {
          setNext(await suggestNextModule(token, language));
          return;
        } catch {
          // Fall back to the rule below.
        }
      }
      setNext(await getNextModuleByRule(token, language));
    } catch (askError) {
      setError(askError instanceof Error ? tr(askError.message) : tr('Unable to find your next module.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Box component="section" aria-label={tr('Which module next')}>
      <Button variant="secondary" disabled={busy} onClick={() => void ask()}>
        {busy ? tr('The Dream Coach is thinking…') : tr('Which module should I do next?')}
      </Button>
      {error && <Alert severity="error" sx={{ mt: 1 }}>{error}</Alert>}
      {next && (
        <Card variant="outlined" sx={{ mt: 1 }} aria-live="polite">
          <CardContent sx={{ display: 'grid', gap: 1 }}>
            {next.source === 'AI' && <Chip size="small" label={tr('AI suggestion')} sx={{ justifySelf: 'start' }} />}
            {next.moduleNumber !== null && <Typography sx={{ fontWeight: 600 }}>{next.title}</Typography>}
            <Typography variant="body2">{next.source === 'AI' ? next.reason : tr(next.reason)}</Typography>
            {next.moduleNumber !== null && (
              <Box>
                <Button onClick={() => navigate(`/modules/${next.moduleNumber}`)}>{tr('Open this module')}</Button>
              </Box>
            )}
            <SuggestionRating token={token} requestId={next.requestId} />
          </CardContent>
        </Card>
      )}
    </Box>
  );
}

/** V15 (AH-FR-15): how far the participant has come. A summary of their own data, with no AI. */
export function JourneyCard({ token }: Readonly<{ token: string }>) {
  const { language } = useI18n();
  const [journey, setJourney] = useState<ModuleJourney | null>(null);
  const [error, setError] = useState('');

  async function load() {
    setError('');
    try {
      setJourney(await getModuleJourney(token, language));
    } catch (loadError) {
      setError(loadError instanceof Error ? tr(loadError.message) : tr('Unable to load your journey.'));
    }
  }

  const changes = journey?.chains.filter((chain) => chain.latest !== chain.first) ?? [];

  return (
    <Box component="section" aria-label={tr('How far have I come')}>
      <Button variant="secondary" onClick={() => void load()}>{tr('How far have I come?')}</Button>
      {error && <Alert severity="error" sx={{ mt: 1 }}>{error}</Alert>}
      {journey && (
        <Card variant="outlined" sx={{ mt: 1 }} aria-live="polite">
          <CardContent sx={{ display: 'grid', gap: 1 }}>
            <Typography variant="body2" sx={{ fontWeight: 600 }}>
              {tr('{done} of {total} modules done', { done: journey.done, total: journey.total })}
            </Typography>
            <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
              {journey.stages.map((stage) => (
                <li key={stage.stage}>
                  <Typography variant="body2">
                    {tr('{stage}: {done} of {total}', { stage: tr(STAGE_LABELS[stage.stage]), done: stage.done, total: stage.total })}
                  </Typography>
                </li>
              ))}
            </Box>
            {journey.assessments < 2 && (
              <Typography variant="body2" color="text.secondary">
                {journey.assessments === 0
                  ? tr('Take the assessment to see how your barriers change.')
                  : tr('Retake the assessment later to see how your barriers change.')}
              </Typography>
            )}
            {journey.assessments >= 2 && changes.length === 0 && (
              <Typography variant="body2">{tr('Your barrier scores are the same as at your first assessment.')}</Typography>
            )}
            {changes.length > 0 && (
              <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
                {changes.map((chain) => (
                  <li key={chain.key}>
                    <Typography variant="body2">
                      {tr('{label}: {first} to {latest} of 5', { label: tr(chain.label), first: chain.first, latest: chain.latest })}
                    </Typography>
                  </li>
                ))}
              </Box>
            )}
            <Typography variant="body2">
              {journey.nextStage
                ? tr('Next stage to finish: {stage}', { stage: tr(STAGE_LABELS[journey.nextStage]) })
                : tr('You have finished every stage.')}
            </Typography>
          </CardContent>
        </Card>
      )}
    </Box>
  );
}
