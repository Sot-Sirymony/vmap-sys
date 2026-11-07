import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { suggestDraftStepTasks, suggestTasks } from '../../api/aiPlanApi';
import { tr } from '../../i18n/translate';
import type {
  DraftGoal, DraftStep, DraftTask, Dream, PlanAsk, PlanDraftContent, RefineAction, TaskItem, VisionStep,
} from '../../types/vision';
import { Button } from '../common/Button';
import { Loading } from '../common/Loading';
import { SuggestionRating } from '../ai/SuggestionRating';
import {
  type DraftUpdate, addClarification, addTasksToStep, emptyTask, isReplaceable, removeTask, replaceTasks, taskFromSuggestion, updateTask,
} from './draftModel';
import { arrangeFor } from './arrange';
import { AnswerQuestion, DateField, ItemCard, PrioritySelect, Prompts, RefineBar, twoColumns } from './parts';
import { useAiAction } from './useAiAction';

type Props = {
  dream: Dream;
  content: PlanDraftContent;
  onChange: (update: DraftUpdate) => void;
  existingSteps: VisionStep[];
  existingTasks: TaskItem[];
  token: string;
  language: string;
  aiReady: boolean;
};

/** A complex step to break down: one already in the plan, or one in the draft. stepKey is its key in the draft, if any. */
type Row = { id: string; goal: DraftGoal; stepKey?: string; existing?: VisionStep; draft?: DraftStep };

/**
 * Stage 4: complex steps broken into small tasks, each with owner, date and
 * priority (CLAUDE.md rule 5). Works for new complex steps and for complex
 * steps already in the plan. With AI, the task builder suggests 2 to 8 tasks
 * (PB-FR-14), which can be refined, locked or swapped like goals and steps.
 */
export function TasksStage({ dream, content, onChange, existingSteps, existingTasks, token, language, aiReady }: Props) {
  const ai = useAiAction();
  const rows: Row[] = [];
  for (const goal of content.goals) {
    if (goal.existingId != null) {
      for (const step of existingSteps.filter((item) => item.goalId === goal.existingId && item.complex && !item.archived)) {
        const container = goal.steps.find((item) => item.existingId === step.id);
        rows.push({ id: `e${step.id}`, goal, stepKey: container?.key, existing: step, draft: container });
      }
    }
    for (const step of goal.steps.filter((item) => item.existingId == null && item.complex)) {
      rows.push({ id: step.key, goal, stepKey: step.key, draft: step });
    }
  }

  function request(row: Row, ask: PlanAsk) {
    if (row.existing) {
      return suggestTasks(token, row.existing.id, language, ask);
    }
    const step = row.draft as DraftStep;
    return suggestDraftStepTasks(token, dream.id, language, {
      goalTitle: row.goal.title, goalTargetDate: row.goal.targetDate, stepTitle: step.title,
      stepDescription: step.description, stepTargetDate: step.targetDate,
    }, ask);
  }

  function suggest(row: Row, extra: PlanAsk = {}, answers = content.clarifications ?? []) {
    return ai.run(row.id, async () => {
      const answer = await request(row, { answers, ...extra });
      if (answer.tasks.length > 0) {
        const tasks = answer.tasks.map((task) => taskFromSuggestion(task, answer.requestId));
        onChange((current) => addTasksToStep(current, row.goal.key, { key: row.stepKey, existing: row.existing }, tasks));
      }
      return { question: answer.clarifyingQuestion, requestId: answer.requestId };
    });
  }

  function answerQuestion(row: Row, text: string) {
    const item = { question: ai.question ?? '', answer: text };
    const answers = addClarification(content, item).clarifications;
    onChange((current) => addClarification(current, item));
    void suggest(row, {}, answers);
  }

  function refine(row: Row, action: RefineAction, note: string, only?: DraftTask) {
    const tasks = row.draft?.tasks ?? [];
    const replacing = only ? [only] : tasks.filter(isReplaceable);
    const keys = replacing.map((task) => task.key);
    const keep = tasks.filter((task) => !keys.includes(task.key)).map((task) => task.title).filter(Boolean);
    return ai.run(only ? only.key : row.id, async () => {
      const answer = await request(row, {
        answers: content.clarifications ?? [], refine: action, note: note || undefined, keep, replace: replacing.map((task) => task.title),
      });
      const next = answer.tasks.map((task) => taskFromSuggestion(task, answer.requestId));
      if (next.length > 0 && row.stepKey) {
        onChange((current) => replaceTasks(current, row.goal.key, row.stepKey as string, keys, next));
      }
      return { requestId: answer.requestId };
    });
  }

  function add(row: Row) {
    onChange((current) => addTasksToStep(current, row.goal.key, { key: row.stepKey, existing: row.existing }, [emptyTask()]));
  }

  if (rows.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        {tr('No complex steps. If a step needs several actions, tick "Needs several tasks" on it in Steps.')}
      </Typography>
    );
  }
  return (
    <Stack spacing={3}>
      <Prompts questions={['Can this be done in one action?', 'Who will do each task, and by when?', 'How long will each take?']} />
      {ai.error && <Alert severity="error">{ai.error}</Alert>}
      <SuggestionRating key={ai.requestId ?? 'none'} token={token} requestId={ai.requestId} />
      {rows.map((row) => {
        const title = row.existing?.title ?? row.draft?.title ?? '';
        const savedCount = row.existing ? existingTasks.filter((task) => task.stepId === row.existing?.id && !task.archived).length : 0;
        const tasks = row.draft?.tasks ?? [];
        const stepKey = row.stepKey as string;
        const update = (task: DraftTask, patch: Partial<DraftTask>) =>
          onChange((current) => updateTask(current, row.goal.key, stepKey, task.key, patch));
        return (
          <Box key={row.id} sx={{ display: 'grid', gap: 1 }}>
            <Box>
              <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>{title || tr('Untitled step')}</Typography>
              <Typography variant="caption" color="text.secondary">
                {row.goal.title}{savedCount > 0 ? ` · ${tr('{count} task(s) already', { count: savedCount })}` : ''}
              </Typography>
            </Box>
            {tasks.map((task) => {
              const fromAi = task.requestId != null;
              return (
                <ItemCard key={task.key} title={task.title} fromAi={fromAi} edited={task.edited} locked={task.locked} busy={ai.busy !== null}
                  onToggleLock={fromAi ? () => update(task, { locked: !task.locked }) : undefined}
                  onAnother={aiReady && isReplaceable(task) ? () => void refine(row, 'ANOTHER', '', task) : undefined}
                  onRemove={() => onChange((current) => removeTask(current, row.goal.key, stepKey, task.key))}
                  arrange={arrangeFor(content, { goalKey: row.goal.key, stepKey, taskKey: task.key }, onChange)}>
                  <TextField size="small" label={tr('Task')} value={task.title} required
                    onChange={(event) => update(task, { title: event.target.value })} />
                  <Box sx={twoColumns}>
                    <TextField size="small" label={tr('Owner')} value={task.owner} required
                      onChange={(event) => update(task, { owner: event.target.value })} />
                    <DateField label="Due date" value={task.dueDate} onChange={(value) => update(task, { dueDate: value })} />
                    <PrioritySelect value={task.priority} onChange={(value) => update(task, { priority: value })} />
                    <TextField size="small" type="number" label={tr('Estimated hours')} value={task.estimatedHours ?? ''}
                      slotProps={{ htmlInput: { min: 0, max: 999, step: 0.5 } }}
                      onChange={(event) => update(task, { estimatedHours: event.target.value === '' ? null : Number(event.target.value) })} />
                  </Box>
                  {ai.busy === task.key && <Loading />}
                </ItemCard>
              );
            })}
            {aiReady && row.stepKey && (
              <RefineBar replaceable={tasks.filter(isReplaceable).length} busy={ai.busy !== null}
                onRefine={(action, note) => void refine(row, action, note)} />
            )}
            <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
              <Button variant="secondary" onClick={() => add(row)}>{tr('Add a task')}</Button>
              {aiReady && (
                <Button disabled={ai.busy !== null || !title.trim()} onClick={() => void suggest(row)}>{tr('Suggest tasks')}</Button>
              )}
            </Box>
            {ai.busy === row.id && <Loading />}
            {ai.lastId === row.id && (
              <AnswerQuestion key={ai.question ?? 'none'} question={ai.question} busy={ai.busy !== null}
                onAnswer={(text) => answerQuestion(row, text)} onSuggestAnyway={() => void suggest(row, { suggestAnyway: true })} />
            )}
          </Box>
        );
      })}
    </Stack>
  );
}
