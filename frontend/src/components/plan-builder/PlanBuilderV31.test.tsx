import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { checkPlanDraft } from '../../api/aiPlanApi';
import type { DraftIssue, Dream, PlanDraftContent } from '../../types/vision';
import { GoalsStage } from './GoalsStage';
import { ReviewStage } from './ReviewStage';
import { StepsStage } from './StepsStage';
import { TasksStage } from './TasksStage';
import {
  type DraftUpdate, addSteps, addTask, applyIssueFix, emptyGoal, emptyStep, emptyTask, goalFromSuggestion, updateGoal,
} from './draftModel';

vi.mock('../../api/aiPlanApi', () => ({
  checkPlanDraft: vi.fn(), fixPlanDraft: vi.fn(), savePlanCapacity: vi.fn(), ratePlanAnswer: vi.fn(),
  suggestGoals: vi.fn(), suggestSteps: vi.fn(), suggestDraftGoalSteps: vi.fn(), suggestTasks: vi.fn(), suggestDraftStepTasks: vi.fn(),
}));

const dream = { id: 5, title: 'Become a researcher', archived: false } as Dream;

function draft(): PlanDraftContent {
  const goal = { ...emptyGoal(), key: 'g1', title: 'Prepare a concept note', successCriteria: 'Mentor accepts it', targetDate: '2027-05-31' };
  const step = { ...emptyStep(), key: 's1', title: 'Search literature', targetDate: '2027-04-20' };
  return addSteps({ goals: [goal] }, 'g1', [step]);
}

function Harness({ initial, stage, onAdd = vi.fn() }: {
  initial: PlanDraftContent;
  stage: 'goals' | 'steps' | 'tasks' | 'review';
  onAdd?: (accepted: string[]) => void;
}) {
  const [content, setContent] = useState(initial);
  const change = (update: DraftUpdate) => setContent((current) => (typeof update === 'function' ? update(current) : update));
  return (
    <MemoryRouter>
      {stage === 'goals' && <GoalsStage dream={dream} content={content} onChange={change} token="t" language="en" aiReady={false} />}
      {stage === 'steps' && <StepsStage dream={dream} content={content} onChange={change} existingSteps={[]} token="t" language="en" aiReady={false} />}
      {stage === 'tasks' && (
        <TasksStage dream={dream} content={content} onChange={change} existingSteps={[]} existingTasks={[]} token="t" language="en" aiReady={false} />
      )}
      {stage === 'review' && (
        <ReviewStage content={content} onChange={change} existingSteps={[]} busy={false} onGoTo={vi.fn()} onAdd={onAdd}
          dreamId={5} token="t" language="en" aiReady={false} saveDraft={vi.fn().mockResolvedValue(undefined)}
          />
      )}
      <output data-testid="draft">{JSON.stringify(content)}</output>
    </MemoryRouter>
  );
}

const current = () => JSON.parse(screen.getByTestId('draft').textContent ?? '{}') as PlanDraftContent;

const issue = (patch: Partial<DraftIssue>): DraftIssue => ({ kind: 'X', itemKey: 's1', problem: 'Problem', fromAi: false, ...patch });

describe('Build my plan, V31 accuracy check', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('applies the new one-click fixes and does not count approving as an edit (R-03, R-04, R-07)', () => {
    let content = addGoals(draft());
    content = applyIssueFix(content, issue({ itemKey: 'g2', fixAction: 'APPROVE' }));
    expect(content.goals[1]).toMatchObject({ approved: true, edited: false });
    content = applyIssueFix(content, issue({ itemKey: 'g2', fixAction: 'SET_DATE', fixValue: '2027-06-30' }));
    expect(content.goals[1].targetDate).toBe('2027-06-30');
    content = addTask(content, 'g1', 's1', { ...emptyTask(), key: 't1', title: 'Search PubMed', priority: undefined as never });
    content = applyIssueFix(content, issue({ itemKey: 't1', fixAction: 'SET_PRIORITY', fixValue: 'MEDIUM' }));
    expect(content.goals[0].steps[0].tasks[0].priority).toBe('MEDIUM');
    expect(emptyGoal().approved).toBe(true);

    function addGoals(base: PlanDraftContent) {
      const suggested = { ...goalFromSuggestion({ title: 'Use two AI tools', priority: 'HIGH', moonshot: false }, 9), key: 'g2' };
      return { ...base, goals: [...base.goals, suggested] };
    }
  });

  it('asks the participant to approve each AI goal (DC-FR-26)', async () => {
    const suggested = { ...goalFromSuggestion({ title: 'Use two AI tools', priority: 'HIGH', moonshot: false }, 9), key: 'g2' };
    render(<Harness initial={{ goals: [suggested] }} stage="goals" />);
    expect(current().goals[0].approved).toBe(false);
    await userEvent.click(screen.getByRole('checkbox', { name: 'I have read this goal and it is right for me' }));
    expect(current().goals[0]).toMatchObject({ approved: true, edited: false });
    expect(screen.getByText("How you'll know this goal is done.")).toBeInTheDocument();
  });

  it('asks for a reason when a task is set to Blocked (DC-FR-38)', async () => {
    const initial = addTask(updateGoal(draft(), 'g1', {}), 'g1', 's1', { ...emptyTask(), key: 't1', title: 'Search PubMed' });
    const complex = { ...initial, goals: initial.goals.map((goal) => ({ ...goal, steps: goal.steps.map((step) => ({ ...step, complex: true })) })) };
    render(<Harness initial={complex} stage="tasks" />);
    expect(current().goals[0].steps[0].tasks[0].status).toBe('NOT_STARTED');
    expect(screen.queryByRole('textbox', { name: 'What is in the way?' })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('combobox', { name: 'Status' }));
    await userEvent.click(screen.getByRole('option', { name: 'Blocked' }));
    expect(current().goals[0].steps[0].tasks[0].status).toBe('BLOCKED');
    expect(screen.getByText('A blocked task needs a reason.')).toBeInTheDocument();
    await userEvent.type(screen.getByRole('textbox', { name: 'What is in the way?' }), 'No library card');
    expect(current().goals[0].steps[0].tasks[0].blockerReason).toBe('No library card');
  });

  it('shows the kind of gap and the helper the fixed map suggests (DC-FR-31, D-31-8)', async () => {
    render(<Harness initial={draft()} stage="steps" />);
    expect(screen.queryByRole('combobox', { name: 'Kind of gap' })).not.toBeInTheDocument();
    await userEvent.type(screen.getByRole('textbox', { name: 'Resources and people' }), 'How to write a methodology');
    await userEvent.click(screen.getByRole('combobox', { name: 'Kind of gap' }));
    await userEvent.click(screen.getByRole('option', { name: 'Knowledge' }));
    expect(current().goals[0].steps[0].gapKind).toBe('KNOWLEDGE');
    expect(screen.getByText('Who usually helps: Mentor or expert')).toBeInTheDocument();
  });

  it('blocks saving on a MUST fail and needs "I understand" for each SHOULD rule (DC-FR-52, DC-FR-53)', async () => {
    vi.mocked(checkPlanDraft).mockResolvedValue({ aiChecked: false, issues: [
      issue({ kind: 'GAP_NO_HELPER', itemTitle: 'Search literature', problem: 'This step is missing knowledge and has no helper yet.',
        rule: 'R-06', level: 'SHOULD', fixAction: 'GO_TO_HELPERS' }),
      issue({ kind: 'AFTER_GOAL_DATE', itemTitle: 'Search literature', problem: 'This step is due after its goal.', rule: 'R-04',
        level: 'MUST', fixAction: 'SET_DATE', fixValue: '2027-04-10' }),
      issue({ kind: 'MISSING_RESOURCE', problem: 'This step does not say what it needs.', rule: 'R-06', level: 'INFO' }),
    ] });
    const onAdd = vi.fn();
    render(<Harness initial={draft()} stage="review" onAdd={onAdd} />);

    expect(await screen.findByText('1 to fix', {}, { timeout: 3000 })).toBeInTheDocument();
    expect(checkPlanDraft).toHaveBeenCalledWith('t', 5, 'en', true);
    const save = screen.getByRole('button', { name: 'Save my plan' });
    expect(save).toBeDisabled();
    expect(screen.getByText('1 to accept')).toBeInTheDocument();
    expect(screen.getByText('Worth a look:')).toBeInTheDocument();
    expect(screen.getAllByText('Pass').length).toBe(6);

    // The MUST fail is fixed on the server's next run; the SHOULD rule still needs a tick.
    vi.mocked(checkPlanDraft).mockResolvedValue({ aiChecked: false, issues: [
      issue({ kind: 'GAP_NO_HELPER', itemTitle: 'Search literature', problem: 'This step is missing knowledge and has no helper yet.',
        rule: 'R-06', level: 'SHOULD' }),
    ] });
    await userEvent.click(screen.getByRole('button', { name: /^Use / }));
    await vi.waitFor(() => expect(screen.queryByText('1 to fix')).not.toBeInTheDocument(), { timeout: 3000 });
    expect(save).toBeDisabled();
    const row = screen.getByText(/R-06 · Gaps point to the right kind of helper/).closest('li') as HTMLElement;
    await userEvent.click(within(row).getByRole('checkbox', { name: 'I understand, save anyway' }));
    expect(save).toBeEnabled();
    await userEvent.click(save);
    expect(onAdd).toHaveBeenCalledWith(['R-06']);
  });
});
