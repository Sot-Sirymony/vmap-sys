import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as dreamApi from '../api/dreamApi';
import * as goalApi from '../api/goalApi';
import * as idealPartnerProfileApi from '../api/idealPartnerProfileApi';
import * as stepApi from '../api/stepApi';
import * as taskApi from '../api/taskApi';
import * as visionAreaApi from '../api/visionAreaApi';
import { answerEmptyLists } from '../test/apiMocks';
import { aDream, aGoal, aStep, aTask, aVisionArea } from '../test/builders';
import { currentLocation, renderPage } from '../test/render';
import { TasksBoardPage } from './TasksBoardPage';

vi.mock('../api/dreamApi');
vi.mock('../api/goalApi');
vi.mock('../api/idealPartnerProfileApi');
vi.mock('../api/stepApi');
vi.mock('../api/taskApi');
vi.mock('../api/visionAreaApi');

const ME = { id: 1, fullName: 'Sok Dara', email: 'dara@example.com', role: 'USER' };
const steps = [aStep({ id: 1, goalId: 1, title: 'Search literature' }), aStep({ id: 2, goalId: 2, title: 'Book a venue' })];
const pubmed = aTask({ id: 10, code: 'T-010', stepId: 1, title: 'Search PubMed', owner: 'Sok Dara', dueDate: '2020-01-31', priority: 'HIGH', sortOrder: 0 });
const scholar = aTask({ id: 11, code: 'T-011', stepId: 1, title: 'Search Google Scholar', owner: 'Sok Dara', dueDate: '2099-02-01', priority: 'MEDIUM', sortOrder: 1 });
const venue = aTask({ id: 12, code: 'T-012', stepId: 2, title: 'Call the venue', owner: 'Mentor', dueDate: '2099-03-01', priority: 'LOW', status: 'BLOCKED', blockerReason: 'No budget approved', sortOrder: 0 });

const row = (title: string) => screen.getByText(title, { selector: 'td *, td' }).closest('tr') as HTMLElement;
const openActions = (title: string) => userEvent.click(within(row(title)).getByRole('button', { name: 'Task actions' }));

describe('TasksBoardPage (CLAUDE.md page 7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    answerEmptyLists([dreamApi, goalApi, idealPartnerProfileApi, stepApi, taskApi, visionAreaApi]);
    vi.mocked(taskApi.listTasks).mockResolvedValue([pubmed, scholar, venue]);
    vi.mocked(stepApi.listSteps).mockResolvedValue(steps);
    vi.mocked(goalApi.listGoals).mockResolvedValue([aGoal({ id: 1, dreamId: 1, title: 'Prepare one concept note' }), aGoal({ id: 2, dreamId: 2, title: 'Host a workshop' })]);
    vi.mocked(dreamApi.listDreams).mockResolvedValue([aDream({ id: 1, visionAreaId: 1 }), aDream({ id: 2, visionAreaId: 1, title: 'Lead a team' })]);
    vi.mocked(visionAreaApi.listVisionAreas).mockResolvedValue([aVisionArea({ id: 1 })]);
    vi.mocked(taskApi.createTask).mockResolvedValue(scholar);
    vi.mocked(taskApi.updateTask).mockResolvedValue(scholar);
    vi.mocked(taskApi.updateTaskStatus).mockResolvedValue(scholar);
    vi.mocked(taskApi.archiveTask).mockResolvedValue(undefined);
    vi.mocked(taskApi.reorderTaskList).mockResolvedValue(undefined as never);
  });

  it('lists tasks with owner and due date, highlighting overdue and blocked ones (BR-9)', async () => {
    renderPage(<TasksBoardPage />, { auth: { user: ME } });
    expect(await screen.findByText('Search PubMed', { selector: 'td *, td' })).toBeInTheDocument();
    expect(within(row('Search PubMed')).getAllByText('Sok Dara').length).toBeGreaterThan(0);
    await userEvent.click(screen.getByRole('button', { name: 'Board' }));
    expect(screen.getByText('Search PubMed').closest('article')).toHaveClass('list-card--overdue');
    expect(screen.getByText('Call the venue').closest('article')).toHaveClass('list-card--blocked');
    expect(screen.getByText('No budget approved')).toBeInTheDocument();
    expect(screen.getByText('Search Google Scholar').closest('article')).not.toHaveClass('list-card--overdue');
  });

  it('shows the six Kanban columns and moves a task between them', async () => {
    renderPage(<TasksBoardPage />, { auth: { user: ME } });
    await screen.findByText('Search PubMed', { selector: 'td *, td' });
    await userEvent.click(screen.getByRole('button', { name: 'Board' }));
    for (const column of ['Not Started', 'In Progress', 'Waiting', 'Blocked', 'Completed', 'Paused']) {
      expect(screen.getByRole('heading', { level: 2, name: new RegExp(`^${column}`) })).toBeInTheDocument();
    }
    const card = screen.getByText('Search Google Scholar').closest('article') as HTMLElement;
    await userEvent.click(within(card).getByRole('combobox', { name: 'Move to status' }));
    await userEvent.click(await screen.findByRole('option', { name: 'In Progress' }));
    expect(taskApi.updateTaskStatus).toHaveBeenCalledWith('token', 11, 'IN_PROGRESS');
  });

  it('creates a task with the required owner, due date, priority and status, owned by me by default (BR-5, FR-22.2)', async () => {
    renderPage(<TasksBoardPage />, { auth: { user: ME } });
    await screen.findByText('Search PubMed', { selector: 'td *, td' });
    await userEvent.click(screen.getByRole('button', { name: 'Create task' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByRole('textbox', { name: /^Owner/ })).toHaveValue('Sok Dara');
    await userEvent.type(within(dialog).getByRole('textbox', { name: /^Title/ }), 'Save 15 articles');
    await userEvent.type(within(dialog).getByLabelText(/^Due Date/), '2027-02-15');
    await userEvent.click(within(dialog).getAllByRole('button', { name: 'Create task' }).at(-1)!);
    await waitFor(() => expect(taskApi.createTask).toHaveBeenCalledWith('token', expect.objectContaining({
      stepId: 1, title: 'Save 15 articles', owner: 'Sok Dara', dueDate: '2027-02-15', priority: 'HIGH', status: 'NOT_STARTED', energyDemand: 'NEUTRAL',
    })));
  });

  it('needs a blocker reason for a blocked task, and suggests who could help by what is missing (BR-6)', async () => {
    renderPage(<TasksBoardPage />, { auth: { user: ME } });
    await screen.findByText('Search PubMed', { selector: 'td *, td' });
    await userEvent.click(screen.getByRole('button', { name: 'Create task' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).queryByText(/^Blocker Reason/)).not.toBeInTheDocument();
    await userEvent.type(within(dialog).getByRole('textbox', { name: /^Title/ }), 'Buy a statistics licence');
    await userEvent.type(within(dialog).getByLabelText(/^Due Date/), '2027-02-15');
    await userEvent.click(within(dialog).getByRole('combobox', { name: 'Status' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Blocked' }));

    await userEvent.click(within(dialog).getByRole('combobox', { name: "What's missing?" }));
    await userEvent.click(await screen.findByRole('option', { name: 'Money' }));
    expect(within(dialog).getByText('Financial partner or sponsor')).toBeInTheDocument();

    const reason = within(dialog).getByRole('textbox', { name: /^Blocker Reason/ });
    expect(reason).toBeRequired();
    await userEvent.click(within(dialog).getAllByRole('button', { name: 'Create task' }).at(-1)!);
    expect(taskApi.createTask).not.toHaveBeenCalled();

    await userEvent.type(reason, 'No budget for the licence');
    await userEvent.click(within(dialog).getAllByRole('button', { name: 'Create task' }).at(-1)!);
    await waitFor(() => expect(taskApi.createTask).toHaveBeenCalledWith('token', expect.objectContaining({ status: 'BLOCKED', blockerReason: 'No budget for the licence' })));
  });

  it('opens a new task under a step from a link, with a suggested title', async () => {
    renderPage(<TasksBoardPage />, { auth: { user: ME }, route: '/tasks?create=task&parent=2&title=Email%20the%20venue' });
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByRole('textbox', { name: /^Title/ })).toHaveValue('Email the venue');
    await waitFor(() => expect(currentLocation()).toBe('/tasks'));
  });

  it('quick-adds a task owned by me with a due date (FR-22.1)', async () => {
    renderPage(<TasksBoardPage />, { auth: { user: ME } });
    await screen.findByText('Search PubMed', { selector: 'td *, td' });
    await userEvent.type(screen.getByPlaceholderText('New task title'), 'Summarize findings');
    await userEvent.type(screen.getByLabelText(/^Due date/), '2027-03-01');
    await userEvent.click(screen.getByRole('button', { name: 'Add' }));
    await waitFor(() => expect(taskApi.createTask).toHaveBeenCalledWith('token', expect.objectContaining({
      stepId: 1, title: 'Summarize findings', owner: 'Sok Dara', dueDate: '2027-03-01',
    })));
  });

  it('edits a task, and suggests completing its step when its last task is completed (BR-11)', async () => {
    vi.mocked(taskApi.listTasks).mockResolvedValue([pubmed, { ...scholar, status: 'COMPLETED' }, venue]);
    renderPage(<TasksBoardPage />, { auth: { user: ME } });
    await screen.findByText('Search PubMed', { selector: 'td *, td' });
    await openActions('Search PubMed');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Edit' }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit Task' });
    expect(within(dialog).getByRole('textbox', { name: /^Title/ })).toHaveValue('Search PubMed');
    await userEvent.click(within(dialog).getByRole('combobox', { name: 'Status' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Completed' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }));
    expect(taskApi.updateTask).toHaveBeenCalledWith('token', 10, expect.objectContaining({ status: 'COMPLETED' }));
    expect(await screen.findByRole('button', { name: 'Complete step' })).toBeInTheDocument();
  });

  it('archives a task after confirming', async () => {
    renderPage(<TasksBoardPage />, { auth: { user: ME } });
    await screen.findByText('Search Google Scholar', { selector: 'td *, td' });
    await openActions('Search Google Scholar');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Archive' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Archive' }));
    expect(taskApi.archiveTask).toHaveBeenCalledWith('token', 11);
  });

  it('filters by owner, overdue, one step, and search (CLAUDE.md page 7 filters)', async () => {
    renderPage(<TasksBoardPage />, { auth: { user: ME } });
    await screen.findByText('Search PubMed', { selector: 'td *, td' });
    await userEvent.type(screen.getByPlaceholderText('Any owner'), 'mentor');
    expect(screen.getByText('Call the venue', { selector: 'td *, td' })).toBeInTheDocument();
    expect(screen.queryByText('Search PubMed', { selector: 'td *, td' })).not.toBeInTheDocument();
    await userEvent.clear(screen.getByPlaceholderText('Any owner'));
    await userEvent.click(screen.getByRole('checkbox', { name: 'Overdue only' }));
    expect(screen.getByText('Search PubMed', { selector: 'td *, td' })).toBeInTheDocument();
    expect(screen.queryByText('Search Google Scholar', { selector: 'td *, td' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('checkbox', { name: 'Overdue only' }));
    await userEvent.type(screen.getByRole('searchbox', { name: 'Search tasks' }), 'budget');
    expect(screen.getByText('Call the venue', { selector: 'td *, td' })).toBeInTheDocument();
    expect(screen.queryByText('Search PubMed', { selector: 'td *, td' })).not.toBeInTheDocument();
  });

  it('shows one step’s tasks from the address, and my overdue tasks in one click', async () => {
    const { unmount } = renderPage(<TasksBoardPage />, { auth: { user: ME }, route: '/tasks?stepId=2' });
    expect(await screen.findByText('Call the venue', { selector: 'td *, td' })).toBeInTheDocument();
    expect(screen.queryByText('Search PubMed', { selector: 'td *, td' })).not.toBeInTheDocument();
    unmount();

    renderPage(<TasksBoardPage />, { auth: { user: ME } });
    await screen.findByText('Search PubMed', { selector: 'td *, td' });
    await userEvent.click(screen.getByText(/· My overdue/));
    expect(screen.getByText('Search PubMed', { selector: 'td *, td' })).toBeInTheDocument();
    expect(screen.queryByText('Call the venue', { selector: 'td *, td' })).not.toBeInTheDocument();
  });

  it('sets the status or priority of selected tasks together', async () => {
    renderPage(<TasksBoardPage />, { auth: { user: ME } });
    await screen.findByText('Search PubMed', { selector: 'td *, td' });
    await userEvent.click(screen.getByRole('checkbox', { name: 'Select Search PubMed' }));
    await userEvent.click(screen.getByRole('checkbox', { name: 'Select Search Google Scholar' }));
    await userEvent.click(screen.getByRole('combobox', { name: 'Set status' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Waiting' }));
    await userEvent.click(screen.getAllByRole('button', { name: 'Apply' })[0]);
    await waitFor(() => expect(taskApi.updateTaskStatus).toHaveBeenCalledTimes(2));
    expect(await screen.findByText('Updated 2 tasks.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('checkbox', { name: 'Select Call the venue' }));
    await userEvent.click(screen.getByRole('combobox', { name: 'Set priority' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Critical' }));
    await userEvent.click(screen.getAllByRole('button', { name: 'Apply' })[1]);
    await waitFor(() => expect(taskApi.updateTask).toHaveBeenCalledWith('token', 12, expect.objectContaining({ priority: 'CRITICAL' })));
  });

  it('puts any task first, whatever its step, and shows why a move failed', async () => {
    vi.mocked(taskApi.updateTaskStatus).mockRejectedValue(new Error('A blocked task needs a reason.'));
    renderPage(<TasksBoardPage />, { auth: { user: ME } });
    await screen.findByText('Search PubMed', { selector: 'td *, td' });
    // No order saved yet, so the list starts oldest first: 10, 11, 12.
    // "Call the venue" (another step) dropped onto the first task goes first.
    fireEvent.dragStart(row('Call the venue'));
    fireEvent.dragOver(row('Search PubMed'));
    fireEvent.drop(row('Search PubMed'));
    await waitFor(() => expect(taskApi.reorderTaskList).toHaveBeenCalledWith('token', [12, 10, 11]));

    await userEvent.click(screen.getByRole('button', { name: 'Board' }));
    const card = screen.getByText('Search Google Scholar').closest('article') as HTMLElement;
    await userEvent.click(within(card).getByRole('combobox', { name: 'Move to status' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Blocked' }));
    expect(await screen.findByText('A blocked task needs a reason.')).toBeInTheDocument();
  });

  it('points a blocked task to its step’s ideal partner profile', async () => {
    vi.mocked(idealPartnerProfileApi.listIdealPartnerProfiles).mockResolvedValue([{ id: 5, stepId: 2, archived: false }]);
    renderPage(<TasksBoardPage />, { auth: { user: ME } });
    await screen.findByText('Call the venue', { selector: 'td *, td' });
    await userEvent.click(screen.getByRole('button', { name: 'Board' }));
    expect(await screen.findByRole('link', { name: 'Ideal partner profile defined for this step →' })).toHaveAttribute('href', '/partners');
  });

  it('starts with an explanation when there are no tasks', async () => {
    vi.mocked(taskApi.listTasks).mockResolvedValue([]);
    renderPage(<TasksBoardPage />, { auth: { user: ME } });
    expect(await screen.findByText('No tasks yet')).toBeInTheDocument();
  });

  it('opens the create form from the dashboard’s "New Task" link, once', async () => {
    renderPage(<TasksBoardPage />, { auth: { user: ME }, route: '/tasks?new=true' });
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    await waitFor(() => expect(currentLocation()).toBe('/tasks'));
  });

  it('filters by due date range, priority, status, dream, goal and vision area', async () => {
    renderPage(<TasksBoardPage />, { auth: { user: ME } });
    await screen.findByText('Search PubMed', { selector: 'td *, td' });
    await userEvent.type(screen.getByLabelText(/^Due from/), '2099-02-15');
    expect(screen.getByText('Call the venue', { selector: 'td *, td' })).toBeInTheDocument();
    expect(screen.queryByText('Search Google Scholar', { selector: 'td *, td' })).not.toBeInTheDocument();
    await userEvent.clear(screen.getByLabelText(/^Due from/));
    await userEvent.type(screen.getByLabelText(/^Due to/), '2021-01-01');
    expect(screen.getByText('Search PubMed', { selector: 'td *, td' })).toBeInTheDocument();
    expect(screen.queryByText('Call the venue', { selector: 'td *, td' })).not.toBeInTheDocument();
    await userEvent.clear(screen.getByLabelText(/^Due to/));

    const pick = async (label: string, option: string) => {
      await userEvent.click(within(screen.getByText(label, { selector: 'label' })).getByRole('combobox'));
      await userEvent.click(await screen.findByRole('option', { name: option }));
    };
    await pick('Status', 'Blocked');
    expect(screen.getByText('Call the venue', { selector: 'td *, td' })).toBeInTheDocument();
    expect(screen.queryByText('Search PubMed', { selector: 'td *, td' })).not.toBeInTheDocument();
  });

  it('narrows to a dream, a goal or a vision area from the address', async () => {
    const first = renderPage(<TasksBoardPage />, { auth: { user: ME }, route: '/tasks?dreamId=2' });
    expect(await screen.findByText('Call the venue', { selector: 'td *, td' })).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText('Search PubMed', { selector: 'td *, td' })).not.toBeInTheDocument());
    first.unmount();
    const second = renderPage(<TasksBoardPage />, { auth: { user: ME }, route: '/tasks?goalId=1' });
    expect(await screen.findByText('Search PubMed', { selector: 'td *, td' })).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText('Call the venue', { selector: 'td *, td' })).not.toBeInTheDocument());
    second.unmount();
    renderPage(<TasksBoardPage />, { auth: { user: ME }, route: '/tasks?visionAreaId=9' });
    expect(await screen.findByText('No tasks match these filters.')).toBeInTheDocument();
  });

  it('shows tasks due this week in one click, and cancels an edit', async () => {
    const soon = new Date();
    soon.setDate(soon.getDate() + 2);
    const iso = soon.toISOString().slice(0, 10);
    vi.mocked(taskApi.listTasks).mockResolvedValue([pubmed, { ...scholar, dueDate: iso }, venue]);
    renderPage(<TasksBoardPage />, { auth: { user: ME } });
    await screen.findByText('Search PubMed', { selector: 'td *, td' });
    await userEvent.click(screen.getByText(/· Due this week/));
    expect(screen.getByText('Search Google Scholar', { selector: 'td *, td' })).toBeInTheDocument();
    expect(screen.queryByText('Call the venue', { selector: 'td *, td' })).not.toBeInTheDocument();

    await openActions('Search Google Scholar');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Edit' }));
    await userEvent.click(within(await screen.findByRole('dialog', { name: 'Edit Task' })).getByRole('button', { name: 'Cancel' }));
    expect(taskApi.updateTask).not.toHaveBeenCalled();
  });

  it('edits and archives a task from its board card', async () => {
    renderPage(<TasksBoardPage />, { auth: { user: ME } });
    await screen.findByText('Search Google Scholar', { selector: 'td *, td' });
    await userEvent.click(screen.getByRole('button', { name: 'Board' }));
    const card = screen.getByText('Search Google Scholar').closest('article') as HTMLElement;
    await userEvent.click(within(card).getByRole('button', { name: 'Task actions' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Archive' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Archive' }));
    expect(taskApi.archiveTask).toHaveBeenCalledWith('token', 11);
  });

  it('shows why a quick add or a bulk update failed', async () => {
    vi.mocked(taskApi.createTask).mockRejectedValue(new Error('Due date is required.'));
    vi.mocked(taskApi.updateTaskStatus).mockRejectedValue(new Error('A blocked task needs a reason.'));
    renderPage(<TasksBoardPage />, { auth: { user: ME } });
    await screen.findByText('Search PubMed', { selector: 'td *, td' });
    await userEvent.type(screen.getByPlaceholderText('New task title'), 'x');
    await userEvent.type(screen.getByLabelText(/^Due date/), '2027-03-01');
    await userEvent.click(screen.getByRole('button', { name: 'Add' }));
    expect(await screen.findByText('Due date is required.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('checkbox', { name: 'Select Search PubMed' }));
    await userEvent.click(screen.getAllByRole('button', { name: 'Apply' })[0]);
    expect(await screen.findByText('A blocked task needs a reason.')).toBeInTheDocument();
  });
});
