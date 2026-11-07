import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as dreamApi from '../../api/dreamApi';
import * as goalApi from '../../api/goalApi';
import * as stepApi from '../../api/stepApi';
import * as taskApi from '../../api/taskApi';
import { aDream, aGoal, aStep, aTask } from '../../test/builders';
import { renderPage } from '../../test/render';
import type { Dream, Goal, TaskItem, VisionStep } from '../../types/vision';
import { DECISION_CHECKLIST_QUESTIONS } from '../../utils/enumLabels';
import { VisionMapTree } from './VisionMapTree';

vi.mock('../../api/dreamApi');
vi.mock('../../api/goalApi');
vi.mock('../../api/stepApi');
vi.mock('../../api/taskApi');

const ME = { id: 1, fullName: 'Sok Dara', email: 'dara@example.com', role: 'USER' };
const dream = aDream({ id: 1, title: 'Become a researcher', status: 'ACTIVE', priority: 'HIGH', whyImportant: 'Help my community', successDefinition: 'One study' });
const conceptNote = aGoal({ id: 10, dreamId: 1, title: 'Prepare one concept note', sortOrder: 0, progressPercent: 50 });
const tools = aGoal({ id: 11, dreamId: 1, title: 'Learn AI research tools', sortOrder: 1, priority: 'LOW' });
const literature = aStep({ id: 20, goalId: 10, title: 'Search literature', sequenceNumber: 1 });
const rationale = aStep({ id: 21, goalId: 10, title: 'Write rationale', sequenceNumber: 2, complex: true });
const pubmed = aTask({ id: 30, stepId: 20, title: 'Search PubMed', sortOrder: 0 });
const scholar = aTask({ id: 31, stepId: 20, title: 'Search Google Scholar', status: 'COMPLETED', progressPercent: 100, sortOrder: 1 });

function renderTree(options: { dream?: Dream; goals?: Goal[]; steps?: VisionStep[]; tasks?: TaskItem[]; priorityFilter?: string; statusFilter?: string } = {}) {
  const onDataChange = vi.fn().mockResolvedValue(undefined);
  const view = renderPage(
    <VisionMapTree
      dream={options.dream ?? dream}
      visionAreaName="Career"
      goals={options.goals ?? [conceptNote, tools]}
      steps={options.steps ?? [literature, rationale]}
      tasks={options.tasks ?? [pubmed, scholar]}
      token="token"
      onDataChange={onDataChange}
      priorityFilter={options.priorityFilter}
      statusFilter={options.statusFilter}
    />,
    { auth: { user: ME } },
  );
  return { ...view, onDataChange };
}

/**
 * Waits out the tree's deliberate focus moves: a dialog focuses its title
 * 100 ms after opening (FR-22.3), and a saved rename returns focus to its
 * row after 50 ms. A person never acts inside that time; a test can.
 */
const settle = () => new Promise((resolve) => setTimeout(resolve, 150));

const item = (title: string) => screen.getByText(title, { selector: '.map-title' }).closest('[role="treeitem"]') as HTMLElement;
const menu = async (title: string, label: string) => userEvent.click(within(item(title)).getByRole('button', { name: label }));

describe('VisionMapTree: working the plan in the tree (CLAUDE.md page 4)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    for (const module of [dreamApi, goalApi, stepApi, taskApi]) {
      for (const value of Object.values(module)) {
        if (vi.isMockFunction(value)) value.mockResolvedValue(undefined);
      }
    }
    vi.mocked(goalApi.getGoalArchiveImpact).mockResolvedValue({ steps: 2, tasks: 2 } as never);
    vi.mocked(stepApi.getStepArchiveImpact).mockResolvedValue({ tasks: 2 } as never);
    vi.mocked(dreamApi.getDreamArchiveImpact).mockResolvedValue({ goals: 2, steps: 2, tasks: 2 } as never);
  });

  it('shows dream, goals, steps and tasks with progress, and coaching where the plan stops', () => {
    renderTree();
    for (const title of ['Become a researcher', 'Prepare one concept note', 'Learn AI research tools', 'Search literature', 'Write rationale', 'Search PubMed']) {
      expect(item(title)).toBeInTheDocument();
    }
    expect(within(item('Prepare one concept note')).getByText('50%')).toBeInTheDocument();
    expect(screen.getByText('This step is marked complex — break it into tasks so each piece is small enough to do.')).toBeInTheDocument();
    expect(screen.getByText('No steps yet — what has to happen, in order, to reach this goal?')).toBeInTheDocument();
  });

  it('prompts a dream with no goals to break into goals, and adds the first goal', async () => {
    const { onDataChange } = renderTree({ goals: [], steps: [], tasks: [] });
    expect(screen.getByText('Start by breaking this dream into goals — the major results that, together, make it real.')).toBeInTheDocument();
    await userEvent.type(screen.getByRole('textbox', { name: 'New goal title' }), 'Prepare one concept note');
    await userEvent.click(screen.getByRole('button', { name: 'Add' }));
    expect(goalApi.createGoal).toHaveBeenCalledWith('token', expect.objectContaining({ dreamId: 1, title: 'Prepare one concept note' }));
    expect(onDataChange).toHaveBeenCalled();
  });

  it('adds a step under a goal with owner and date, continuing its order', async () => {
    renderTree();
    const [stepTitle] = screen.getAllByRole('textbox', { name: 'New step title' });
    await userEvent.type(stepTitle, 'Define the research gap');
    const [stepDate] = screen.getAllByLabelText('Step target date');
    await userEvent.type(stepDate, '2027-04-01');
    expect(screen.getAllByRole('textbox', { name: 'Step owner' })[0]).toHaveValue('Sok Dara');
    await userEvent.click(within(stepTitle.closest('form') as HTMLElement).getByRole('button', { name: 'Add' }));
    await waitFor(() => expect(stepApi.createStep).toHaveBeenCalledWith('token', expect.objectContaining({
      goalId: 10, title: 'Define the research gap', owner: 'Sok Dara', targetDate: '2027-04-01', sequenceNumber: 3,
    })));
  });

  it('adds a task under a step with owner and due date (BR-5)', async () => {
    renderTree();
    const [taskTitle] = screen.getAllByRole('textbox', { name: 'New task title' });
    const form = within(taskTitle.closest('form') as HTMLElement);
    await userEvent.type(taskTitle, 'Save 15 articles');
    expect(form.getByRole('button', { name: 'Add task' })).toBeDisabled();
    await userEvent.type(form.getByLabelText('Due date'), '2027-02-01');
    await userEvent.click(form.getByRole('button', { name: 'Add task' }));
    await waitFor(() => expect(taskApi.createTask).toHaveBeenCalledWith('token', expect.objectContaining({
      stepId: 20, title: 'Save 15 articles', owner: 'Sok Dara', dueDate: '2027-02-01',
    })));
  });

  it('changes the status of a goal, a step and a task from the row', async () => {
    renderTree();
    const pick = async (title: string, option: string) => {
      await userEvent.click(screen.getByRole('combobox', { name: `Status of ${title}` }));
      await userEvent.click(await screen.findByRole('option', { name: option }));
    };
    await pick('Prepare one concept note', 'Waiting');
    expect(goalApi.updateGoalStatus).toHaveBeenCalledWith('token', 10, 'WAITING');
    await pick('Search literature', 'In Progress');
    expect(stepApi.updateStepStatus).toHaveBeenCalledWith('token', 20, 'IN_PROGRESS');
    await pick('Become a researcher', 'Paused');
    expect(dreamApi.updateDream).toHaveBeenCalledWith('token', 1, expect.objectContaining({ status: 'PAUSED' }));
  });

  it('suggests completing the step when its last task is completed (BR-11)', async () => {
    renderTree();
    await userEvent.click(screen.getByRole('combobox', { name: 'Status of Search PubMed' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Completed' }));
    expect(taskApi.updateTaskStatus).toHaveBeenCalledWith('token', 30, 'COMPLETED');
    expect(await screen.findByRole('button', { name: 'Complete step' })).toBeInTheDocument();
  });

  it('holds a high-priority moonshot dream at the decision gate when set to Active (FR-55.1)', async () => {
    const moonshot = { ...dream, status: 'IDEA' as const, moonshot: true, priority: 'CRITICAL' as const, decisionGateClearedAt: null };
    renderTree({ dream: moonshot });
    await userEvent.click(screen.getByRole('combobox', { name: 'Status of Become a researcher' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Active' }));
    expect(dreamApi.updateDream).not.toHaveBeenCalled();
    const dialog = await screen.findByRole('dialog', { name: 'Edit Dream' });
    for (const question of DECISION_CHECKLIST_QUESTIONS) {
      await userEvent.click(within(within(dialog).getByRole('group', { name: question.label })).getByRole('button', { name: 'No' }));
    }
    expect(within(dialog).getByText('All eight answered — this checklist clears the gate on save.')).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }));
    expect(dreamApi.updateDream).toHaveBeenCalledWith('token', 1, expect.objectContaining({ status: 'ACTIVE', decisionSkippedResearch: false }));
  });

  it('edits the dream, a goal, a step and a task in their forms', async () => {
    renderTree();
    await menu('Become a researcher', 'Dream actions');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Edit' }));
    let dialog = await screen.findByRole('dialog', { name: 'Edit Dream' });
    expect(within(dialog).getByRole('textbox', { name: /^Why Important/ })).toHaveValue('Help my community');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }));
    expect(dreamApi.updateDream).toHaveBeenCalledWith('token', 1, expect.objectContaining({ title: 'Become a researcher' }));

    await menu('Learn AI research tools', 'Goal actions');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Edit' }));
    dialog = await screen.findByRole('dialog', { name: 'Edit Goal' });
    await settle();
    const criteria = within(dialog).getByRole('textbox', { name: /^Success Criteria/ });
    expect(criteria).toHaveValue('Mentor approves it');
    await userEvent.clear(criteria);
    await userEvent.type(criteria, 'Use three tools weekly');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }));
    expect(goalApi.updateGoal).toHaveBeenCalledWith('token', 11, expect.objectContaining({ successCriteria: 'Use three tools weekly' }));

    await menu('Write rationale', 'Step actions');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Edit' }));
    dialog = await screen.findByRole('dialog', { name: 'Edit Step' });
    expect(within(dialog).getByRole('checkbox', { name: /Complex step/ })).toBeChecked();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }));
    expect(stepApi.updateStep).toHaveBeenCalledWith('token', 21, expect.objectContaining({ complex: true }));

    await menu('Search PubMed', 'Task actions');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Edit' }));
    dialog = await screen.findByRole('dialog', { name: 'Edit Task' });
    await userEvent.click(within(dialog).getByRole('combobox', { name: 'Status' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Blocked' }));
    await settle();
    await userEvent.type(within(dialog).getByRole('textbox', { name: /^Blocker Reason/ }), 'Library is closed');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }));
    expect(taskApi.updateTask).toHaveBeenCalledWith('token', 30, expect.objectContaining({ status: 'BLOCKED', blockerReason: 'Library is closed' }));
  });

  it('cancels each edit form without saving', async () => {
    renderTree();
    for (const [title, label, name] of [
      ['Become a researcher', 'Dream actions', 'Edit Dream'], ['Learn AI research tools', 'Goal actions', 'Edit Goal'],
      ['Search literature', 'Step actions', 'Edit Step'], ['Search PubMed', 'Task actions', 'Edit Task'],
    ] as const) {
      await menu(title, label);
      await userEvent.click(screen.getByRole('menuitem', { name: 'Edit' }));
      await userEvent.click(within(await screen.findByRole('dialog', { name })).getByRole('button', { name: 'Cancel' }));
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    }
    expect(dreamApi.updateDream).not.toHaveBeenCalled();
    expect(taskApi.updateTask).not.toHaveBeenCalled();
  });

  it('archives a goal, a step and a task after confirming, showing what goes with them', async () => {
    renderTree();
    await menu('Learn AI research tools', 'Goal actions');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Archive' }));
    expect(await screen.findByText(/also archives 2 step\(s\) and 2 task\(s\)/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Archive' }));
    expect(goalApi.archiveGoal).toHaveBeenCalledWith('token', 11);

    await menu('Search literature', 'Step actions');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Archive' }));
    expect(await screen.findByText(/also archives 2 task\(s\)/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Archive' }));
    expect(stepApi.archiveStep).toHaveBeenCalledWith('token', 20);

    await menu('Search PubMed', 'Task actions');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Archive' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Archive' }));
    expect(taskApi.archiveTask).toHaveBeenCalledWith('token', 30);
  });

  it('restores or permanently deletes archived goals, steps and tasks', async () => {
    renderTree({
      goals: [conceptNote, { ...tools, archived: true }],
      steps: [{ ...literature, archived: true }, rationale],
      tasks: [{ ...pubmed, archived: true }],
    });
    await menu('Learn AI research tools', 'Goal actions');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Restore' }));
    expect(goalApi.restoreGoal).toHaveBeenCalledWith('token', 11);
    await menu('Search literature', 'Step actions');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Delete permanently' }));
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: /Delete/ }));
    expect(stepApi.permanentlyDeleteStep).toHaveBeenCalledWith('token', 20);
    await menu('Search PubMed', 'Task actions');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Restore' }));
    expect(taskApi.restoreTask).toHaveBeenCalledWith('token', 30);
  });

  it('archives and permanently deletes the dream itself', async () => {
    const { unmount } = renderTree();
    await menu('Become a researcher', 'Dream actions');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Archive' }));
    expect(await screen.findByText(/2 goal\(s\)/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Archive' }));
    expect(dreamApi.archiveDream).toHaveBeenCalledWith('token', 1);
    unmount();

    renderTree({ dream: { ...dream, archived: true } });
    await menu('Become a researcher', 'Dream actions');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Restore' }));
    expect(dreamApi.restoreDream).toHaveBeenCalledWith('token', 1);
  });

  it('moves through the tree with the arrow keys, collapses and expands, and renames with Enter', async () => {
    renderTree();
    item('Become a researcher').focus();
    await userEvent.keyboard('{ArrowDown}');
    expect(item('Prepare one concept note')).toHaveFocus();
    await userEvent.keyboard('{ArrowLeft}');
    await waitFor(() => expect(screen.queryByText('Search literature', { selector: '.map-title' })).not.toBeInTheDocument());
    await userEvent.keyboard('{ArrowRight}');
    expect(await screen.findByText('Search literature', { selector: '.map-title' })).toBeInTheDocument();
    await userEvent.keyboard('{ArrowRight}');
    expect(item('Search literature')).toHaveFocus();
    await userEvent.keyboard('{ArrowLeft}{ArrowLeft}');
    expect(item('Prepare one concept note')).toHaveFocus();
    await userEvent.keyboard('{ArrowUp}');
    expect(item('Become a researcher')).toHaveFocus();

    await userEvent.keyboard('{ArrowDown}{Enter}');
    const rename = screen.getByRole('textbox', { name: 'Rename' });
    await userEvent.clear(rename);
    await userEvent.type(rename, 'Prepare a concept note{Enter}');
    expect(goalApi.updateGoal).toHaveBeenCalledWith('token', 10, expect.objectContaining({ title: 'Prepare a concept note' }));
  });

  it('cancels a rename with Escape, and skips saving an unchanged title', async () => {
    renderTree();
    await userEvent.dblClick(screen.getByText('Search literature', { selector: '.map-title' }));
    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('textbox', { name: 'Rename' })).not.toBeInTheDocument();
    await userEvent.dblClick(screen.getByText('Search PubMed', { selector: '.map-title' }));
    await userEvent.keyboard('{Enter}');
    expect(taskApi.updateTask).not.toHaveBeenCalled();
  });

  it('renames a step and a task in place', async () => {
    renderTree();
    await userEvent.dblClick(screen.getByText('Search literature', { selector: '.map-title' }));
    await userEvent.type(screen.getByRole('textbox', { name: 'Rename' }), ' (PubMed first){Enter}');
    expect(stepApi.updateStep).toHaveBeenCalledWith('token', 20, expect.objectContaining({ title: 'Search literature (PubMed first)' }));
    await settle();
    await userEvent.dblClick(screen.getByText('Search PubMed', { selector: '.map-title' }));
    await userEvent.type(screen.getByRole('textbox', { name: 'Rename' }), ' and Scopus{Enter}');
    expect(taskApi.updateTask).toHaveBeenCalledWith('token', 30, expect.objectContaining({ title: 'Search PubMed and Scopus' }));
  });

  it('reorders goals by dragging one onto another', async () => {
    renderTree();
    // jsdom has no drag data, so the events carry a minimal one.
    const data = new Map<string, string>();
    const dataTransfer = { effectAllowed: '', dropEffect: '', setData: (k: string, v: string) => data.set(k, v), getData: (k: string) => data.get(k) ?? '' };
    fireEvent.dragStart(item('Learn AI research tools'), { dataTransfer });
    fireEvent.dragOver(item('Prepare one concept note'), { dataTransfer });
    fireEvent.drop(item('Prepare one concept note'), { dataTransfer });
    await waitFor(() => expect(goalApi.reorderGoals).toHaveBeenCalledWith('token', 1, [11, 10]));
  });

  it('narrows the tree with the page’s filters', () => {
    renderTree({ statusFilter: 'BLOCKED' });
    expect(screen.getByText('No goals match these filters.')).toBeInTheDocument();
  });

  it('shows why a status change failed', async () => {
    vi.mocked(goalApi.updateGoalStatus).mockRejectedValue(new Error('Steps are still open.'));
    renderTree();
    await userEvent.click(screen.getByRole('combobox', { name: 'Status of Prepare one concept note' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Completed' }));
    expect(await screen.findByText('Steps are still open.')).toBeInTheDocument();
  });

  it('permanently deletes an archived dream, goal and task, and restores a step', async () => {
    const onDreamPermanentlyDeleted = vi.fn();
    renderPage(
      <VisionMapTree
        dream={{ ...dream, archived: true }} visionAreaName="Career" token="token" onDataChange={vi.fn().mockResolvedValue(undefined)}
        onDreamPermanentlyDeleted={onDreamPermanentlyDeleted}
        goals={[{ ...conceptNote, archived: true }]} steps={[{ ...literature, archived: true }]} tasks={[{ ...pubmed, archived: true }]}
      />,
      { auth: { user: ME } },
    );
    const deleteVia = async (title: string, label: string) => {
      await menu(title, label);
      await userEvent.click(screen.getByRole('menuitem', { name: 'Delete permanently' }));
      await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: /Delete/ }));
    };
    await deleteVia('Search PubMed', 'Task actions');
    expect(taskApi.permanentlyDeleteTask).toHaveBeenCalledWith('token', 30);
    await deleteVia('Prepare one concept note', 'Goal actions');
    expect(goalApi.permanentlyDeleteGoal).toHaveBeenCalledWith('token', 10);
    await menu('Search literature', 'Step actions');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Restore' }));
    expect(stepApi.restoreStep).toHaveBeenCalledWith('token', 20);
    await deleteVia('Become a researcher', 'Dream actions');
    await waitFor(() => expect(onDreamPermanentlyDeleted).toHaveBeenCalled());
  });

  it('jumps to the add row with "n" on a row', async () => {
    renderTree();
    item('Prepare one concept note').focus();
    await userEvent.keyboard('n');
    await waitFor(() => expect(screen.getAllByRole('textbox', { name: 'New step title' })[0]).toHaveFocus());
    item('Search PubMed').focus();
    await userEvent.keyboard('n');
    await waitFor(() => expect(screen.getAllByRole('textbox', { name: 'New task title' })[0]).toHaveFocus());
  });

  it('shows why an edit, an archive, a restore, a delete or an add failed', async () => {
    vi.mocked(goalApi.updateGoal).mockRejectedValue(new Error('Target date is after the dream.'));
    vi.mocked(stepApi.archiveStep).mockRejectedValue(new Error('Step is in use.'));
    vi.mocked(stepApi.createStep).mockRejectedValue(new Error('Owner is required.'));
    vi.mocked(taskApi.createTask).mockRejectedValue(new Error('Due date is required.'));
    vi.mocked(goalApi.createGoal).mockRejectedValue(new Error('Title is too long.'));
    renderTree();

    await menu('Learn AI research tools', 'Goal actions');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Edit' }));
    await userEvent.click(within(await screen.findByRole('dialog', { name: 'Edit Goal' })).getByRole('button', { name: 'Save changes' }));
    expect(await screen.findByText('Target date is after the dream.')).toBeInTheDocument();
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancel' }));

    await menu('Search literature', 'Step actions');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Archive' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Archive' }));
    expect(await screen.findByText('Step is in use.')).toBeInTheDocument();

    const [stepTitle] = screen.getAllByRole('textbox', { name: 'New step title' });
    await userEvent.type(stepTitle, 'Draft methodology');
    await userEvent.type(screen.getAllByLabelText('Step target date')[0], '2027-04-01');
    await userEvent.click(within(stepTitle.closest('form') as HTMLElement).getByRole('button', { name: 'Add' }));
    expect(await screen.findByText('Owner is required.')).toBeInTheDocument();

    const [taskTitle] = screen.getAllByRole('textbox', { name: 'New task title' });
    const taskForm = within(taskTitle.closest('form') as HTMLElement);
    await userEvent.type(taskTitle, 'Save articles');
    await userEvent.type(taskForm.getByLabelText('Due date'), '2027-02-01');
    await userEvent.click(taskForm.getByRole('button', { name: 'Add task' }));
    expect(await screen.findByText('Due date is required.')).toBeInTheDocument();

    const goalTitle = screen.getByRole('textbox', { name: 'New goal title' });
    await userEvent.type(goalTitle, 'x');
    await userEvent.click(within(goalTitle.closest('form') as HTMLElement).getByRole('button', { name: 'Add' }));
    expect(await screen.findByText('Title is too long.')).toBeInTheDocument();
  });

  it('shows why the dream’s own changes failed', async () => {
    vi.mocked(dreamApi.updateDream).mockRejectedValue(new Error('Target date is before its goals.'));
    vi.mocked(dreamApi.archiveDream).mockRejectedValue(new Error('Dream is shared.'));
    renderTree();
    await menu('Become a researcher', 'Dream actions');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Edit' }));
    await userEvent.click(within(await screen.findByRole('dialog', { name: 'Edit Dream' })).getByRole('button', { name: 'Save changes' }));
    expect(await screen.findByText('Target date is before its goals.')).toBeInTheDocument();
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancel' }));
    await menu('Become a researcher', 'Dream actions');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Archive' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Archive' }));
    expect(await screen.findByText('Dream is shared.')).toBeInTheDocument();
  });
});
