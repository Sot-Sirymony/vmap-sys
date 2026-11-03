import { useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Checkbox from '@mui/material/Checkbox';
import Chip from '@mui/material/Chip';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { getAiStatus } from '../../api/aiApi';
import { checkPlan, suggestGoals, suggestSteps } from '../../api/aiPlanApi';
import { createGoal } from '../../api/goalApi';
import { createStep } from '../../api/stepApi';
import { useI18n } from '../../i18n/I18nContext';
import { tr } from '../../i18n/translate';
import type { AiStatus, Dream, Goal, GoalSuggestion, PlanCheck, StepSuggestion, VisionStep } from '../../types/vision';
import { Button } from '../common/Button';
import { Loading } from '../common/Loading';

type Props = {
  dream: Dream;
  goals: Goal[];
  steps: VisionStep[];
  token: string;
  onDataChange: () => Promise<void>;
};

type GoalRow = GoalSuggestion & { picked: boolean };
type StepRow = StepSuggestion & { picked: boolean };
type Result =
  | { kind: 'goals'; rows: GoalRow[]; question?: string | null }
  | { kind: 'steps'; goalId: number; rows: StepRow[]; question?: string | null }
  | { kind: 'check'; check: PlanCheck };

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

  const dreamGoals = goals.filter((goal) => goal.dreamId === dream.id && !goal.archived);

  useEffect(() => {
    getAiStatus(token).then(setStatus).catch(() => setStatus(null));
  }, [token]);

  async function run(action: () => Promise<Result>) {
    setBusy(true);
    setError('');
    setNotice('');
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
    return { kind: 'goals', rows: answer.goals.map((goal) => ({ ...goal, picked: true })), question: answer.clarifyingQuestion };
  });

  const askSteps = () => run(async () => {
    const id = Number(goalId);
    const answer = await suggestSteps(token, id, language);
    return { kind: 'steps', goalId: id, rows: answer.steps.map((step) => ({ ...step, picked: true })), question: answer.clarifyingQuestion };
  });

  const askCheck = () => run(async () => ({ kind: 'check', check: await checkPlan(token, dream.id, language) }));

  async function addGoals(rows: GoalRow[]) {
    setBusy(true);
    setError('');
    try {
      for (const row of rows.filter((item) => item.picked)) {
        await createGoal(token, {
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
        });
      }
      const count = rows.filter((item) => item.picked).length;
      setResult(null);
      setNotice(tr('{count} goal(s) added to your plan.', { count }));
      await onDataChange();
    } catch (addError) {
      setError(addError instanceof Error ? tr(addError.message) : tr('Unable to save goal.'));
    } finally {
      setBusy(false);
    }
  }

  async function addSteps(forGoal: number, rows: StepRow[]) {
    setBusy(true);
    setError('');
    try {
      let sequence = steps.filter((step) => step.goalId === forGoal && !step.archived).length;
      for (const row of rows.filter((item) => item.picked)) {
        sequence += 1;
        await createStep(token, {
          goalId: forGoal,
          title: row.title,
          description: row.description ?? undefined,
          owner: row.owner,
          sequenceNumber: sequence,
          complex: false,
          priority: 'MEDIUM',
          targetDate: row.targetDate ?? undefined,
          status: 'NOT_STARTED',
          resources: [row.resources, row.people ? `${tr('People')}: ${row.people}` : null].filter(Boolean).join('\n'),
        });
      }
      const count = rows.filter((item) => item.picked).length;
      setResult(null);
      setNotice(tr('{count} step(s) added to your plan.', { count }));
      await onDataChange();
    } catch (addError) {
      setError(addError instanceof Error ? tr(addError.message) : tr('Unable to save step.'));
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
            </Box>
          )}
          {busy && (
            <Box>
              <Loading />
              <Typography variant="caption" color="text.secondary">{tr('This can take up to 30 seconds.')}</Typography>
            </Box>
          )}
          {error && <Alert severity="error">{error}</Alert>}
          {notice && <Alert severity="success">{notice}</Alert>}
          {result?.kind === 'goals' && (
            <GoalRows result={result} busy={busy} onChange={setResult} onAdd={(rows) => void addGoals(rows)} />
          )}
          {result?.kind === 'steps' && (
            <StepRows result={result} busy={busy} onChange={setResult} onAdd={(rows) => void addSteps(result.goalId, rows)} />
          )}
          {result?.kind === 'check' && <CheckResult check={result.check} />}
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
