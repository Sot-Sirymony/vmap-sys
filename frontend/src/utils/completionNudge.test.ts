import { beforeEach, describe, expect, it, vi } from 'vitest';
import { updateGoalStatus } from '../api/goalApi';
import { updateStepStatus } from '../api/stepApi';
import { aGoal, aStep, aTask } from '../test/builders';
import { nudgeAfterStepComplete, nudgeAfterTaskComplete } from './completionNudge';

vi.mock('../api/goalApi');
vi.mock('../api/stepApi');

/**
 * CLAUDE.md BR-11: when all child tasks are completed, suggest marking the
 * parent step completed, and then its goal. Nothing changes until the
 * participant chooses the toast's action.
 */
describe('completion nudges (BR-11)', () => {
  const goal = aGoal({ id: 1, title: 'Prepare one concept note' });
  const steps = [aStep({ id: 10, goalId: 1, title: 'Search literature' }), aStep({ id: 11, goalId: 1, status: 'COMPLETED' })];

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(updateStepStatus).mockResolvedValue(undefined as never);
    vi.mocked(updateGoalStatus).mockResolvedValue(undefined as never);
  });

  function context(tasks = [aTask({ id: 100, stepId: 10 }), aTask({ id: 101, stepId: 10, status: 'COMPLETED' })]) {
    return { token: 'token', completedTaskId: 100, tasks, steps, goals: [goal], showToast: vi.fn(), onApplied: vi.fn() };
  }

  it('suggests completing the step when its last open task is completed, and changes nothing yet', () => {
    const ctx = context();
    nudgeAfterTaskComplete(ctx);
    expect(ctx.showToast).toHaveBeenCalledWith('All tasks done — mark step "Search literature" completed?',
      expect.objectContaining({ duration: 8000, action: expect.objectContaining({ label: 'Complete step' }) }));
    expect(updateStepStatus).not.toHaveBeenCalled();
  });

  it('completes the step when chosen, then suggests completing the goal whose steps are now all done', async () => {
    const ctx = context();
    nudgeAfterTaskComplete(ctx);
    ctx.showToast.mock.calls[0][1].action.onClick();
    await vi.waitFor(() => expect(updateStepStatus).toHaveBeenCalledWith('token', 10, 'COMPLETED'));
    await vi.waitFor(() => expect(ctx.onApplied).toHaveBeenCalled());
    expect(ctx.showToast).toHaveBeenLastCalledWith('Every step is done — mark goal "Prepare one concept note" completed?',
      expect.objectContaining({ action: expect.objectContaining({ label: 'Complete goal' }) }));

    ctx.showToast.mock.calls[1][1].action.onClick();
    await vi.waitFor(() => expect(updateGoalStatus).toHaveBeenCalledWith('token', 1, 'COMPLETED'));
  });

  it('says nothing while the step still has open tasks, ignoring archived ones', () => {
    const open = context([aTask({ id: 100, stepId: 10 }), aTask({ id: 101, stepId: 10, status: 'IN_PROGRESS' })]);
    nudgeAfterTaskComplete(open);
    expect(open.showToast).not.toHaveBeenCalled();

    const archived = context([aTask({ id: 100, stepId: 10 }), aTask({ id: 101, stepId: 10, status: 'IN_PROGRESS', archived: true })]);
    nudgeAfterTaskComplete(archived);
    expect(archived.showToast).toHaveBeenCalled();
  });

  it('says nothing for an unknown task, an unknown step, or a step already completed', () => {
    const unknownTask = { ...context(), completedTaskId: 999 };
    nudgeAfterTaskComplete(unknownTask);
    const unknownStep = context([aTask({ id: 100, stepId: 77 })]);
    nudgeAfterTaskComplete(unknownStep);
    const doneStep = { ...context(), steps: [aStep({ id: 10, goalId: 1, status: 'COMPLETED' })] };
    nudgeAfterTaskComplete(doneStep);
    for (const ctx of [unknownTask, unknownStep, doneStep]) {
      expect(ctx.showToast).not.toHaveBeenCalled();
    }
  });

  it('suggests completing a goal only when every other step is done', () => {
    const base = { token: 'token', completedStepId: 10, goals: [goal], showToast: vi.fn(), onApplied: vi.fn() };
    const open = { ...base, steps: [aStep({ id: 10, goalId: 1 }), aStep({ id: 11, goalId: 1, status: 'BLOCKED' })] };
    nudgeAfterStepComplete(open);
    expect(open.showToast).not.toHaveBeenCalled();

    const done = { ...base, showToast: vi.fn(), steps: [aStep({ id: 10, goalId: 1 }), aStep({ id: 11, goalId: 1, status: 'COMPLETED' })] };
    nudgeAfterStepComplete(done);
    expect(done.showToast).toHaveBeenCalled();
  });

  it('says nothing for an unknown step, an unknown goal, or a goal already completed', () => {
    const base = { token: 'token', completedStepId: 10, steps, showToast: vi.fn(), onApplied: vi.fn() };
    const cases = [
      { ...base, completedStepId: 999, goals: [goal] },
      { ...base, showToast: vi.fn(), goals: [aGoal({ id: 2 })] },
      { ...base, showToast: vi.fn(), goals: [aGoal({ id: 1, status: 'COMPLETED' })] },
    ];
    for (const ctx of cases) {
      nudgeAfterStepComplete(ctx);
      expect(ctx.showToast).not.toHaveBeenCalled();
    }
  });
});
