import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as dreamApi from '../api/dreamApi';
import * as goalApi from '../api/goalApi';
import * as idealPartnerProfileApi from '../api/idealPartnerProfileApi';
import * as stepApi from '../api/stepApi';
import * as taskApi from '../api/taskApi';
import * as visionAreaApi from '../api/visionAreaApi';
import * as workStyleProfileApi from '../api/workStyleProfileApi';
import { answerEmptyLists } from '../test/apiMocks';
import { aDream, aGoal, aStep, aTask, aVisionArea } from '../test/builders';
import { currentLocation, renderPage } from '../test/render';
import type { IdealPartnerProfile, WorkStyleProfile } from '../types/vision';
import { StepsPage } from './StepsPage';

vi.mock('../api/dreamApi');
vi.mock('../api/goalApi');
vi.mock('../api/idealPartnerProfileApi');
vi.mock('../api/stepApi');
vi.mock('../api/taskApi');
vi.mock('../api/visionAreaApi');
vi.mock('../api/workStyleProfileApi');

const goals = [aGoal({ id: 1, dreamId: 1, title: 'Prepare one concept note' }), aGoal({ id: 2, dreamId: 1, title: 'Learn AI research tools' })];
const selectTopic = aStep({ id: 10, code: 'S-010', goalId: 1, title: 'Select topic', sequenceNumber: 1, status: 'COMPLETED', progressPercent: 100 });
const searchLiterature = aStep({ id: 11, code: 'S-011', goalId: 1, title: 'Search literature', sequenceNumber: 2, complex: true, targetDate: '2020-02-01' });
const writeRationale = aStep({ id: 12, code: 'S-012', goalId: 1, title: 'Write rationale', sequenceNumber: 3, complex: true });
const tryTools = aStep({ id: 13, code: 'S-013', goalId: 2, title: 'Try three AI tools', sequenceNumber: 1 });

const row = (title: string) => screen.getByText(title, { selector: 'td *, td' }).closest('tr') as HTMLElement;
const openActions = (title: string) => userEvent.click(within(row(title)).getByRole('button', { name: 'Step actions' }));

describe('StepsPage (CLAUDE.md page 6)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    answerEmptyLists([dreamApi, goalApi, idealPartnerProfileApi, stepApi, taskApi, visionAreaApi]);
    vi.mocked(stepApi.listSteps).mockResolvedValue([selectTopic, searchLiterature, writeRationale, tryTools]);
    vi.mocked(goalApi.listGoals).mockResolvedValue(goals);
    vi.mocked(dreamApi.listDreams).mockResolvedValue([aDream({ id: 1, title: 'Become a researcher' })]);
    vi.mocked(visionAreaApi.listVisionAreas).mockResolvedValue([aVisionArea({ id: 1 })]);
    vi.mocked(taskApi.listTasks).mockResolvedValue([aTask({ id: 1, stepId: 12 }), aTask({ id: 2, stepId: 10, status: 'COMPLETED' })]);
    vi.mocked(workStyleProfileApi.getWorkStyleProfile).mockResolvedValue({ dominant: null } as WorkStyleProfile);
    vi.mocked(stepApi.createStep).mockResolvedValue(tryTools);
    vi.mocked(stepApi.updateStep).mockResolvedValue(tryTools);
    vi.mocked(stepApi.updateStepStatus).mockResolvedValue(tryTools as never);
    vi.mocked(stepApi.archiveStep).mockResolvedValue(undefined);
    vi.mocked(stepApi.reorderSteps).mockResolvedValue(undefined as never);
    vi.mocked(stepApi.getStepArchiveImpact).mockResolvedValue({ tasks: 4 } as never);
  });

  it('lists steps in order with their task count, and prompts to break a complex step with no tasks into tasks (BR-7)', async () => {
    renderPage(<StepsPage />);
    expect(await screen.findByText('Search literature', { selector: 'td *, td' })).toBeInTheDocument();
    const literature = within(row('Search literature'));
    expect(literature.getByText('Complex step, no tasks yet')).toBeInTheDocument();
    expect(literature.getByRole('link', { name: 'Break this into tasks →' })).toHaveAttribute('href', '/tasks?stepId=11');
    expect(within(row('Write rationale')).queryByText('Complex step, no tasks yet')).not.toBeInTheDocument();
    expect(within(row('Select topic')).queryByText('Complex step, no tasks yet')).not.toBeInTheDocument();
  });

  it('creates a step under a goal with owner, order, target date and the complex flag, then continues the order', async () => {
    renderPage(<StepsPage />, { auth: { user: { id: 1, fullName: 'Sok Dara', email: 'dara@example.com', role: 'USER' } } });
    await screen.findByText('Search literature', { selector: 'td *, td' });
    await userEvent.click(screen.getByRole('button', { name: 'Create step' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByRole('textbox', { name: /^Owner/ })).toHaveValue('Sok Dara');
    await userEvent.type(within(dialog).getByRole('textbox', { name: /^Title/ }), 'Define research gap');
    const sequence = within(dialog).getByRole('spinbutton');
    await userEvent.clear(sequence);
    await userEvent.type(sequence, '4');
    await userEvent.type(within(dialog).getByLabelText(/^Target Date/), '2027-03-15');
    await userEvent.click(within(dialog).getByRole('checkbox', { name: /Complex step/ }));
    await userEvent.type(within(dialog).getByPlaceholderText('Money, tools, skills, information, and who could help'), 'Library access');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save & add another' }));
    await waitFor(() => expect(stepApi.createStep).toHaveBeenCalledWith('token', expect.objectContaining({
      goalId: 1, title: 'Define research gap', owner: 'Sok Dara', sequenceNumber: 4, targetDate: '2027-03-15', complex: true, resources: 'Library access',
    })));
    await waitFor(() => expect(within(screen.getByRole('dialog')).getByRole('spinbutton')).toHaveValue(5));
  });

  it('opens a new step under a goal from a link', async () => {
    renderPage(<StepsPage />, { route: '/steps?create=step&parent=2' });
    const dialog = await screen.findByRole('dialog');
    await waitFor(() => expect(within(dialog).getByRole('combobox', { name: 'Goal' })).toHaveTextContent('Learn AI research tools'));
  });

  it('quick-adds a step that continues the goal’s order (FR-22.1)', async () => {
    renderPage(<StepsPage />, { auth: { user: { id: 1, fullName: 'Sok Dara', email: 'dara@example.com', role: 'USER' } } });
    await screen.findByText('Search literature', { selector: 'td *, td' });
    await userEvent.type(screen.getByPlaceholderText('New step title — Enter to add'), 'Draft methodology');
    const due = screen.queryByLabelText(/^Due date/);
    if (due) {
      await userEvent.type(due, '2027-03-01');
    }
    await userEvent.keyboard('{Enter}');
    await waitFor(() => expect(stepApi.createStep).toHaveBeenCalledWith('token', expect.objectContaining({
      goalId: 1, title: 'Draft methodology', owner: 'Sok Dara', sequenceNumber: 4, complex: false,
    })));
  });

  it('edits a step, and archives one after showing its tasks', async () => {
    renderPage(<StepsPage />);
    await screen.findByText('Write rationale', { selector: 'td *, td' });
    await openActions('Write rationale');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Edit' }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit Step' });
    expect(within(dialog).getByRole('textbox', { name: /^Title/ })).toHaveValue('Write rationale');
    expect(within(dialog).getByRole('checkbox', { name: /Complex step/ })).toBeChecked();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }));
    expect(stepApi.updateStep).toHaveBeenCalledWith('token', 12, expect.objectContaining({ title: 'Write rationale', complex: true }));

    await openActions('Try three AI tools');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Archive' }));
    expect(await screen.findByText(/also archives 4 task\(s\)/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Archive' }));
    expect(stepApi.archiveStep).toHaveBeenCalledWith('token', 13);
  });

  it('opens a step’s tasks, or a new task under it', async () => {
    renderPage(<StepsPage />);
    await screen.findByText('Write rationale', { selector: 'td *, td' });
    await openActions('Write rationale');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Add task' }));
    expect(currentLocation()).toBe('/tasks?create=task&parent=12');
    await openActions('Write rationale');
    await userEvent.click(screen.getByRole('menuitem', { name: 'View tasks' }));
    expect(currentLocation()).toBe('/tasks?stepId=12');
  });

  it('describes the ideal partner for a complex step, with a work-style suggestion (FR-50)', async () => {
    vi.mocked(workStyleProfileApi.getWorkStyleProfile).mockResolvedValue({ dominant: 'DRIVER' } as WorkStyleProfile);
    const saved: IdealPartnerProfile = { id: 70, stepId: 11, requiredExperience: 'Systematic reviews', archived: false };
    vi.mocked(idealPartnerProfileApi.createIdealPartnerProfile).mockResolvedValue(saved);
    vi.mocked(idealPartnerProfileApi.listIdealPartnerProfiles).mockResolvedValueOnce([]).mockResolvedValue([saved]);
    renderPage(<StepsPage />);
    await screen.findByText('Search literature', { selector: 'td *, td' });
    await openActions('Search literature');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Define ideal partner profile' }));
    const dialog = await screen.findByRole('dialog', { name: 'Ideal partner for: Search literature' });
    expect(within(dialog).getByText('Complementary work style')).toBeInTheDocument();
    await userEvent.type(within(dialog).getByRole('textbox', { name: /^Required experience/ }), 'Systematic reviews');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save profile' }));
    expect(idealPartnerProfileApi.createIdealPartnerProfile).toHaveBeenCalledWith('token', expect.objectContaining({ stepId: 11, requiredExperience: 'Systematic reviews' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    // A simple step has no profile action.
    await openActions('Try three AI tools');
    expect(screen.queryByRole('menuitem', { name: /ideal partner profile/ })).not.toBeInTheDocument();
  });

  it('updates and removes an existing ideal partner profile', async () => {
    const existing: IdealPartnerProfile = { id: 70, stepId: 11, requiredExperience: 'Systematic reviews', archived: false };
    vi.mocked(idealPartnerProfileApi.listIdealPartnerProfiles).mockResolvedValue([existing]);
    vi.mocked(idealPartnerProfileApi.updateIdealPartnerProfile).mockResolvedValue(existing);
    vi.mocked(idealPartnerProfileApi.archiveIdealPartnerProfile).mockResolvedValue(undefined);
    renderPage(<StepsPage />);
    await screen.findByText('Search literature', { selector: 'td *, td' });
    await openActions('Search literature');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Edit ideal partner profile' }));
    let dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByRole('textbox', { name: /^Required experience/ })).toHaveValue('Systematic reviews');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save profile' }));
    expect(idealPartnerProfileApi.updateIdealPartnerProfile).toHaveBeenCalledWith('token', 70, expect.objectContaining({ stepId: 11 }));

    await openActions('Search literature');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Edit ideal partner profile' }));
    dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Remove profile' }));
    expect(idealPartnerProfileApi.archiveIdealPartnerProfile).toHaveBeenCalledWith('token', 70);
  });

  it('filters to complex, overdue and one goal’s steps', async () => {
    renderPage(<StepsPage />);
    await screen.findByText('Search literature', { selector: 'td *, td' });
    await userEvent.click(screen.getByRole('checkbox', { name: 'Complex only' }));
    expect(screen.queryByText('Select topic', { selector: 'td *, td' })).not.toBeInTheDocument();
    expect(screen.getByText('Write rationale', { selector: 'td *, td' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('checkbox', { name: 'Complex only' }));
    await userEvent.click(screen.getByRole('checkbox', { name: 'Overdue only' }));
    expect(screen.getByText('Search literature', { selector: 'td *, td' })).toBeInTheDocument();
    expect(screen.queryByText('Write rationale', { selector: 'td *, td' })).not.toBeInTheDocument();
  });

  it('opens one goal’s steps from the address', async () => {
    renderPage(<StepsPage />, { route: '/steps?goalId=2' });
    expect(await screen.findByText('Try three AI tools', { selector: 'td *, td' })).toBeInTheDocument();
    expect(screen.queryByText('Search literature', { selector: 'td *, td' })).not.toBeInTheDocument();
  });

  it('reorders steps within a goal, and moves a step on the board', async () => {
    renderPage(<StepsPage />);
    await screen.findByText('Write rationale', { selector: 'td *, td' });
    fireEvent.dragStart(row('Write rationale'));
    fireEvent.dragOver(row('Select topic'));
    fireEvent.drop(row('Select topic'));
    await waitFor(() => expect(stepApi.reorderSteps).toHaveBeenCalledWith('token', 1, [12, 10, 11]));

    await userEvent.click(screen.getByRole('button', { name: 'Board' }));
    const card = screen.getByText('Try three AI tools').closest('article') as HTMLElement;
    await userEvent.click(within(card).getByRole('combobox', { name: 'Move to status' }));
    await userEvent.click(await screen.findByRole('option', { name: 'In Progress' }));
    expect(stepApi.updateStepStatus).toHaveBeenCalledWith('token', 13, 'IN_PROGRESS');
  });

  it('asks for a goal first when there are none', async () => {
    vi.mocked(stepApi.listSteps).mockResolvedValue([]);
    vi.mocked(goalApi.listGoals).mockResolvedValue([]);
    renderPage(<StepsPage />);
    expect(await screen.findByText('No steps yet')).toBeInTheDocument();
  });

  it('filters by status, priority, vision area from the address, and search', async () => {
    const { unmount } = renderPage(<StepsPage />, { route: '/steps?visionAreaId=9' });
    expect(await screen.findByText('No steps match these filters.')).toBeInTheDocument();
    unmount();

    renderPage(<StepsPage />);
    await screen.findByText('Search literature', { selector: 'td *, td' });
    await userEvent.click(within(screen.getByText('Status', { selector: 'label' })).getByRole('combobox'));
    await userEvent.click(await screen.findByRole('option', { name: 'Completed' }));
    expect(screen.getByText('Select topic', { selector: 'td *, td' })).toBeInTheDocument();
    expect(screen.queryByText('Write rationale', { selector: 'td *, td' })).not.toBeInTheDocument();
    await userEvent.type(screen.getByRole('searchbox', { name: 'Search steps' }), 'zzz');
    expect(await screen.findByText('No steps match these filters.')).toBeInTheDocument();
  });

  it('starts a new step under the goal being filtered', async () => {
    renderPage(<StepsPage />, { route: '/steps?goalId=2' });
    await screen.findByText('Try three AI tools', { selector: 'td *, td' });
    await userEvent.click(screen.getByRole('button', { name: 'Create step' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByRole('combobox', { name: 'Goal' })).toHaveTextContent('Learn AI research tools');
  });

  it('cancels a step edit without saving', async () => {
    renderPage(<StepsPage />);
    await screen.findByText('Write rationale', { selector: 'td *, td' });
    await openActions('Write rationale');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Edit' }));
    await userEvent.click(within(await screen.findByRole('dialog', { name: 'Edit Step' })).getByRole('button', { name: 'Cancel' }));
    expect(stepApi.updateStep).not.toHaveBeenCalled();
  });

  it('shows why a quick add, a board move, a reorder or a profile save failed', async () => {
    vi.mocked(stepApi.createStep).mockRejectedValue(new Error('Title is too long.'));
    vi.mocked(stepApi.updateStepStatus).mockRejectedValue(new Error('Tasks are still open.'));
    vi.mocked(stepApi.reorderSteps).mockRejectedValue(new Error('Order changed elsewhere.'));
    vi.mocked(idealPartnerProfileApi.createIdealPartnerProfile).mockRejectedValue(new Error('Profile is too long.'));
    renderPage(<StepsPage />, { auth: { user: { id: 1, fullName: 'Sok Dara', email: 'dara@example.com', role: 'USER' } } });
    await screen.findByText('Write rationale', { selector: 'td *, td' });

    await userEvent.type(screen.getByPlaceholderText('New step title — Enter to add'), 'x');
    const due = screen.queryByLabelText(/^Due date/);
    if (due) {
      await userEvent.type(due, '2027-03-01');
    }
    await userEvent.keyboard('{Enter}');
    expect(await screen.findByText('Title is too long.')).toBeInTheDocument();

    fireEvent.dragStart(row('Write rationale'));
    fireEvent.dragOver(row('Select topic'));
    fireEvent.drop(row('Select topic'));
    expect(await screen.findByText('Order changed elsewhere.')).toBeInTheDocument();

    await openActions('Search literature');
    await userEvent.click(screen.getByRole('menuitem', { name: 'Define ideal partner profile' }));
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Save profile' }));
    expect(await screen.findByText('Profile is too long.')).toBeInTheDocument();

    await userEvent.keyboard('{Escape}');
    await userEvent.click(screen.getByRole('button', { name: 'Board' }));
    const card = screen.getByText('Try three AI tools').closest('article') as HTMLElement;
    await userEvent.click(within(card).getByRole('combobox', { name: 'Move to status' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Waiting' }));
    expect(await screen.findByText('Tasks are still open.')).toBeInTheDocument();
  });
});
