import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Checkbox from '@mui/material/Checkbox';
import FormControlLabel from '@mui/material/FormControlLabel';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { suggestDraftGoalSteps, suggestSteps } from '../../api/aiPlanApi';
import { tr } from '../../i18n/translate';
import type { DraftGoal, DraftStep, Dream, PlanAsk, PlanDraftContent, RefineAction, VisionStep } from '../../types/vision';
import { Button } from '../common/Button';
import { Loading } from '../common/Loading';
import { SuggestionRating } from '../ai/SuggestionRating';
import {
  type DraftUpdate, addClarification, addSteps, emptyStep, isReplaceable, removeStep, replaceSteps, stepsFromSuggestions, updateStep,
} from './draftModel';
import { AnswerQuestion, DateField, ItemCard, PrioritySelect, Prompts, RefineBar, twoColumns } from './parts';
import { useAiAction } from './useAiAction';

type Props = {
  dream: Dream;
  content: PlanDraftContent;
  onChange: (update: DraftUpdate) => void;
  existingSteps: VisionStep[];
  token: string;
  language: string;
  aiReady: boolean;
};

/**
 * Stage 3: the actions, in order, for each goal. A step that needs several
 * actions is marked complex for stage 4. As with goals, a clarifying question
 * is answered in place and each goal's suggested steps can be refined.
 */
export function StepsStage({ dream, content, onChange, existingSteps, token, language, aiReady }: Props) {
  const ai = useAiAction();
  const goals = content.goals.filter((goal) => goal.existingId != null || goal.title.trim());

  function request(goal: DraftGoal, ask: PlanAsk) {
    return goal.existingId != null
      ? suggestSteps(token, goal.existingId, language, ask)
      : suggestDraftGoalSteps(token, dream.id, language, goal, ask);
  }

  function suggest(goal: DraftGoal, extra: PlanAsk = {}, answers = content.clarifications ?? []) {
    return ai.run(goal.key, async () => {
      const answer = await request(goal, { answers, ...extra });
      if (answer.steps.length > 0) {
        const next = stepsFromSuggestions(answer.steps, answer.requestId, tr('People'));
        onChange((current) => addSteps(current, goal.key, next));
      }
      return { question: answer.clarifyingQuestion, requestId: answer.requestId };
    });
  }

  function answerQuestion(goal: DraftGoal, text: string) {
    const item = { question: ai.question ?? '', answer: text };
    const answers = addClarification(content, item).clarifications;
    onChange((current) => addClarification(current, item));
    void suggest(goal, {}, answers);
  }

  function refine(goal: DraftGoal, action: RefineAction, note: string, only?: DraftStep) {
    const fresh = goal.steps.filter((step) => step.existingId == null);
    const replacing = only ? [only] : fresh.filter(isReplaceable);
    const keys = replacing.map((step) => step.key);
    const keep = fresh.filter((step) => !keys.includes(step.key)).map((step) => step.title).filter(Boolean);
    return ai.run(only ? only.key : goal.key, async () => {
      const answer = await request(goal, {
        answers: content.clarifications ?? [], refine: action, note: note || undefined, keep, replace: replacing.map((step) => step.title),
      });
      const next = stepsFromSuggestions(answer.steps, answer.requestId, tr('People'));
      if (next.length > 0) {
        onChange((current) => replaceSteps(current, goal.key, keys, next));
      }
      return { requestId: answer.requestId };
    });
  }

  // PB-FR-13: steps for every goal that has none yet, one goal after another.
  const unplanned = goals.filter((goal) => goal.steps.every((step) => step.existingId != null)
    && !existingSteps.some((step) => step.goalId === goal.existingId && !step.archived));

  async function suggestAll() {
    for (const goal of unplanned) {
      await suggest(goal);
    }
  }

  if (goals.length === 0) {
    return <Typography variant="body2" color="text.secondary">{tr('Add a goal first, then break it into steps here.')}</Typography>;
  }
  return (
    <Stack spacing={3}>
      <Prompts questions={['What must happen first?', 'What can be done in one sitting?', 'What does each step need: money, tools, skills or people?']} />
      {aiReady && unplanned.length > 1 && (
        <Box>
          <Button disabled={ai.busy !== null} onClick={() => void suggestAll()}>
            {tr('Suggest steps for all {count} goals without steps', { count: unplanned.length })}
          </Button>
        </Box>
      )}
      {ai.error && <Alert severity="error">{ai.error}</Alert>}
      <SuggestionRating key={ai.requestId ?? 'none'} token={token} requestId={ai.requestId} />
      {goals.map((goal) => {
        const saved = goal.existingId != null
          ? existingSteps.filter((step) => step.goalId === goal.existingId && !step.archived)
            .sort((left, right) => left.sequenceNumber - right.sequenceNumber)
          : [];
        const fresh = goal.steps.filter((step) => step.existingId == null);
        const update = (step: DraftStep, patch: Partial<DraftStep>) => onChange((current) => updateStep(current, goal.key, step.key, patch));
        return (
          <Box key={goal.key} sx={{ display: 'grid', gap: 1 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>{goal.title}</Typography>
            {saved.map((step) => <ItemCard key={step.id} title={step.title} existing />)}
            {fresh.map((step) => {
              const fromAi = step.requestId != null;
              return (
                <ItemCard key={step.key} title={step.title} fromAi={fromAi} edited={step.edited} locked={step.locked} busy={ai.busy !== null}
                  onToggleLock={fromAi ? () => update(step, { locked: !step.locked }) : undefined}
                  onAnother={aiReady && isReplaceable(step) ? () => void refine(goal, 'ANOTHER', '', step) : undefined}
                  onRemove={() => onChange((current) => removeStep(current, goal.key, step.key))}>
                  <TextField size="small" label={tr('Step')} value={step.title} required
                    onChange={(event) => update(step, { title: event.target.value })} />
                  <Box sx={twoColumns}>
                    <TextField size="small" label={tr('Owner')} value={step.owner} required
                      onChange={(event) => update(step, { owner: event.target.value })} />
                    <DateField label="Target date" value={step.targetDate} onChange={(value) => update(step, { targetDate: value })} />
                    <PrioritySelect value={step.priority} onChange={(value) => update(step, { priority: value })} />
                    <TextField size="small" type="number" label={tr('Estimated hours')} value={step.estimatedHours ?? ''}
                      slotProps={{ htmlInput: { min: 0, max: 999, step: 0.5 } }}
                      onChange={(event) => update(step, { estimatedHours: event.target.value === '' ? null : Number(event.target.value) })} />
                    <TextField select size="small" label={tr('Starts after')} value={step.startsAfterKey ?? ''}
                      onChange={(event) => update(step, { startsAfterKey: event.target.value || null })}>
                      <MenuItem value="">{tr('Can start any time')}</MenuItem>
                      {fresh.slice(0, fresh.indexOf(step)).map((earlier) => (
                        <MenuItem key={earlier.key} value={earlier.key}>{earlier.title || tr('Untitled step')}</MenuItem>
                      ))}
                    </TextField>
                    <FormControlLabel label={tr('Needs several tasks (complex)')} control={(
                      <Checkbox checked={step.complex} onChange={(event) => update(step, { complex: event.target.checked })} />
                    )} />
                  </Box>
                  <TextField size="small" label={tr('Resources and people')} value={step.resources ?? ''} multiline
                    onChange={(event) => update(step, { resources: event.target.value })} />
                  {ai.busy === step.key && <Loading />}
                </ItemCard>
              );
            })}
            {aiReady && (
              <RefineBar replaceable={fresh.filter(isReplaceable).length} busy={ai.busy !== null}
                onRefine={(action, note) => void refine(goal, action, note)} />
            )}
            <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
              <Button variant="secondary" onClick={() => onChange((current) => addSteps(current, goal.key, [emptyStep()]))}>
                {tr('Add a step')}
              </Button>
              {aiReady && (
                <Button disabled={ai.busy !== null} onClick={() => void suggest(goal)}>{tr('Suggest steps')}</Button>
              )}
            </Box>
            {ai.busy === goal.key && <Loading />}
            {ai.lastId === goal.key && (
              <AnswerQuestion key={ai.question ?? 'none'} question={ai.question} busy={ai.busy !== null}
                onAnswer={(text) => answerQuestion(goal, text)} onSuggestAnyway={() => void suggest(goal, { suggestAnyway: true })} />
            )}
          </Box>
        );
      })}
    </Stack>
  );
}
