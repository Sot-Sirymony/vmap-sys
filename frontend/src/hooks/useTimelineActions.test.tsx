import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as dreamApi from '../api/dreamApi';
import * as goalApi from '../api/goalApi';
import * as stepApi from '../api/stepApi';
import * as taskApi from '../api/taskApi';
import { Button } from '../components/common/Button';
import { aGoal, aStep, aTask } from '../test/builders';
import { renderPage } from '../test/render';
import { useTimelineActions, type TimelineActionTarget } from './useTimelineActions';

vi.mock('../api/dreamApi');
vi.mock('../api/goalApi');
vi.mock('../api/stepApi');
vi.mock('../api/taskApi');

const steps = [aStep({ id: 20, goalId: 10, sequenceNumber: 1 }), aStep({ id: 21, goalId: 10, sequenceNumber: 4 })];
const dream: TimelineActionTarget = { kind: 'dream', id: 1, dreamId: 1, title: 'Become a researcher' };
const goal: TimelineActionTarget = { kind: 'goal', id: 10, dreamId: 1, title: 'Prepare a concept note' };
const step: TimelineActionTarget = { kind: 'step', id: 20, dreamId: 1, title: 'Search literature' };
const task: TimelineActionTarget = { kind: 'task', id: 31, dreamId: 1, title: 'Summarise findings' };

function Harness({ onChanged, onDreamArchived }: { onChanged: () => Promise<void>; onDreamArchived?: (id: number) => void }) {
  const actions = useTimelineActions({ token: 'token', steps, onChanged, onDreamArchived });
  return (
    <>
      {[dream, goal, step, task].map((target) => (
        <span key={target.kind}>
          <Button onClick={() => actions.addChild(target)}>{`add to ${target.kind}`}</Button>
          <Button onClick={() => void actions.archive(target)}>{`archive ${target.kind}`}</Button>
          <Button onClick={() => void actions.restore(target)}>{`restore ${target.kind}`}</Button>
        </span>
      ))}
      {actions.dialogs}
    </>
  );
}

function setup(onDreamArchived?: (id: number) => void) {
  const onChanged = vi.fn().mockResolvedValue(undefined);
  renderPage(<Harness onChanged={onChanged} onDreamArchived={onDreamArchived} />);
  return onChanged;
}

describe('useTimelineActions', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(goalApi.createGoal).mockResolvedValue(aGoal());
    vi.mocked(stepApi.createStep).mockResolvedValue(aStep());
    vi.mocked(taskApi.createTask).mockResolvedValue(aTask());
    vi.mocked(dreamApi.getDreamArchiveImpact).mockResolvedValue({ goals: 2, steps: 3, tasks: 4 } as never);
    vi.mocked(goalApi.getGoalArchiveImpact).mockResolvedValue({ steps: 3, tasks: 4 } as never);
  });

  it('adds a goal to a dream without leaving the timeline', async () => {
    const onChanged = setup();
    await userEvent.click(screen.getByRole('button', { name: 'add to dream' }));
    const dialog = within(screen.getByRole('dialog'));
    await userEvent.type(dialog.getByRole('textbox', { name: 'Title' }), 'Learn AI tools');
    await userEvent.click(dialog.getByRole('button', { name: 'Add a goal' }));
    expect(goalApi.createGoal).toHaveBeenCalledWith('token', expect.objectContaining({ dreamId: 1, title: 'Learn AI tools', status: 'NOT_STARTED' }));
    expect(onChanged).toHaveBeenCalled();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('adds a step after its siblings, owned by the signed-in user', async () => {
    setup();
    await userEvent.click(screen.getByRole('button', { name: 'add to goal' }));
    const dialog = within(screen.getByRole('dialog'));
    await userEvent.type(dialog.getByRole('textbox', { name: 'Title' }), 'Define the gap');
    const submit = dialog.getByRole('button', { name: 'Add a step' });
    // FR-64: a step needs a date before it can be saved.
    expect(submit).toBeDisabled();
    await userEvent.type(dialog.getByLabelText(/^Target Date/), '2027-01-15');
    await userEvent.click(submit);
    expect(stepApi.createStep).toHaveBeenCalledWith('token', expect.objectContaining({
      goalId: 10, title: 'Define the gap', owner: 'Test Participant', sequenceNumber: 5, targetDate: '2027-01-15',
    }));
  });

  it('adds a task with owner and due date, and shows a refusal from the server', async () => {
    vi.mocked(taskApi.createTask).mockRejectedValueOnce(new Error('Due date is after the step target date.'));
    setup();
    await userEvent.click(screen.getByRole('button', { name: 'add to step' }));
    const dialog = within(screen.getByRole('dialog'));
    await userEvent.type(dialog.getByRole('textbox', { name: 'Title' }), 'Search PubMed');
    await userEvent.type(dialog.getByLabelText(/^Due Date/), '2026-12-10');
    await userEvent.click(dialog.getByRole('button', { name: 'Add a task' }));
    expect(taskApi.createTask).toHaveBeenCalledWith('token', expect.objectContaining({ stepId: 20, title: 'Search PubMed', owner: 'Test Participant', dueDate: '2026-12-10' }));
    expect(await dialog.findByText('Due date is after the step target date.')).toBeInTheDocument();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('archives after confirming, shows what else is archived, and offers Undo', async () => {
    const onChanged = setup();
    await userEvent.click(screen.getByRole('button', { name: 'archive goal' }));
    expect(await screen.findByText(/also archives 3 step\(s\) and 4 task\(s\)/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Archive' }));
    expect(goalApi.archiveGoal).toHaveBeenCalledWith('token', 10);
    expect(onChanged).toHaveBeenCalled();
    await userEvent.click(await screen.findByRole('button', { name: 'Undo' }));
    expect(goalApi.restoreGoal).toHaveBeenCalledWith('token', 10);
  });

  it('tells the page when a dream is archived', async () => {
    const onDreamArchived = vi.fn();
    setup(onDreamArchived);
    await userEvent.click(screen.getByRole('button', { name: 'archive dream' }));
    await screen.findByText(/also archives 2 goal\(s\), 3 step\(s\), and 4 task\(s\)/);
    await userEvent.click(screen.getByRole('button', { name: 'Archive' }));
    await waitFor(() => expect(onDreamArchived).toHaveBeenCalledWith(1));
    expect(dreamApi.archiveDream).toHaveBeenCalledWith('token', 1);
  });

  it('archives and restores a task', async () => {
    setup();
    await userEvent.click(screen.getByRole('button', { name: 'archive task' }));
    await userEvent.click(screen.getByRole('button', { name: 'Archive' }));
    expect(taskApi.archiveTask).toHaveBeenCalledWith('token', 31);
    await userEvent.click(screen.getByRole('button', { name: 'restore step' }));
    expect(stepApi.restoreStep).toHaveBeenCalledWith('token', 20);
  });

  it('does not archive when cancelled', async () => {
    setup();
    await userEvent.click(screen.getByRole('button', { name: 'archive step' }));
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(stepApi.archiveStep).not.toHaveBeenCalled();
  });
});
