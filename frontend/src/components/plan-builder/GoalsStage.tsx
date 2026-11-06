import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { suggestGoals } from '../../api/aiPlanApi';
import { tr } from '../../i18n/translate';
import type { DraftGoal, Dream, PlanAsk, PlanDraftContent, RefineAction } from '../../types/vision';
import { Button } from '../common/Button';
import { Loading } from '../common/Loading';
import { SuggestionRating } from '../ai/SuggestionRating';
import {
  type DraftUpdate, addClarification, addGoals, emptyGoal, goalFromSuggestion, isReplaceable, removeGoal, replaceGoals, updateGoal,
} from './draftModel';
import { AnswerQuestion, DateField, ItemCard, PrioritySelect, Prompts, RefineBar, twoColumns } from './parts';
import { useAiAction } from './useAiAction';

type Props = {
  dream: Dream;
  content: PlanDraftContent;
  onChange: (update: DraftUpdate) => void;
  token: string;
  language: string;
  aiReady: boolean;
};

/**
 * Stage 2: the major results that would make the dream real. AI suggestions
 * land in the draft as editable goals; a clarifying question is answered in
 * place (PB-FR-08 to PB-FR-11), and the set can be refined or one goal swapped,
 * keeping what the participant edited or locked (PB-FR-19 to PB-FR-21).
 */
export function GoalsStage({ dream, content, onChange, token, language, aiReady }: Props) {
  const ai = useAiAction();
  const existing = content.goals.filter((goal) => goal.existingId != null);
  const fresh = content.goals.filter((goal) => goal.existingId == null);
  const replaceable = fresh.filter(isReplaceable);

  /** Every request carries the answers given so far (PB-FR-09). */
  function ask(extra: PlanAsk = {}, answers = content.clarifications ?? []): PlanAsk {
    return { answers, ...extra };
  }

  function suggest(stretch: boolean, extra: PlanAsk = {}, answers?: PlanDraftContent['clarifications']) {
    return ai.run(stretch ? 'stretch' : 'goals', async () => {
      const answer = await suggestGoals(token, dream.id, language, stretch, ask(extra, answers));
      if (answer.goals.length > 0) {
        onChange((current) => addGoals(current, answer.goals.map((goal) => goalFromSuggestion(goal, answer.requestId))));
      }
      return { question: answer.clarifyingQuestion, requestId: answer.requestId };
    });
  }

  function answerQuestion(text: string) {
    const item = { question: ai.question ?? '', answer: text };
    const answers = addClarification(content, item).clarifications;
    onChange((current) => addClarification(current, item));
    void suggest(false, {}, answers);
  }

  function refine(action: RefineAction, note: string, only?: DraftGoal) {
    const replacing = only ? [only] : replaceable;
    const keys = replacing.map((goal) => goal.key);
    const keep = fresh.filter((goal) => !keys.includes(goal.key)).map((goal) => goal.title).filter(Boolean);
    return ai.run(only ? only.key : 'refine', async () => {
      const answer = await suggestGoals(token, dream.id, language, false, ask({
        refine: action, note: note || undefined, keep, replace: replacing.map((goal) => goal.title),
      }));
      const next = answer.goals.map((goal) => goalFromSuggestion(goal, answer.requestId));
      if (next.length > 0) {
        onChange((current) => replaceGoals(current, keys, next));
      }
      return { requestId: answer.requestId };
    });
  }

  return (
    <Stack spacing={2}>
      <Prompts questions={[
        'Which major results would make this dream happen?',
        'How will you know each one is done?',
        'Which must come first?',
      ]} />
      {existing.length > 0 && (
        <Stack spacing={1}>
          {existing.map((goal) => <ItemCard key={goal.key} title={goal.title} existing />)}
        </Stack>
      )}
      {fresh.map((goal) => {
        const fromAi = goal.requestId != null;
        return (
          <ItemCard key={goal.key} title={goal.title} fromAi={fromAi} edited={goal.edited} locked={goal.locked} busy={ai.busy !== null}
            onToggleLock={fromAi ? () => onChange((current) => updateGoal(current, goal.key, { locked: !goal.locked })) : undefined}
            onAnother={aiReady && isReplaceable(goal) ? () => void refine('ANOTHER', '', goal) : undefined}
            onRemove={() => onChange((current) => removeGoal(current, goal.key))}>
            <TextField size="small" label={tr('Goal')} value={goal.title} required
              onChange={(event) => onChange((current) => updateGoal(current, goal.key, { title: event.target.value }))} />
            <TextField size="small" label={tr('Success criteria')} value={goal.successCriteria ?? ''} multiline
              onChange={(event) => onChange((current) => updateGoal(current, goal.key, { successCriteria: event.target.value }))} />
            <Box sx={twoColumns}>
              <DateField label="Target date" value={goal.targetDate}
                onChange={(value) => onChange((current) => updateGoal(current, goal.key, { targetDate: value }))} />
              <PrioritySelect value={goal.priority}
                onChange={(value) => onChange((current) => updateGoal(current, goal.key, { priority: value }))} />
            </Box>
            {ai.busy === goal.key && <Loading />}
          </ItemCard>
        );
      })}
      {content.goals.length === 0 && (
        <Typography variant="body2" color="text.secondary">{tr('No goals yet. Add your first one, or ask for suggestions.')}</Typography>
      )}
      {aiReady && <RefineBar replaceable={replaceable.length} busy={ai.busy !== null} onRefine={(action, note) => void refine(action, note)} />}
      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
        <Button variant="secondary" onClick={() => onChange((current) => addGoals(current, [emptyGoal()]))}>{tr('Add a goal')}</Button>
        {aiReady && (
          <>
            <Button disabled={ai.busy !== null} onClick={() => void suggest(false)}>{tr('Suggest goals')}</Button>
            <Button variant="secondary" disabled={ai.busy !== null} onClick={() => void suggest(true)}>{tr('Suggest a stretch goal')}</Button>
          </>
        )}
      </Box>
      {ai.busy && <Box><Loading /><Typography variant="caption" color="text.secondary">{tr('This can take up to 30 seconds.')}</Typography></Box>}
      {ai.error && <Alert severity="error">{ai.error}</Alert>}
      <AnswerQuestion key={ai.question ?? 'none'} question={ai.question} busy={ai.busy !== null}
        onAnswer={answerQuestion} onSuggestAnyway={() => void suggest(false, { suggestAnyway: true })} />
      <SuggestionRating key={ai.requestId ?? 'none'} token={token} requestId={ai.requestId} />
    </Stack>
  );
}
