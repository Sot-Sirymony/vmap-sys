import { useEffect, useState } from 'react';
import { aiOnFor } from '../ai/aiFocus';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Typography from '@mui/material/Typography';
import { getAiStatus } from '../../api/aiApi';
import { getBlockedWithoutObstacle, getObstaclePriority, obstacleHelp } from '../../api/obstacleHelpApi';
import { useI18n } from '../../i18n/I18nContext';
import { tr } from '../../i18n/translate';
import type { BlockedTask, RankedObstacle } from '../../types/vision';
import { Button } from '../common/Button';
import { SuggestionRating } from '../ai/SuggestionRating';

/**
 * V15 (AH-FR-39): Blocked tasks with a blocker reason and no obstacle. A rule,
 * no AI. Make this an obstacle opens the normal obstacle form, filled; nothing
 * is saved until the participant saves it. reloadKey reloads the list after a save.
 */
export function BlockedTasksCard({ token, reloadKey, onMakeObstacle }: Readonly<{
  token: string;
  reloadKey: number;
  onMakeObstacle: (task: BlockedTask) => void;
}>) {
  const [tasks, setTasks] = useState<BlockedTask[]>([]);

  useEffect(() => {
    getBlockedWithoutObstacle(token).then(setTasks).catch(() => setTasks([]));
  }, [token, reloadKey]);

  if (tasks.length === 0) {
    return null;
  }
  return (
    <Card component="section" aria-label={tr('Blocked tasks without an obstacle')} sx={{ mb: 2 }}>
      <CardContent>
        <Typography variant="subtitle2" component="h2">{tr('Blocked tasks without an obstacle')}</Typography>
        <Typography variant="body2" color="text.secondary">
          {tr('Recording what blocks them helps you find a solution and the right person to ask.')}
        </Typography>
        <Box component="ul" sx={{ m: 0, mt: 1, pl: 2.5 }}>
          {tasks.map((task) => (
            <li key={task.id}>
              <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                <Typography variant="body2">
                  <strong>{task.title}</strong>{` – ${task.blockerReason}`}
                </Typography>
                <Button variant="secondary" onClick={() => onMakeObstacle(task)}>{tr('Make this an obstacle')}</Button>
              </Box>
            </li>
          ))}
        </Box>
      </CardContent>
    </Card>
  );
}

const SEVERITY_LABELS: Record<string, string> = { LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High', CRITICAL: 'Critical' };
const SHOWN = 5;

/**
 * V15 (AH-FR-41): which open obstacle to tackle first. The order is a rule:
 * severity, then the Blocked tasks it holds up, then how long it has been
 * open. With AI, the top three also get a reason.
 */
export function ObstaclePriorityCard({ token }: Readonly<{ token: string }>) {
  const { language } = useI18n();
  const [aiOn, setAiOn] = useState(false);
  const [ranking, setRanking] = useState<RankedObstacle[] | null>(null);
  const [requestId, setRequestId] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    getAiStatus(token).then((status) => setAiOn(aiOnFor(status, 'OBSTACLES'))).catch(() => setAiOn(false));
  }, [token]);

  async function rank() {
    setBusy(true);
    setError('');
    try {
      if (aiOn) {
        try {
          const answer = await obstacleHelp(token, language, { mode: 'PRIORITISE' });
          setRanking(answer.ranking);
          setRequestId(answer.requestId);
          return;
        } catch {
          // Fall back to the rule order below, so the participant still gets an answer.
        }
      }
      setRanking(await getObstaclePriority(token));
      setRequestId(null);
    } catch (rankError) {
      setError(rankError instanceof Error ? tr(rankError.message) : tr('Unable to sort your obstacles.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Box component="section" aria-label={tr('Which obstacle first')} sx={{ mb: 2 }}>
      <Button variant="secondary" disabled={busy} onClick={() => void rank()}>
        {busy ? tr('Sorting…') : tr('Which obstacle should I tackle first?')}
      </Button>
      {error && <Alert severity="error" sx={{ mt: 1 }}>{error}</Alert>}
      {ranking && (
        <Box aria-live="polite" sx={{ mt: 1 }}>
          {ranking.length === 0 && <Typography variant="body2" color="text.secondary">{tr('You have no open obstacles.')}</Typography>}
          {ranking.length > 0 && (
            <Box component="ol" sx={{ m: 0, pl: 2.5 }}>
              {ranking.slice(0, SHOWN).map((item) => (
                <li key={item.id}>
                  <Typography variant="body2">
                    <strong>{item.title}</strong>
                    {` – ${tr(SEVERITY_LABELS[item.severity ?? ''] ?? '')}, `}
                    {tr('holds up {count} blocked task(s), open {days} day(s)', { count: item.blockedTasks, days: item.daysOpen })}
                  </Typography>
                  {item.reason && (
                    <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
                      <Chip size="small" label={tr('AI suggestion')} />
                      <Typography variant="body2" color="text.secondary">{item.reason}</Typography>
                    </Box>
                  )}
                </li>
              ))}
            </Box>
          )}
          {!requestId && ranking.length > 0 && (
            <Typography variant="caption" color="text.secondary">
              {tr('Sorted by severity, then blocked tasks held up, then how long each has been open.')}
            </Typography>
          )}
          <SuggestionRating token={token} requestId={requestId} />
        </Box>
      )}
    </Box>
  );
}
