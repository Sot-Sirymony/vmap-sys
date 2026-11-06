import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { suggestDraftGoalSteps, suggestDraftStepTasks, suggestTasks } from '../../api/aiPlanApi';
import type { Dream, PlanDraftContent, VisionStep } from '../../types/vision';
import { StepsStage } from './StepsStage';
import { TasksStage } from './TasksStage';
import { type DraftUpdate, emptyGoal, emptyStep, syncWithPlan } from './draftModel';

vi.mock('../../api/aiPlanApi', () => ({
  suggestSteps: vi.fn(), suggestDraftGoalSteps: vi.fn(), suggestTasks: vi.fn(), suggestDraftStepTasks: vi.fn(), ratePlanAnswer: vi.fn(),
}));

const dream = { id: 5, title: 'Own a home', archived: false } as Dream;
const savedComplex = { id: 31, goalId: 3, title: 'Visit banks', owner: 'Me', complex: true, archived: false, sequenceNumber: 1,
  priority: 'HIGH', targetDate: '2027-01-30' } as VisionStep;

function Harness({ initial, stage, steps = [] }: { initial: PlanDraftContent; stage: 'steps' | 'tasks'; steps?: VisionStep[] }) {
  const [content, setContent] = useState(initial);
  const change = (update: DraftUpdate) => setContent((current) => (typeof update === 'function' ? update(current) : update));
  return (
    <MemoryRouter>
      {stage === 'steps'
        ? <StepsStage dream={dream} content={content} onChange={change} existingSteps={steps} token="t" language="en" aiReady />
        : <TasksStage dream={dream} content={content} onChange={change} existingSteps={steps} existingTasks={[]} token="t" language="en" aiReady />}
      <output data-testid="draft">{JSON.stringify(content)}</output>
    </MemoryRouter>
  );
}

const draft = () => JSON.parse(screen.getByTestId('draft').textContent ?? '{}') as PlanDraftContent;

describe('Build my plan, phase 4', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(suggestDraftGoalSteps).mockReset();
    vi.mocked(suggestTasks).mockReset();
    vi.mocked(suggestDraftStepTasks).mockReset();
  });

  it('suggests steps for every goal without steps, with effort and order (PB-FR-12, PB-FR-13)', async () => {
    vi.mocked(suggestDraftGoalSteps)
      .mockResolvedValueOnce({ requestId: 1, steps: [
        { title: 'Gather papers', owner: 'Me', priority: 'HIGH', estimatedHours: 2 },
        { title: 'Visit banks', owner: 'Me', complex: true, startsAfter: 1 },
      ] })
      .mockResolvedValueOnce({ requestId: 2, steps: [{ title: 'Get credit report', owner: 'Me' }] });
    render(<Harness stage="steps" initial={{ goals: [{ ...emptyGoal(), title: 'Save' }, { ...emptyGoal(), title: 'Fix credit' }] }} />);

    await userEvent.click(screen.getByRole('button', { name: 'Suggest steps for all 2 goals without steps' }));
    expect(await screen.findByDisplayValue('Get credit report')).toBeInTheDocument();
    expect(suggestDraftGoalSteps).toHaveBeenCalledTimes(2);

    const [save] = draft().goals;
    expect(save.steps[0]).toMatchObject({ title: 'Gather papers', priority: 'HIGH', estimatedHours: 2 });
    expect(save.steps[1]).toMatchObject({ complex: true, startsAfterKey: save.steps[0].key });
    expect(screen.getByDisplayValue('2')).toBeInTheDocument();
  });

  it('breaks an existing complex step into suggested tasks through a container (PB-FR-14)', async () => {
    vi.mocked(suggestTasks).mockResolvedValue({ requestId: 7, tasks: [
      { title: 'Call bank A', owner: 'Me', priority: 'HIGH', dueDate: '2027-01-10', estimatedHours: 0.5 },
      { title: 'Call bank B', owner: 'Me', priority: 'HIGH', dueDate: '2027-01-11' },
    ] });
    const initial = syncWithPlan({ goals: [] }, 5, [{ id: 3, dreamId: 5, title: 'Save', priority: 'HIGH', archived: false } as never]);
    render(<Harness stage="tasks" initial={initial} steps={[savedComplex]} />);

    await userEvent.click(screen.getByRole('button', { name: 'Suggest tasks' }));
    expect(suggestTasks).toHaveBeenCalledWith('t', 31, 'en', { answers: [] });
    expect(await screen.findByDisplayValue('Call bank B')).toBeInTheDocument();
    const container = draft().goals[0].steps[0];
    expect(container).toMatchObject({ existingId: 31 });
    expect(container.tasks.map((task) => task.title)).toEqual(['Call bank A', 'Call bank B']);
    expect(container.tasks[0]).toMatchObject({ requestId: 7, estimatedHours: 0.5 });
  });

  it('suggests tasks for a new complex step and swaps one for another option', async () => {
    vi.mocked(suggestDraftStepTasks)
      .mockResolvedValueOnce({ requestId: 8, tasks: [{ title: 'Call bank A', owner: 'Me', priority: 'LOW' }] })
      .mockResolvedValueOnce({ requestId: 9, tasks: [{ title: 'Email bank A', owner: 'Me', priority: 'LOW' }] });
    const step = { ...emptyStep(), title: 'Visit banks', complex: true, targetDate: '2027-01-30' };
    render(<Harness stage="tasks" initial={{ goals: [{ ...emptyGoal(), title: 'Save', steps: [step] }] }} />);

    await userEvent.click(screen.getByRole('button', { name: 'Suggest tasks' }));
    expect(suggestDraftStepTasks).toHaveBeenCalledWith('t', 5, 'en', expect.objectContaining({
      goalTitle: 'Save', stepTitle: 'Visit banks', stepTargetDate: '2027-01-30',
    }), { answers: [] });
    expect(await screen.findByDisplayValue('Call bank A')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Another option' }));
    expect(suggestDraftStepTasks).toHaveBeenLastCalledWith('t', 5, 'en', expect.anything(),
      expect.objectContaining({ refine: 'ANOTHER', replace: ['Call bank A'] }));
    expect(await screen.findByDisplayValue('Email bank A')).toBeInTheDocument();
    expect(draft().goals[0].steps[0].tasks.map((task) => task.title)).toEqual(['Email bank A']);
    expect(within(document.body).queryByDisplayValue('Call bank A')).not.toBeInTheDocument();
  });
});
