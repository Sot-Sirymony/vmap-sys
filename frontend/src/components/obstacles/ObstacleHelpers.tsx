import { useEffect, useState } from 'react';
import { aiOnFor } from '../ai/aiFocus';
import { useNavigate } from 'react-router';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { getAiStatus } from '../../api/aiApi';
import { recordPageOutcome } from '../../api/aiPageApi';
import { getPartnerMatches, obstacleHelp } from '../../api/obstacleHelpApi';
import { useI18n } from '../../i18n/I18nContext';
import { tr } from '../../i18n/translate';
import type {
  ObstacleHelpAnswer, ObstacleHelpMode, ObstacleType, PageChange, PartnerMatches, Priority, TaskProposal, WhyAnswer,
} from '../../types/vision';
import { suggestPartnerFor } from '../../utils/partnerSuggestion';
import { localDateIso } from '../../utils/localDate';
import { Button } from '../common/Button';
import { SuggestionRating } from '../ai/SuggestionRating';
import { PageChangeCard } from '../ai/page-assistant/PageChangeCard';
import { OBSTACLE_FIELD_LABELS } from '../ai/page-assistant/pageHelp';

const PRIORITIES: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const PRIORITY_LABELS: Record<Priority, string> = { LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High', CRITICAL: 'Critical' };

type Props = {
  token: string;
  /** The saved obstacle being edited, or null for a new one. */
  obstacleId: number | null;
  obstacleType: ObstacleType;
  title: string;
  description: string;
  solution: string;
  requiredPartnerId: string;
  /** The unsaved form, as the page assistant reads it. */
  read: () => Record<string, string>;
  /** Fills one form field; the page's own Save stores it. */
  apply: (field: string, value: string) => void;
  onSetRequiredPartner: (partnerId: string) => void;
  /** The partner's name, for the Ask for help button. */
  partnerName?: string | null;
};

/**
 * V15 Phase 2 (AH-FR-31 to AH-FR-37, AH-FR-42): helpers in the obstacle form.
 * Who could help and partner matching need no AI; classify, root cause,
 * solutions and the next task need it and are hidden when it is off. Every
 * suggestion only fills the form or opens another normal form; nothing is
 * saved here, and no helper marks the obstacle Resolved.
 */
export function ObstacleHelpers({
  token, obstacleId, obstacleType, title, description, solution, requiredPartnerId, read, apply, onSetRequiredPartner, partnerName,
}: Readonly<Props>) {
  const { language } = useI18n();
  const navigate = useNavigate();
  const [aiOn, setAiOn] = useState(false);
  const [busy, setBusy] = useState<ObstacleHelpMode | null>(null);
  const [error, setError] = useState('');
  const [answer, setAnswer] = useState<ObstacleHelpAnswer | null>(null);
  const [cards, setCards] = useState<(PageChange & { id: number })[]>([]);
  const [applied, setApplied] = useState('');
  const [whyAnswers, setWhyAnswers] = useState<WhyAnswer[]>([]);
  const [whyText, setWhyText] = useState('');
  const [task, setTask] = useState<TaskProposal | null>(null);
  const [matches, setMatches] = useState<PartnerMatches | null>(null);
  const [matchError, setMatchError] = useState('');
  const suggestion = suggestPartnerFor(obstacleType);

  useEffect(() => {
    getAiStatus(token).then((status) => setAiOn(aiOnFor(status, 'OBSTACLES'))).catch(() => setAiOn(false));
  }, [token]);

  // A new type means a different kind of partner.
  useEffect(() => {
    setMatches(null);
  }, [obstacleType]);

  async function run(mode: ObstacleHelpMode, answers: WhyAnswer[] = []) {
    setBusy(mode);
    setError('');
    setApplied('');
    if (mode !== 'ROOT_CAUSE') {
      setWhyAnswers([]);
    }
    try {
      const next = await obstacleHelp(token, language, { mode, obstacleId, form: read(), answers });
      setAnswer(next);
      setCards(next.changes.map((change, index) => ({ ...change, id: index })));
      setTask(next.task);
      if (mode === 'ROOT_CAUSE' && next.changes.length > 0) {
        setWhyAnswers([]);
      }
    } catch (helpError) {
      setError(helpError instanceof Error ? tr(helpError.message) : tr('AI help is temporarily unavailable.'));
    } finally {
      setBusy(null);
    }
  }

  function startRootCause() {
    setWhyAnswers([]);
    setWhyText('');
    void run('ROOT_CAUSE', []);
  }

  function answerWhy() {
    if (!answer?.question || !whyText.trim()) {
      return;
    }
    const next = [...whyAnswers, { question: answer.question, answer: whyText.trim() }];
    setWhyAnswers(next);
    setWhyText('');
    void run('ROOT_CAUSE', next);
  }

  function count(edited: boolean) {
    if (answer?.requestId) {
      // Counts only; a failure here must not undo what the participant chose.
      recordPageOutcome(token, answer.requestId, edited ? 0 : 1, edited ? 1 : 0).catch(() => undefined);
    }
  }

  function applyCard(card: PageChange & { id: number }, value: string, edited: boolean) {
    apply(card.field, value);
    count(edited);
    setApplied(tr('Added to the form. Nothing is saved until you press Save.'));
    setCards((current) => current.filter((item) => item.id !== card.id));
  }

  async function findMatches() {
    setMatchError('');
    try {
      setMatches(await getPartnerMatches(token, obstacleType));
    } catch (loadError) {
      setMatchError(loadError instanceof Error ? tr(loadError.message) : tr('Unable to load your partners.'));
    }
  }

  function openTaskForm() {
    if (!task) {
      return;
    }
    const edited = task.title !== answer?.task?.title || task.dueDate !== answer?.task?.dueDate || task.priority !== answer?.task?.priority
      || task.description !== answer?.task?.description;
    count(edited);
    // AH-FR-37: opens the normal task form, filled; nothing is created until it is saved there.
    const params = new URLSearchParams({ create: 'task', title: task.title, owner: task.owner, priority: task.priority });
    if (task.stepId) {
      params.set('parent', String(task.stepId));
    }
    if (task.dueDate) {
      params.set('dueDate', task.dueDate);
    }
    if (task.description) {
      params.set('description', task.description);
    }
    navigate(`/tasks?${params.toString()}`);
  }

  function draftMessage() {
    // AH-FR-36: opens the Communication page with a new message filled from the obstacle.
    const params = new URLSearchParams({
      create: 'message',
      partnerId: requiredPartnerId,
      purpose: tr('Help with: {title}', { title }),
      problem: description,
      request: solution,
    });
    if (partnerName) {
      params.set('audience', partnerName);
    }
    navigate(`/communication?${params.toString()}`);
  }

  function addPartner() {
    const params = new URLSearchParams({ create: 'partner', supportType: matches?.suggestedSupportType ?? 'OTHER', status: 'TO_CONTACT' });
    navigate(`/partners?${params.toString()}`);
  }

  return (
    <Box className="field-full coaching-panel" component="section" aria-label={tr('Obstacle helpers')}>
      <Typography variant="subtitle2" component="h3">{tr('Obstacle helpers')}</Typography>

      <Box sx={{ mt: 1 }}>
        <Typography variant="body2">
          {suggestion
            ? tr('Who could help: {partner}', { partner: suggestion.label })
            : tr('Who could help: think of someone who has faced this kind of problem before.')}
        </Typography>
        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mt: 1 }}>
          <Button variant="secondary" onClick={() => void findMatches()}>{tr('Match my partners')}</Button>
          {requiredPartnerId && (
            <Button variant="secondary" onClick={draftMessage}>{tr('Draft a message to them')}</Button>
          )}
        </Box>
        {requiredPartnerId && (
          <Typography variant="caption" color="text.secondary">{tr('Save the obstacle first if you changed it, then draft the message.')}</Typography>
        )}
        {matchError && <Alert severity="error" sx={{ mt: 1 }}>{matchError}</Alert>}
        {matches && matches.partners.length > 0 && (
          <Box component="ul" sx={{ m: 0, mt: 1, pl: 2.5 }}>
            {matches.partners.map((partner) => (
              <li key={partner.id}>
                <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                  <Typography variant="body2">{partner.name}{partner.role ? ` – ${partner.role}` : ''}</Typography>
                  {String(partner.id) === requiredPartnerId ? (
                    <Chip size="small" label={tr('Required partner')} />
                  ) : (
                    <Button variant="secondary" onClick={() => onSetRequiredPartner(String(partner.id))}>{tr('Set as required partner')}</Button>
                  )}
                </Box>
              </li>
            ))}
          </Box>
        )}
        {matches && matches.partners.length === 0 && (
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap', mt: 1 }}>
            <Typography variant="body2" color="text.secondary">{tr('None of your partners fits this yet.')}</Typography>
            <Button variant="secondary" onClick={addPartner}>{tr('Add a partner')}</Button>
          </Box>
        )}
      </Box>

      {aiOn && (
        <Box sx={{ mt: 2 }}>
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
            <Button variant="secondary" disabled={busy !== null} onClick={() => void run('CLASSIFY')}>{tr('Suggest type and severity')}</Button>
            <Button variant="secondary" disabled={busy !== null} onClick={startRootCause}>{tr('Find the root cause')}</Button>
            <Button variant="secondary" disabled={busy !== null} onClick={() => void run('SOLUTIONS')}>{tr('What could I try?')}</Button>
            <Button variant="secondary" disabled={busy !== null} onClick={() => void run('NEXT_TASK')}>{tr('Turn this into a task')}</Button>
          </Box>
          {busy && <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>{tr('The Dream Coach is thinking…')}</Typography>}
          {error && <Alert severity="error" sx={{ mt: 1 }}>{error}</Alert>}
          <Box aria-live="polite" sx={{ mt: 1 }}>
            {applied && <Alert severity="success" sx={{ mb: 1 }}>{applied}</Alert>}

            {answer?.mode === 'ROOT_CAUSE' && answer.question && (
              <Stack spacing={1}>
                {whyAnswers.map((item) => (
                  <Typography key={item.question} variant="body2" color="text.secondary">{item.question} – {item.answer}</Typography>
                ))}
                <Chip size="small" label={tr('AI suggestion')} sx={{ alignSelf: 'flex-start' }} />
                <TextField size="small" label={answer.question} value={whyText} multiline
                  onChange={(event) => setWhyText(event.target.value)} slotProps={{ htmlInput: { style: { fontSize: 16 } } }} />
                <Typography variant="caption" color="text.secondary">
                  {tr('Question {number} of {total}', { number: whyAnswers.length + 1, total: 3 })}
                </Typography>
                <Box>
                  <Button disabled={busy !== null || !whyText.trim()} onClick={answerWhy}>{tr('Answer')}</Button>
                </Box>
              </Stack>
            )}

            {cards.length > 0 && (
              <Stack spacing={1}>
                {cards.map((card) => (
                  <PageChangeCard key={card.id} change={card} label={tr(OBSTACLE_FIELD_LABELS[card.field] ?? card.field)}
                    onApply={(value, edited) => applyCard(card, value, edited)}
                    onDismiss={() => setCards((current) => current.filter((item) => item.id !== card.id))} />
                ))}
              </Stack>
            )}

            {answer?.mode === 'NEXT_TASK' && task && (
              <Card variant="outlined" component="section" aria-label={tr('Suggested task')}>
                <CardContent sx={{ display: 'grid', gap: 1 }}>
                  <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
                    <Chip size="small" label={tr('AI suggestion')} />
                    <Typography variant="subtitle2" component="h4">{tr('Suggested task')}</Typography>
                  </Box>
                  {answer.note && <Typography variant="caption" color="text.secondary">{answer.note}</Typography>}
                  <TextField size="small" label={tr('Title')} value={task.title} onChange={(event) => setTask({ ...task, title: event.target.value })} />
                  <TextField size="small" label={tr('Description')} multiline value={task.description}
                    onChange={(event) => setTask({ ...task, description: event.target.value })} />
                  <TextField size="small" label={tr('Owner')} value={task.owner} disabled />
                  <TextField size="small" type="date" label={tr('Due date')} value={task.dueDate ?? ''}
                    onChange={(event) => setTask({ ...task, dueDate: event.target.value || null })}
                    slotProps={{ inputLabel: { shrink: true }, htmlInput: { min: localDateIso(), max: task.latestDate ?? undefined } }}
                    helperText={task.latestDate ? tr('On or before {date}, the step\'s date', { date: task.latestDate }) : tr('Pick a date')} />
                  <TextField select size="small" label={tr('Priority')} value={task.priority}
                    onChange={(event) => setTask({ ...task, priority: event.target.value as Priority })}>
                    {PRIORITIES.map((item) => <MenuItem key={item} value={item}>{tr(PRIORITY_LABELS[item])}</MenuItem>)}
                  </TextField>
                  <Typography variant="caption" color="text.secondary">
                    {task.stepTitle ? tr('Under step: {title}', { title: task.stepTitle }) : tr('Link a step to this obstacle, or pick one in the task form.')}
                  </Typography>
                  <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                    <Button disabled={!task.title.trim()} onClick={openTaskForm}>{tr('Open the task form')}</Button>
                    <Button variant="secondary" onClick={() => setTask(null)}>{tr('Dismiss')}</Button>
                  </Box>
                </CardContent>
              </Card>
            )}

            {answer && answer.mode !== 'ROOT_CAUSE' && cards.length === 0 && !task && !applied && (
              <Typography variant="body2" color="text.secondary">{tr('No changes to suggest here.')}</Typography>
            )}
            {answer && !answer.question && <SuggestionRating token={token} requestId={answer.requestId} />}
          </Box>
        </Box>
      )}
    </Box>
  );
}
