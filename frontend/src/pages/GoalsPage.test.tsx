import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as dreamApi from '../api/dreamApi';
import * as goalApi from '../api/goalApi';
import * as stepApi from '../api/stepApi';
import * as visionAreaApi from '../api/visionAreaApi';
import { answerEmptyLists } from '../test/apiMocks';
import { aDream, aGoal, aStep, aVisionArea } from '../test/builders';
import { currentLocation, renderPage } from '../test/render';
import { GoalsPage } from './GoalsPage';

vi.mock('../api/dreamApi');
vi.mock('../api/goalApi');
vi.mock('../api/stepApi');
vi.mock('../api/visionAreaApi');

const dreams = [aDream({ id: 1, title: 'Become a researcher', visionAreaId: 1 }), aDream({ id: 2, title: 'Run a marathon', visionAreaId: 2 })];
const conceptNote = aGoal({ id: 10, code: 'G-010', dreamId: 1, title: 'Prepare one concept note', priority: 'HIGH', targetDate: '2020-01-31', status: 'IN_PROGRESS', progressPercent: 40, sortOrder: 0 });
const tools = aGoal({ id: 11, code: 'G-011', dreamId: 1, title: 'Learn AI research tools', priority: 'MEDIUM', targetDate: '2099-06-30', status: 'NOT_STARTED', sortOrder: 1 });
const training = aGoal({ id: 12, code: 'G-012', dreamId: 2, title: 'Train four days a week', priority: 'LOW', status: 'COMPLETED', progressPercent: 100, moonshot: true, moonshotVision: 'Sub-4 hours', sortOrder: 2 });

const row = (title: string) => screen.getByText(title).closest('tr') as HTMLElement;

describe('GoalsPage (CLAUDE.md page 5)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    answerEmptyLists([dreamApi, goalApi, stepApi, visionAreaApi]);
    vi.mocked(goalApi.listGoals).mockResolvedValue([conceptNote, tools, training]);
    vi.mocked(dreamApi.listDreams).mockResolvedValue(dreams);
    vi.mocked(visionAreaApi.listVisionAreas).mockResolvedValue([aVisionArea({ id: 1, name: 'Career' }), aVisionArea({ id: 2, name: 'Health' })]);
    vi.mocked(stepApi.listSteps).mockResolvedValue([aStep({ id: 1, goalId: 10 }), aStep({ id: 2, goalId: 10 }), aStep({ id: 3, goalId: 10 })]);
    vi.mocked(goalApi.createGoal).mockResolvedValue(tools);
    vi.mocked(goalApi.updateGoal).mockResolvedValue(tools);
    vi.mocked(goalApi.updateGoalStatus).mockResolvedValue(tools);
    vi.mocked(goalApi.archiveGoal).mockResolvedValue(undefined);
    vi.mocked(goalApi.reorderGoals).mockResolvedValue(undefined as never);
    vi.mocked(goalApi.getGoalArchiveImpact).mockResolvedValue({ steps: 3, tasks: 7 } as never);
    vi.mocked(goalApi.goalHasLinkedPartner).mockResolvedValue(true);
  });

  it('lists goals with their step count, progress, and overdue goals marked (BR-9)', async () => {
    renderPage(<GoalsPage />);
    expect(await screen.findByText('Prepare one concept note')).toBeInTheDocument();
    await waitFor(() => expect(within(row('Prepare one concept note')).getByText('3')).toBeInTheDocument());
    expect(row('Prepare one concept note')).toHaveClass('row-overdue');
    expect(row('Learn AI research tools')).not.toHaveClass('row-overdue');
    expect(row('Train four days a week')).not.toHaveClass('row-overdue');
    expect(within(row('Train four days a week')).getByRole('img', { name: 'Moonshot goal' })).toBeInTheDocument();
  });

  it('creates a goal under the chosen dream', async () => {
    renderPage(<GoalsPage />);
    await screen.findByText('Prepare one concept note');
    await userEvent.click(screen.getByRole('button', { name: 'Create goal' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('combobox', { name: 'Dream' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Run a marathon' }));
    await userEvent.type(within(dialog).getByRole('textbox', { name: /^Title/ }), 'Finish a half marathon');
    await userEvent.type(within(dialog).getByLabelText(/^Target Date/), '2027-04-01');
    await userEvent.click(within(dialog).getAllByRole('button', { name: 'Create goal' }).at(-1)!);
    expect(goalApi.createGoal).toHaveBeenCalledWith('token', expect.objectContaining({
      dreamId: 2, title: 'Finish a half marathon', targetDate: '2027-04-01', status: 'NOT_STARTED', moonshot: false, scheduleMode: 'BOTTOM_UP',
    }));
  });

  it('opens a new goal under a dream from a link, then cleans the address', async () => {
    renderPage(<GoalsPage />, { route: '/goals?create=goal&parent=2' });
    const dialog = await screen.findByRole('dialog');
    await waitFor(() => expect(within(dialog).getByRole('combobox', { name: 'Dream' })).toHaveTextContent('Run a marathon'));
    expect(currentLocation()).toBe('/goals');
  });

  it('edits a goal, and marks it a moonshot with its vision', async () => {
    renderPage(<GoalsPage />);
    await screen.findByText('Learn AI research tools');
    await userEvent.click(within(row('Learn AI research tools')).getByRole('button', { name: 'Goal actions' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Edit' }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit Goal' });
    expect(within(dialog).getByRole('textbox', { name: /^Title/ })).toHaveValue('Learn AI research tools');
    await userEvent.click(within(dialog).getByRole('checkbox', { name: /Moonshot goal/ }));
    await userEvent.type(within(dialog).getByRole('textbox', { name: /Moonshot vision/ }), 'Publish in a top journal');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }));
    expect(goalApi.updateGoal).toHaveBeenCalledWith('token', 11, expect.objectContaining({ moonshot: true, moonshotVision: 'Publish in a top journal' }));
  });

  it('archives a goal after showing the steps and tasks that go with it', async () => {
    renderPage(<GoalsPage />);
    await screen.findByText('Learn AI research tools');
    await userEvent.click(within(row('Learn AI research tools')).getByRole('button', { name: 'Goal actions' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Archive' }));
    expect(await screen.findByText(/also archives 3 step\(s\) and 7 task\(s\)/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Archive' }));
    expect(goalApi.archiveGoal).toHaveBeenCalledWith('token', 11);
  });

  it('goes to a goal’s steps, and adds a step under it', async () => {
    renderPage(<GoalsPage />);
    await screen.findByText('Learn AI research tools');
    await userEvent.click(within(row('Learn AI research tools')).getByRole('button', { name: 'Goal actions' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Add step' }));
    expect(currentLocation()).toBe('/steps?create=step&parent=11');
    await userEvent.click(within(row('Learn AI research tools')).getByRole('button', { name: 'Goal actions' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'View steps' }));
    expect(currentLocation()).toBe('/steps?goalId=11');
  });

  it('quick-adds a goal with only a title under the chosen dream (FR-22.1)', async () => {
    renderPage(<GoalsPage />);
    await screen.findByText('Prepare one concept note');
    await userEvent.type(screen.getByPlaceholderText('New goal title — Enter to add'), 'Find a mentor{Enter}');
    await waitFor(() => expect(goalApi.createGoal).toHaveBeenCalledWith('token', expect.objectContaining({ dreamId: 1, title: 'Find a mentor', priority: 'MEDIUM', status: 'NOT_STARTED' })));
  });

  it('filters to overdue goals, by dream, and by search', async () => {
    renderPage(<GoalsPage />);
    await screen.findByText('Prepare one concept note');
    await userEvent.click(screen.getByRole('checkbox', { name: 'Overdue only' }));
    expect(screen.queryByText('Learn AI research tools')).not.toBeInTheDocument();
    expect(screen.getByText('Prepare one concept note')).toBeInTheDocument();
    await waitFor(() => expect(currentLocation()).toContain('overdue'));
    await userEvent.click(screen.getByRole('checkbox', { name: 'Overdue only' }));

    await userEvent.type(screen.getByRole('searchbox', { name: 'Search goals' }), 'train');
    expect(screen.getByText('Train four days a week')).toBeInTheDocument();
    expect(screen.queryByText('Prepare one concept note')).not.toBeInTheDocument();
  });

  it('shows only moonshot goals on request', async () => {
    renderPage(<GoalsPage />);
    await screen.findByText('Prepare one concept note');
    await userEvent.click(screen.getByRole('checkbox', { name: 'Moonshots only' }));
    expect(screen.getByText('Train four days a week')).toBeInTheDocument();
    expect(screen.queryByText('Prepare one concept note')).not.toBeInTheDocument();
  });

  it('sets the status or priority of several selected goals at once (bulk update)', async () => {
    renderPage(<GoalsPage />);
    await screen.findByText('Prepare one concept note');
    await userEvent.click(screen.getByRole('checkbox', { name: 'Select Prepare one concept note' }));
    await userEvent.click(screen.getByRole('checkbox', { name: 'Select Learn AI research tools' }));
    await userEvent.click(screen.getByRole('combobox', { name: 'Set status' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Waiting' }));
    const [applyStatus, applyPriority] = screen.getAllByRole('button', { name: 'Apply' });
    await userEvent.click(applyStatus);
    await waitFor(() => expect(goalApi.updateGoalStatus).toHaveBeenCalledTimes(2));
    expect(goalApi.updateGoalStatus).toHaveBeenCalledWith('token', 10, 'WAITING');
    expect(await screen.findByText('Updated 2 goals.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('checkbox', { name: 'Select Prepare one concept note' }));
    await userEvent.click(screen.getByRole('checkbox', { name: 'Select Learn AI research tools' }));
    await userEvent.click(screen.getByRole('combobox', { name: 'Set priority' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Critical' }));
    await userEvent.click(screen.getAllByRole('button', { name: 'Apply' })[1] ?? applyPriority);
    await waitFor(() => expect(goalApi.updateGoal).toHaveBeenCalledWith('token', expect.any(Number), expect.objectContaining({ priority: 'CRITICAL' })));
  });

  it('shows why a bulk update failed', async () => {
    vi.mocked(goalApi.updateGoalStatus).mockRejectedValue(new Error('Steps are still open.'));
    renderPage(<GoalsPage />);
    await screen.findByText('Prepare one concept note');
    await userEvent.click(screen.getByRole('checkbox', { name: 'Select Prepare one concept note' }));
    await userEvent.click(screen.getAllByRole('button', { name: 'Apply' })[0]);
    expect(await screen.findByText('Steps are still open.')).toBeInTheDocument();
  });

  it('reorders goals only within their dream, and moves a goal on the board', async () => {
    renderPage(<GoalsPage />);
    await screen.findByText('Prepare one concept note');
    // A goal from another dream cannot be dropped among these: nothing changes.
    fireEvent.dragStart(row('Train four days a week'));
    fireEvent.dragOver(row('Prepare one concept note'));
    fireEvent.drop(row('Prepare one concept note'));
    expect(goalApi.reorderGoals).not.toHaveBeenCalled();
    // Within the same dream, the order is saved.
    fireEvent.dragStart(row('Learn AI research tools'));
    fireEvent.dragOver(row('Prepare one concept note'));
    fireEvent.drop(row('Prepare one concept note'));
    await waitFor(() => expect(goalApi.reorderGoals).toHaveBeenCalledWith('token', 1, [11, 10]));

    await userEvent.click(screen.getByRole('button', { name: 'Board' }));
    const [firstMove] = screen.getAllByRole('combobox', { name: 'Move to status' });
    await userEvent.click(firstMove);
    await userEvent.click(await screen.findByRole('option', { name: 'Blocked' }));
    expect(goalApi.updateGoalStatus).toHaveBeenCalledWith('token', expect.any(Number), 'BLOCKED');
  });

  it('asks for a dream first when there are none', async () => {
    vi.mocked(goalApi.listGoals).mockResolvedValue([]);
    vi.mocked(dreamApi.listDreams).mockResolvedValue([]);
    renderPage(<GoalsPage />);
    expect(await screen.findByText('No goals yet')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Create goal' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Create a dream first' }));
    expect(currentLocation()).toBe('/dreams?create=dream');
  });

  it('filters by vision area, dream, status, priority and target date range', async () => {
    renderPage(<GoalsPage />);
    await screen.findByText('Prepare one concept note');
    const filter = async (label: string, option: string) => {
      await userEvent.click(within(screen.getByText(label, { selector: '.filter-panel label, label' })).getByRole('combobox'));
      await userEvent.click(await screen.findByRole('option', { name: option }));
    };
    await filter('Vision Area', 'Health');
    expect(screen.getByText('Train four days a week')).toBeInTheDocument();
    expect(screen.queryByText('Learn AI research tools')).not.toBeInTheDocument();
  });

  it('narrows to goals due within a date range', async () => {
    renderPage(<GoalsPage />);
    await screen.findByText('Prepare one concept note');
    await userEvent.type(screen.getByLabelText(/^Target from/), '2099-01-01');
    expect(screen.getByText('Learn AI research tools')).toBeInTheDocument();
    expect(screen.queryByText('Prepare one concept note')).not.toBeInTheDocument();
    expect(screen.queryByText('Train four days a week')).not.toBeInTheDocument();
    await userEvent.clear(screen.getByLabelText(/^Target from/));
    await userEvent.type(screen.getByLabelText(/^Target to/), '2021-01-01');
    expect(screen.getByText('Prepare one concept note')).toBeInTheDocument();
    expect(screen.queryByText('Learn AI research tools')).not.toBeInTheDocument();
  });

  it('opens with a dream filter from the address', async () => {
    renderPage(<GoalsPage />, { route: '/goals?dreamId=2' });
    expect(await screen.findByText('Train four days a week')).toBeInTheDocument();
    expect(screen.queryByText('Prepare one concept note')).not.toBeInTheDocument();
  });

  it('marks a goal completed from the edit form, suggests recording who helped, and cancels an edit', async () => {
    vi.mocked(goalApi.goalHasLinkedPartner).mockResolvedValue(false);
    renderPage(<GoalsPage />);
    await screen.findByText('Learn AI research tools');
    await userEvent.click(within(row('Learn AI research tools')).getByRole('button', { name: 'Goal actions' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Edit' }));
    let dialog = await screen.findByRole('dialog', { name: 'Edit Goal' });
    await userEvent.click(within(dialog).getByRole('combobox', { name: 'Status' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Completed' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }));
    expect(goalApi.updateGoal).toHaveBeenCalledWith('token', 11, expect.objectContaining({ status: 'COMPLETED' }));
    // No partner helped with this goal yet, so the page suggests recording one (contribution nudge).
    await userEvent.click(await screen.findByRole('button', { name: 'Add partner' }));
    expect(currentLocation()).toBe('/partners?create=partner&relatedGoalId=11');

    await userEvent.click(within(row('Prepare one concept note')).getByRole('button', { name: 'Goal actions' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Edit' }));
    dialog = await screen.findByRole('dialog', { name: 'Edit Goal' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    expect(goalApi.updateGoal).toHaveBeenCalledTimes(1);
  });

  it('shows why a quick add or a board move failed', async () => {
    vi.mocked(goalApi.createGoal).mockRejectedValue(new Error('Title is too long.'));
    vi.mocked(goalApi.updateGoalStatus).mockRejectedValue(new Error('Steps are still open.'));
    renderPage(<GoalsPage />);
    await screen.findByText('Prepare one concept note');
    await userEvent.type(screen.getByPlaceholderText('New goal title — Enter to add'), 'x{Enter}');
    expect(await screen.findByText('Title is too long.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Board' }));
    await userEvent.click(screen.getAllByRole('combobox', { name: 'Move to status' })[0]);
    await userEvent.click(await screen.findByRole('option', { name: 'Waiting' }));
    expect(await screen.findByText('Steps are still open.')).toBeInTheDocument();
  });

  it('shows archived goals on request, restores one, and permanently deletes another', async () => {
    const old = aGoal({ id: 19, dreamId: 1, title: 'Old goal', archived: true });
    vi.mocked(goalApi.listGoals).mockImplementation(async (_token, includeArchived) => (includeArchived ? [conceptNote, old] : [conceptNote]));
    vi.mocked(goalApi.restoreGoal).mockResolvedValue(undefined);
    vi.mocked(goalApi.permanentlyDeleteGoal).mockResolvedValue(undefined);
    renderPage(<GoalsPage />);
    await screen.findByText('Prepare one concept note');
    await userEvent.click(screen.getByRole('checkbox', { name: 'Show archived' }));
    expect(await screen.findByText('Old goal')).toBeInTheDocument();
    expect(row('Old goal')).toHaveClass('row-archived');
    await userEvent.click(within(row('Old goal')).getByRole('button', { name: 'Goal actions' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Restore' }));
    expect(goalApi.restoreGoal).toHaveBeenCalledWith('token', 19);
    await userEvent.click(within(row('Old goal')).getByRole('button', { name: 'Goal actions' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Delete permanently' }));
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: /Delete/ }));
    expect(goalApi.permanentlyDeleteGoal).toHaveBeenCalledWith('token', 19);
  });

  it('shows why a reorder failed', async () => {
    vi.mocked(goalApi.reorderGoals).mockRejectedValue(new Error('Order changed elsewhere.'));
    renderPage(<GoalsPage />);
    await screen.findByText('Learn AI research tools');
    fireEvent.dragStart(row('Learn AI research tools'));
    fireEvent.dragOver(row('Prepare one concept note'));
    fireEvent.drop(row('Prepare one concept note'));
    expect(await screen.findByText('Order changed elsewhere.')).toBeInTheDocument();
  });
});
