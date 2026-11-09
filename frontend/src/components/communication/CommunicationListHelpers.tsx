import { useEffect, useState } from 'react';
import { aiOnFor } from '../ai/aiFocus';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Typography from '@mui/material/Typography';
import { getAiStatus } from '../../api/aiApi';
import { recordPageOutcome } from '../../api/aiPageApi';
import { communicationHelp, getDraftPriority, getFollowUpDue } from '../../api/communicationHelpApi';
import { useI18n } from '../../i18n/I18nContext';
import { tr } from '../../i18n/translate';
import type { FollowUpDue, NewMessageDraft, RankedMessage } from '../../types/vision';
import { followUpTemplate } from '../../utils/messageTemplates';
import { Button } from '../common/Button';
import { SuggestionRating } from '../ai/SuggestionRating';

function useAiOn(token: string) {
  const [aiOn, setAiOn] = useState(false);
  useEffect(() => {
    getAiStatus(token).then((status) => setAiOn(aiOnFor(status, 'COMMUNICATION'))).catch(() => setAiOn(false));
  }, [token]);
  return aiOn;
}

/**
 * V15 (AH-FR-48 to AH-FR-50): Sent or Followed Up messages past their
 * follow-up date with no reply. The list is a rule, with no AI. Draft a
 * follow-up uses Claude when AI is on and the program's template when it is
 * off; either way it opens a new message to edit and save.
 */
export function FollowUpDueCard({ token, reloadKey, onNewMessage }: Readonly<{
  token: string;
  reloadKey: number;
  onNewMessage: (draft: NewMessageDraft) => void;
}>) {
  const { language } = useI18n();
  const aiOn = useAiOn(token);
  const [due, setDue] = useState<FollowUpDue[]>([]);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    getFollowUpDue(token).then(setDue).catch(() => setDue([]));
  }, [token, reloadKey]);

  async function draft(item: FollowUpDue) {
    setError('');
    if (!aiOn) {
      onNewMessage({
        partnerId: item.partnerId,
        subject: tr('Following up: {subject}', { subject: item.subject ?? '' }),
        request: item.request ?? '',
        messageBody: followUpTemplate(item.partnerName ?? '', item.subject ?? '', item.request ?? ''),
        relatedDreamId: null,
        relatedGoalId: null,
        relatedTaskId: null,
      });
      return;
    }
    setBusyId(item.id);
    try {
      const answer = await communicationHelp(token, language, { mode: 'FOLLOW_UP', messageId: item.id });
      if (answer.message) {
        if (answer.requestId) {
          recordPageOutcome(token, answer.requestId, 1, 0).catch(() => undefined);
        }
        onNewMessage(answer.message);
      }
    } catch (helpError) {
      setError(helpError instanceof Error ? tr(helpError.message) : tr('AI help is temporarily unavailable.'));
    } finally {
      setBusyId(null);
    }
  }

  if (due.length === 0) {
    return null;
  }
  return (
    <Card component="section" aria-label={tr('Messages to follow up')} sx={{ mb: 2 }}>
      <CardContent>
        <Typography variant="subtitle2" component="h2">{tr('Messages to follow up')}</Typography>
        <Typography variant="body2" color="text.secondary">{tr('These are past their follow-up date with no reply yet.')}</Typography>
        {error && <Alert severity="error" sx={{ mt: 1 }}>{error}</Alert>}
        <Box component="ul" sx={{ m: 0, mt: 1, pl: 2.5 }}>
          {due.map((item) => (
            <li key={item.id}>
              <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                <Typography variant="body2">
                  <strong>{item.subject || tr('(no subject)')}</strong>
                  {item.partnerName ? ` – ${item.partnerName}` : ''}
                  {`, ${tr('{days} day(s) overdue', { days: item.daysOverdue })}`}
                </Typography>
                <Button variant="secondary" disabled={busyId !== null} onClick={() => void draft(item)}>
                  {busyId === item.id ? tr('The Dream Coach is thinking…') : tr('Draft a follow-up')}
                </Button>
              </Box>
            </li>
          ))}
        </Box>
      </CardContent>
    </Card>
  );
}

const SHOWN = 5;

/**
 * V15 (AH-FR-66): which draft to send first. The order is a rule: a blocked
 * linked task first, then the linked task's due date, then how long the draft
 * has waited. With AI, the top three also get a reason.
 */
export function DraftPriorityCard({ token }: Readonly<{ token: string }>) {
  const { language } = useI18n();
  const aiOn = useAiOn(token);
  const [ranking, setRanking] = useState<RankedMessage[] | null>(null);
  const [requestId, setRequestId] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function rank() {
    setBusy(true);
    setError('');
    try {
      if (aiOn) {
        try {
          const answer = await communicationHelp(token, language, { mode: 'PRIORITISE' });
          setRanking(answer.ranking);
          setRequestId(answer.requestId);
          return;
        } catch {
          // Fall back to the rule order below.
        }
      }
      setRanking(await getDraftPriority(token));
      setRequestId(null);
    } catch (rankError) {
      setError(rankError instanceof Error ? tr(rankError.message) : tr('Unable to sort your messages.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Box component="section" aria-label={tr('Which message first')} sx={{ mb: 2 }}>
      <Button variant="secondary" disabled={busy} onClick={() => void rank()}>
        {busy ? tr('Sorting…') : tr('Which message should I send first?')}
      </Button>
      {error && <Alert severity="error" sx={{ mt: 1 }}>{error}</Alert>}
      {ranking && (
        <Box aria-live="polite" sx={{ mt: 1 }}>
          {ranking.length === 0 && <Typography variant="body2" color="text.secondary">{tr('You have no draft messages.')}</Typography>}
          {ranking.length > 0 && (
            <Box component="ol" sx={{ m: 0, pl: 2.5 }}>
              {ranking.slice(0, SHOWN).map((item) => (
                <li key={item.id}>
                  <Typography variant="body2">
                    <strong>{item.subject || tr('(no subject)')}</strong>
                    {item.partnerName ? ` – ${item.partnerName}` : ''}
                    {item.taskBlocked ? `, ${tr('its task is blocked')}` : ''}
                    {item.taskDueDate ? `, ${tr('task due {date}', { date: item.taskDueDate })}` : ''}
                    {`, ${tr('waiting {days} day(s)', { days: item.daysWaiting })}`}
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
              {tr('Sorted with blocked tasks first, then by task due date, then by how long each draft has waited.')}
            </Typography>
          )}
          <SuggestionRating token={token} requestId={requestId} />
        </Box>
      )}
    </Box>
  );
}
