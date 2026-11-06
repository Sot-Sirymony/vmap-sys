import { useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Checkbox from '@mui/material/Checkbox';
import Chip from '@mui/material/Chip';
import Link from '@mui/material/Link';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { getAiStatus } from '../../api/aiApi';
import { getResourceIdeas } from '../../api/aiDailyApi';
import { addPlan, checkPlan, suggestGoals, suggestSteps, undoPlan } from '../../api/aiPlanApi';
import { useI18n } from '../../i18n/I18nContext';
import { tr } from '../../i18n/translate';
import type {
  AiStatus, Dream, Goal, GoalSuggestion, PlanAddResult, PlanCheck, ResourceIdea, StepSuggestion, VisionStep,
} from '../../types/vision';
import { Button } from '../common/Button';
import { Loading } from '../common/Loading';
import { safeHttpUrl } from '../../utils/safeUrl';
import { SuggestionRating, wasEdited } from './SuggestionRating';

type Props = {
  dream: Dream;
  goals: Goal[];
  steps: VisionStep[];
  token: string;
  onDataChange: () => Promise<void>;
};

type GoalRow = GoalSuggestion & { picked: boolean; original: GoalSuggestion };
type StepRow = StepSuggestion & { picked: boolean; original: StepSuggestion };
type Result =
  | { kind: 'goals'; rows: GoalRow[]; question?: string | null; requestId?: number | null }
  | { kind: 'steps'; goalId: number; rows: StepRow[]; question?: string | null; requestId?: number | null }
  | { kind: 'check'; check: PlanCheck }
  | { kind: 'resources'; ideas: ResourceIdea[] };

const ISSUE_LABELS: Record<string, string> = {
  MISSING_OWNER: 'No owner',
  MISSING_DATE: 'No date',
  MISSING_RESOURCE: 'No resources',
  NO_STEPS: 'No steps',
  AFTER_GOAL_DATE: 'After the goal date',
  OVERDUE: 'Overdue',
  VAGUE_STEP: 'Vague',
  UNREALISTIC_TIMING: 'Timing',
  GAP: 'Gap',
};

/**
 * V9 Phase 5: the AI goal builder (AI-FR-07), stretch goal (AI-FR-10), plan
 * builder (AI-FR-08) and plan check (AI-FR-09) for one dream. Suggestions can
 * be edited and are only saved when the participant adds them (AI-FR-04). The
 * plan check works without AI too, using its built-in rules.
 */
export function AiPlanAssistant({ dream, goals, steps, token, onDataChange }: Props) {
  const { language } = useI18n();
  const [status, setStatus] = useState<AiStatus | null>(null);
  const [goalId, setGoalId] = useState('');
  const [result, setResult] = useState<Result | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  // V11 (PB-FR-32): what the last add saved, so it can be undone.
  const [lastAdd, setLastAdd] = useState<(PlanAddResult & { requestId?: number | null }) | null>(null);

  const dreamGoals = goals.filter((goal) => goal.dreamId === dream.id && !goal.archived);

  useEffect(() => {
    getAiStatus(token).then(setStatus).catch(() => setStatus(null));
  }, [token]);

  async function run(action: () => Promise<Result>) {
    setBusy(true);
    setError('');
    setNotice('');
    setLastAdd(null);
    setResult(null);
    try {
      setResult(await action());
    } catch (runError) {
      setError(runError instanceof Error ? tr(runError.message) : tr('AI help is temporarily unavailable.'));
    } finally {
      setBusy(false);
      getAiStatus(token).then(setStatus).catch(() => undefined);
    }
  }

  const askGoals = (stretch: boolean) => run(async () => {
    const answer = await suggestGoals(token, dream.id, language, stretch);
    return {
      kind: 'goals',
      rows: answer.goals.map((goal) => ({ ...goal, picked: true, original: goal })),
      question: answer.clarifyingQuestion,
      requestId: answer.requestId,
    };
  });

  const askSteps = () => run(async () => {
    const id = Number(goalId);
    const answer = await suggestSteps(token, id, language);
    return {
      kind: 'steps',
      goalId: id,
      rows: answer.steps.map((step) => ({ ...step, picked: true, original: step })),
      question: answer.clarifyingQuestion,
      requestId: answer.requestId,
    };
  });

  const askCheck = () => run(async () => ({ kind: 'check', check: await checkPlan(token, dream.id, language) }));

  // AI-FR-13: ideas only, each to check before use until admins keep an approved list.
  const askResources = () => run(async () => ({ kind: 'resources', ideas: (await getResourceIdeas(token, Number(goalId), language)).resources }));

  // V11 (PB-FR-31): everything picked is saved in one request, or nothing is.
  async function addGoals(rows: GoalRow[], requestId?: number | null) {
    const picked = rows.filter((item) => item.picked);
    await save('Unable to save goal.', requestId, {
      goals: picked.map((row) => ({
        edited: wasEdited(row.original, row, ['title', 'successCriteria', 'targetDate']),
        goal: {
          dreamId: dream.id,
          title: row.title,
          description: row.description ?? undefined,
          successCriteria: row.successCriteria ?? undefined,
          priority: row.priority,
          targetDate: row.targetDate || undefined,
          status: 'NOT_STARTED',
          moonshot: row.moonshot,
          moonshotVision: row.moonshot ? row.reason ?? undefined : undefined,
          scheduleMode: dream.scheduleMode ?? 'BOTTOM_UP',
        },
      })),
    }, tr('{count} goal(s) added to your plan.', { count: picked.length }));
  }

  async function addSteps(forGoal: number, rows: StepRow[], requestId?: number | null) {
    const picked = rows.filter((item) => item.picked);
    const sequence = steps.filter((step) => step.goalId === forGoal && !step.archived).length;
    await save('Unable to save step.', requestId, {
      steps: picked.map((row, index) => ({
        edited: wasEdited(row.original, row, ['title', 'owner', 'targetDate', 'resources', 'people']),
        step: {
          goalId: forGoal,
          title: row.title,
          description: row.description ?? undefined,
          owner: row.owner,
          sequenceNumber: sequence + index + 1,
          complex: false,
          priority: 'MEDIUM',
          targetDate: row.targetDate ?? undefined,
          status: 'NOT_STARTED',
          resources: [row.resources, row.people ? `${tr('People')}: ${row.people}` : null].filter(Boolean).join('\n'),
        },
      })),
    }, tr('{count} step(s) added to your plan.', { count: picked.length }));
  }

  async function save(failure: string, requestId: number | null | undefined, body: Parameters<typeof addPlan>[1], done: string) {
    setBusy(true);
    setError('');
    try {
      const added = await addPlan(token, { ...body, requestId });
      setResult(null);
      setLastAdd({ ...added, requestId });
      setNotice(done);
      await onDataChange();
    } catch (addError) {
      setError(addError instanceof Error ? tr(addError.message) : tr(failure));
    } finally {
      setBusy(false);
    }
  }

  async function undo() {
    if (!lastAdd) {
      return;
    }
    setBusy(true);
    setError('');
    try {
      await undoPlan(token, lastAdd);
      setLastAdd(null);
      setNotice(tr('Undone. What was just added is archived.'));
      await onDataChange();
    } catch (undoError) {
      setError(undoError instanceof Error ? tr(undoError.message) : tr('Unable to undo.'));
    } finally {
      setBusy(false);
    }
  }

  const aiReady = status?.available === true;

  return (
    <Card>
      <CardHeader
        title={tr('Plan assistant')}
        subheader={tr('AI suggestions you can edit before adding. Nothing is saved until you add it.')}
      />
      <CardContent>
        <Stack spacing={1.5}>
          {status && !aiReady && (
            <Typography variant="body2" color="text.secondary">
              {status.reason === 'NO_CONSENT'
                ? tr('Turn on AI consent in Account settings to get suggestions. The plan check still works without it.')
                : tr('AI suggestions are unavailable right now. The plan check still works without them.')}
            </Typography>
          )}
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'center' }}>
            <Button disabled={busy || !aiReady} onClick={() => void askGoals(false)}>{tr('Suggest goals')}</Button>
            <Button variant="secondary" disabled={busy || !aiReady} onClick={() => void askGoals(true)}>{tr('Suggest a stretch goal')}</Button>
            <Button variant="secondary" disabled={busy} onClick={() => void askCheck()}>{tr('Check my plan')}</Button>
          </Box>
          {dreamGoals.length > 0 && (
            <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'center' }}>
              <Select
                size="small"
                displayEmpty
                value={goalId}
                onChange={(event) => setGoalId(event.target.value)}
                sx={{ minWidth: 220, maxWidth: '100%' }}
                SelectDisplayProps={{ 'aria-label': tr('Goal to plan') }}
              >
                <MenuItem value="">{tr('Choose a goal')}</MenuItem>
                {dreamGoals.map((goal) => <MenuItem key={goal.id} value={String(goal.id)}>{goal.title}</MenuItem>)}
              </Select>
              <Button variant="secondary" disabled={busy || !aiReady || !goalId} onClick={() => void askSteps()}>
                {tr('Suggest steps')}
              </Button>
              <Button variant="secondary" disabled={busy || !aiReady || !goalId} onClick={() => void askResources()}>
                {tr('Resource ideas')}
              </Button>
            </Box>
          )}
          {busy && (
            <Box>
              <Loading />
              <Typography variant="caption" color="text.secondary">{tr('This can take up to 30 seconds.')}</Typography>
            </Box>
          )}
          {error && <Alert severity="error">{error}</Alert>}
          {notice && (
            <Alert
              severity="success"
              action={lastAdd ? <Button variant="secondary" disabled={busy} onClick={() => void undo()}>{tr('Undo')}</Button> : undefined}
            >
              {notice}
            </Alert>
          )}
          {result?.kind === 'goals' && (
            <GoalRows result={result} busy={busy} onChange={setResult} onAdd={(rows) => void addGoals(rows, result.requestId)} />
          )}
          {result?.kind === 'steps' && (
            <StepRows result={result} busy={busy} onChange={setResult} onAdd={(rows) => void addSteps(result.goalId, rows, result.requestId)} />
          )}
          {result?.kind === 'check' && <CheckResult check={result.check} />}
          {result && result.kind !== 'resources' && (
            <SuggestionRating
              key={result.kind === 'check' ? result.check.requestId : result.requestId}
              token={token}
              requestId={result.kind === 'check' ? result.check.requestId : result.requestId}
            />
          )}
          {result?.kind === 'resources' && <ResourceList ideas={result.ideas} />}
        </Stack>
      </CardContent>
    </Card>
  );
}

function Question({ question }: { question?: string | null }) {
  if (!question) {
    return null;
  }
  return (
    <Alert severity="info">
      <Typography variant="body2" sx={{ fontWeight: 600 }}>{question}</Typography>
      <Typography variant="body2">{tr('Add the answer to your dream or goal, or talk it through with the Dream Coach, then ask again.')}</Typography>
    </Alert>
  );
}

function GoalRows({ result, busy, onChange, onAdd }: {
  result: Extract<Result, { kind: 'goals' }>;
  busy: boolean;
  onChange: (next: Result) => void;
  onAdd: (rows: GoalRow[]) => void;
}) {
  const update = (index: number, patch: Partial<GoalRow>) =>
    onChange({ ...result, rows: result.rows.map((row, rowIndex) => (rowIndex === index ? { ...row, ...patch } : row)) });
  const picked = result.rows.filter((row) => row.picked && row.title.trim()).length;
  return (
    <Stack spacing={1.5}>
      <Question question={result.question} />
      {result.rows.map((row, index) => (
        <Box key={index} sx={{ display: 'grid', gap: 1, p: 1.5, border: 1, borderColor: 'divider', borderRadius: 2 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
            <Checkbox checked={row.picked} onChange={(event) => update(index, { picked: event.target.checked })}
              slotProps={{ input: { 'aria-label': tr('Add {title}', { title: row.title }) } }} />
            <Chip size="small" label={tr('AI suggestion')} />
            {row.moonshot && <Chip size="small" color="secondary" label={tr('Stretch goal')} />}
          </Box>
          <TextField size="small" label={tr('Goal')} value={row.title} onChange={(event) => update(index, { title: event.target.value })} />
          <TextField size="small" label={tr('Success criteria')} value={row.successCriteria ?? ''} multiline
            onChange={(event) => update(index, { successCriteria: event.target.value })} />
          <TextField size="small" type="date" label={tr('Target date')} value={row.targetDate ?? ''}
            onChange={(event) => update(index, { targetDate: event.target.value })} slotProps={{ inputLabel: { shrink: true } }} />
          {row.reason && <Typography variant="caption" color="text.secondary">{row.reason}</Typography>}
        </Box>
      ))}
      {result.rows.length > 0 && (
        <Box>
          <Button disabled={busy || picked === 0} onClick={() => onAdd(result.rows.filter((row) => row.picked && row.title.trim()))}>
            {tr('Add {count} selected goal(s)', { count: picked })}
          </Button>
        </Box>
      )}
    </Stack>
  );
}

function StepRows({ result, busy, onChange, onAdd }: {
  result: Extract<Result, { kind: 'steps' }>;
  busy: boolean;
  onChange: (next: Result) => void;
  onAdd: (rows: StepRow[]) => void;
}) {
  const update = (index: number, patch: Partial<StepRow>) =>
    onChange({ ...result, rows: result.rows.map((row, rowIndex) => (rowIndex === index ? { ...row, ...patch } : row)) });
  const ready = result.rows.filter((row) => row.picked && row.title.trim() && row.owner.trim() && row.targetDate);
  const missingDate = result.rows.some((row) => row.picked && !row.targetDate);
  return (
    <Stack spacing={1.5}>
      <Question question={result.question} />
      {result.rows.map((row, index) => (
        <Box key={index} sx={{ display: 'grid', gap: 1, p: 1.5, border: 1, borderColor: 'divider', borderRadius: 2 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Checkbox checked={row.picked} onChange={(event) => update(index, { picked: event.target.checked })}
              slotProps={{ input: { 'aria-label': tr('Add {title}', { title: row.title }) } }} />
            <Chip size="small" label={tr('AI suggestion')} />
          </Box>
          <TextField size="small" label={tr('Step')} value={row.title} onChange={(event) => update(index, { title: event.target.value })} />
          <Box sx={{ display: 'grid', gap: 1, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
            <TextField size="small" label={tr('Owner')} value={row.owner} onChange={(event) => update(index, { owner: event.target.value })} />
            <TextField size="small" type="date" label={tr('Target date')} value={row.targetDate ?? ''}
              onChange={(event) => update(index, { targetDate: event.target.value })} slotProps={{ inputLabel: { shrink: true } }} />
          </Box>
          <TextField size="small" label={tr('Resources and people')} multiline
            value={[row.resources, row.people ? `${tr('People')}: ${row.people}` : null].filter(Boolean).join('\n')}
            onChange={(event) => update(index, { resources: event.target.value, people: null })} />
          {row.reason && <Typography variant="caption" color="text.secondary">{row.reason}</Typography>}
        </Box>
      ))}
      {missingDate && <Typography variant="caption" color="text.secondary">{tr('Pick a date for each step you want to add.')}</Typography>}
      {result.rows.length > 0 && (
        <Box>
          <Button disabled={busy || ready.length === 0 || missingDate} onClick={() => onAdd(ready)}>
            {tr('Add {count} selected step(s)', { count: ready.length })}
          </Button>
        </Box>
      )}
    </Stack>
  );
}

function CheckResult({ check }: { check: PlanCheck }) {
  return (
    <Stack spacing={1}>
      {!check.aiChecked && (
        <Typography variant="caption" color="text.secondary">
          {tr('Showing the built-in checks only. The AI review was not available.')}
        </Typography>
      )}
      {check.issues.length === 0 && <Alert severity="success">{tr('No problems found. Your plan looks ready.')}</Alert>}
      {check.issues.map((issue, index) => (
        <Box key={index} sx={{ borderLeft: 3, borderColor: issue.fromAi ? 'secondary.main' : 'warning.main', pl: 1.5 }}>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
            <Chip size="small" label={tr(ISSUE_LABELS[issue.kind] ?? issue.kind)} />
            {issue.fromAi && <Chip size="small" variant="outlined" label={tr('AI suggestion')} />}
            <Typography variant="body2" sx={{ fontWeight: 600 }}>
              {issue.itemType === 'GOAL' ? tr('Goal') : tr('Step')}: {issue.itemTitle}
            </Typography>
          </Box>
          <Typography variant="body2">{tr(issue.problem)}</Typography>
          {issue.fix && <Typography variant="body2" color="text.secondary">{tr(issue.fix)}</Typography>}
        </Box>
      ))}
    </Stack>
  );
}

const RESOURCE_KIND_LABELS: Record<string, string> = {
  COURSE: 'Course',
  BOOK: 'Book',
  TOOL: 'Tool',
  ORGANISATION: 'Organisation',
  FUNDING: 'Funding',
  PERSON: 'Person to ask',
  OTHER: 'Other',
};

function ResourceList({ ideas }: { ideas: ResourceIdea[] }) {
  if (ideas.length === 0) {
    return <Typography variant="body2" color="text.secondary">{tr('No resource ideas this time.')}</Typography>;
  }
  return (
    <Stack spacing={1}>
      {ideas.some((idea) => idea.checkBeforeUse) && (
        <Alert severity="warning">{tr('AI ideas, not recommendations. Check each one yourself before you rely on it or pay for it.')}</Alert>
      )}
      {ideas.map((idea, index) => (
        <Box key={index} sx={{ borderLeft: 3, borderColor: 'divider', pl: 1.5 }}>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
            <Chip size="small" label={tr(RESOURCE_KIND_LABELS[idea.kind] ?? 'Other')} />
            {safeHttpUrl(idea.url)
              ? <Link href={safeHttpUrl(idea.url)} target="_blank" rel="noopener noreferrer" sx={{ fontWeight: 600 }}>{idea.name}</Link>
              : <Typography variant="body2" sx={{ fontWeight: 600 }}>{idea.name}</Typography>}
            {idea.checkBeforeUse
              ? <Chip size="small" variant="outlined" color="warning" label={tr('Check before use')} />
              : <Chip size="small" color="success" label={tr('Approved by the program')} />}
          </Box>
          {idea.why && <Typography variant="body2" color="text.secondary">{idea.why}</Typography>}
        </Box>
      ))}
    </Stack>
  );
}
