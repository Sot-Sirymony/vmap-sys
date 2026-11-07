import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as aiApi from '../api/aiApi';
import * as aiPlanApi from '../api/aiPlanApi';
import * as dreamApi from '../api/dreamApi';
import * as goalApi from '../api/goalApi';
import * as visionAreaApi from '../api/visionAreaApi';
import { answerEmptyLists } from '../test/apiMocks';
import { aDream, aGoal, aVisionArea } from '../test/builders';
import { currentLocation, renderPage } from '../test/render';
import { DECISION_CHECKLIST_QUESTIONS } from '../utils/enumLabels';
import { DreamsPage } from './DreamsPage';

vi.mock('../api/aiApi');
vi.mock('../api/aiPlanApi');
vi.mock('../api/dreamApi');
vi.mock('../api/goalApi');
vi.mock('../api/visionAreaApi');

const areas = [aVisionArea({ id: 1, name: 'Career' }), aVisionArea({ id: 2, name: 'Health' })];
const researcher = aDream({ id: 1, code: 'D-001', visionAreaId: 1, title: 'Become a researcher', dreamType: 'LONG_TERM', targetDate: '2020-06-30', sortOrder: 0 });
const book = aDream({ id: 2, code: 'D-002', visionAreaId: 1, title: 'Write a book', dreamType: 'LIFETIME', status: 'IDEA', sortOrder: 1 });
const moonshot = aDream({
  id: 3, code: 'D-003', visionAreaId: 2, title: 'Run an ultramarathon', priority: 'CRITICAL', status: 'IDEA', moonshot: true,
  moonshotVision: 'Finish in the top 10', decisionGateClearedAt: null, targetDate: '2099-01-01', sortOrder: 2,
});

const row = (title: string) => screen.getByText(title).closest('tr') as HTMLElement;

describe('DreamsPage (CLAUDE.md page 3)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    answerEmptyLists([aiPlanApi, dreamApi, goalApi, visionAreaApi]);
    vi.mocked(aiApi.getAiStatus).mockResolvedValue({ available: false, reason: 'OFF', consentGiven: false, dailyLimit: 0, messagesLeftToday: 0 });
    vi.mocked(dreamApi.listDreams).mockResolvedValue([researcher, book, moonshot]);
    vi.mocked(visionAreaApi.listVisionAreas).mockResolvedValue(areas);
    vi.mocked(goalApi.listGoals).mockResolvedValue([aGoal({ id: 1, dreamId: 1 }), aGoal({ id: 2, dreamId: 1 })]);
    vi.mocked(dreamApi.createDream).mockResolvedValue(book);
    vi.mocked(dreamApi.updateDream).mockResolvedValue(book);
    vi.mocked(dreamApi.archiveDream).mockResolvedValue(undefined);
    vi.mocked(dreamApi.reorderDreams).mockResolvedValue(undefined as never);
    vi.mocked(dreamApi.getDreamArchiveImpact).mockResolvedValue({ goals: 2, steps: 5, tasks: 9 } as never);
    vi.mocked(dreamApi.dreamHasLinkedPartner).mockResolvedValue(true);
  });

  it('lists dreams with their goal count and moonshots, and marks overdue dreams on the board', async () => {
    renderPage(<DreamsPage />);
    expect(await screen.findByText('Become a researcher')).toBeInTheDocument();
    await waitFor(() => expect(within(row('Become a researcher')).getByText('2')).toBeInTheDocument());
    expect(within(row('Run an ultramarathon')).getByRole('img', { name: 'Moonshot dream' })).toBeInTheDocument();
    // Overdue dreams are highlighted on the board. (The list does not highlight them, unlike Goals: noted in BRD 12.)
    await userEvent.click(screen.getByRole('button', { name: 'Board' }));
    expect(screen.getByText('Become a researcher').closest('article')).toHaveClass('list-card--overdue');
    expect(screen.getByText('Write a book').closest('article')).not.toHaveClass('list-card--overdue');
  });

  it('coaches a vague dream: each clarifying question is ticked as it is answered (CLAUDE.md "If dream is vague")', async () => {
    renderPage(<DreamsPage />);
    await screen.findByText('Become a researcher');
    await userEvent.click(screen.getByRole('button', { name: 'Create dream' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('Make this dream clearer')).toBeInTheDocument();
    const question = (text: string) => within(dialog).getByText(text, { selector: 'li' });
    expect(question('What exactly do you want to achieve?')).not.toHaveClass('coaching-item-done');

    await userEvent.type(within(dialog).getByRole('textbox', { name: /^Title/ }), 'Speak at a conference');
    expect(question('What exactly do you want to achieve?')).toHaveClass('coaching-item-done');
    await userEvent.type(within(dialog).getByRole('textbox', { name: /^Why Important/ }), 'Share our research widely');
    await userEvent.type(within(dialog).getByRole('textbox', { name: /^Success Definition/ }), 'One accepted talk in 2027');
    await userEvent.type(within(dialog).getByLabelText(/^Target Date/), '2027-09-01');
    await waitFor(() => expect(within(dialog).queryByText('Make this dream clearer')).not.toBeInTheDocument());

    await userEvent.click(within(dialog).getAllByRole('button', { name: 'Create dream' }).at(-1)!);
    expect(dreamApi.createDream).toHaveBeenCalledWith('token', expect.objectContaining({
      visionAreaId: 1, title: 'Speak at a conference', whyImportant: 'Share our research widely', successDefinition: 'One accepted talk in 2027', targetDate: '2027-09-01',
    }));
  });

  it('opens the coaching wizard when there are no dreams yet, under the area from the link', async () => {
    vi.mocked(dreamApi.listDreams).mockResolvedValue([]);
    renderPage(<DreamsPage />, { route: '/dreams?create=dream&parent=2' });
    expect(await screen.findByLabelText(/What exactly do you want to achieve/)).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Which area of life or work does this belong to?' })).toHaveTextContent('Health');
    await waitFor(() => expect(currentLocation()).toBe('/dreams'));
  });

  it('cannot create a dream until a vision area exists', async () => {
    vi.mocked(visionAreaApi.listVisionAreas).mockResolvedValue([]);
    vi.mocked(dreamApi.listDreams).mockResolvedValue([]);
    renderPage(<DreamsPage />);
    await waitFor(() => expect(visionAreaApi.listVisionAreas).toHaveBeenCalled());
    expect(screen.getByRole('button', { name: 'Create dream' })).toBeDisabled();
  });

  it('edits a dream, and cancels an edit without saving', async () => {
    renderPage(<DreamsPage />);
    await screen.findByText('Write a book');
    await userEvent.click(within(row('Write a book')).getByRole('button', { name: 'Dream actions' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Edit' }));
    let dialog = await screen.findByRole('dialog', { name: 'Edit Dream' });
    const title = within(dialog).getByRole('textbox', { name: /^Title/ });
    expect(title).toHaveValue('Write a book');
    await userEvent.clear(title);
    await userEvent.type(title, 'Write a novel');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }));
    expect(dreamApi.updateDream).toHaveBeenCalledWith('token', 2, expect.objectContaining({ title: 'Write a novel', dreamType: 'LIFETIME' }));

    await userEvent.click(within(row('Become a researcher')).getByRole('button', { name: 'Dream actions' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Edit' }));
    dialog = await screen.findByRole('dialog', { name: 'Edit Dream' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    expect(dreamApi.updateDream).toHaveBeenCalledTimes(1);
  });

  it('archives a dream after showing the goals, steps and tasks that go with it', async () => {
    renderPage(<DreamsPage />);
    await screen.findByText('Write a book');
    await userEvent.click(within(row('Write a book')).getByRole('button', { name: 'Dream actions' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Archive' }));
    expect(await screen.findByText(/also archives 2 goal\(s\), 5 step\(s\), and 9 task\(s\)/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Archive' }));
    expect(dreamApi.archiveDream).toHaveBeenCalledWith('token', 2);
  });

  it('opens a dream’s map, its goals, or a new goal under it', async () => {
    renderPage(<DreamsPage />);
    await screen.findByText('Write a book');
    const menu = () => userEvent.click(within(row('Write a book')).getByRole('button', { name: 'Dream actions' }));
    await menu();
    await userEvent.click(screen.getByRole('menuitem', { name: 'View map' }));
    expect(currentLocation()).toBe('/dreams/2');
    await menu();
    await userEvent.click(screen.getByRole('menuitem', { name: 'View goals' }));
    expect(currentLocation()).toBe('/goals?dreamId=2');
    await menu();
    await userEvent.click(screen.getByRole('menuitem', { name: 'Add goal' }));
    expect(currentLocation()).toBe('/goals?create=goal&parent=2');
  });

  it('holds a high-priority moonshot dream at the decision gate before it becomes Active (FR-55.1)', async () => {
    renderPage(<DreamsPage />);
    await screen.findByText('Run an ultramarathon');
    await userEvent.click(screen.getByRole('button', { name: 'Board' }));
    const card = screen.getByText('Run an ultramarathon').closest('article') as HTMLElement;
    await userEvent.click(within(card).getByRole('combobox', { name: 'Move to status' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Active' }));
    expect(dreamApi.updateDream).not.toHaveBeenCalled();

    const dialog = await screen.findByRole('dialog', { name: 'Edit Dream' });
    expect(within(dialog).getByText('Before you move this moonshot dream to Active…')).toBeInTheDocument();
    expect(within(dialog).getByText('0 of 8 answered.')).toBeInTheDocument();
    for (const question of DECISION_CHECKLIST_QUESTIONS) {
      await userEvent.click(within(within(dialog).getByRole('group', { name: question.label })).getByRole('button', { name: 'Yes' }));
    }
    expect(within(dialog).getByText('All eight answered — this checklist clears the gate on save.')).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }));
    expect(dreamApi.updateDream).toHaveBeenCalledWith('token', 3, expect.objectContaining({
      status: 'ACTIVE', decisionSkippedResearch: true, decisionDismissedDisagreeingAdvice: true,
    }));
  });

  it('moves an ordinary dream on the board, and suggests recording who helped when it is completed', async () => {
    vi.mocked(dreamApi.dreamHasLinkedPartner).mockResolvedValue(false);
    renderPage(<DreamsPage />);
    await screen.findByText('Write a book');
    await userEvent.click(screen.getByRole('button', { name: 'Board' }));
    const card = screen.getByText('Write a book').closest('article') as HTMLElement;
    await userEvent.click(within(card).getByRole('combobox', { name: 'Move to status' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Completed' }));
    expect(dreamApi.updateDream).toHaveBeenCalledWith('token', 2, expect.objectContaining({ status: 'COMPLETED' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Add partner' }));
    expect(currentLocation()).toBe('/partners?create=partner&relatedDreamId=2');
  });

  it('reorders dreams only within their vision area', async () => {
    renderPage(<DreamsPage />);
    await screen.findByText('Write a book');
    fireEvent.dragStart(row('Run an ultramarathon'));
    fireEvent.dragOver(row('Become a researcher'));
    fireEvent.drop(row('Become a researcher'));
    expect(dreamApi.reorderDreams).not.toHaveBeenCalled();
    fireEvent.dragStart(row('Write a book'));
    fireEvent.dragOver(row('Become a researcher'));
    fireEvent.drop(row('Become a researcher'));
    await waitFor(() => expect(dreamApi.reorderDreams).toHaveBeenCalledWith('token', 1, [2, 1]));
  });

  it('filters by moonshot, overdue, vision area from the address, and search', async () => {
    const { unmount } = renderPage(<DreamsPage />, { route: '/dreams?visionAreaId=2' });
    expect(await screen.findByText('Run an ultramarathon')).toBeInTheDocument();
    expect(screen.queryByText('Write a book')).not.toBeInTheDocument();
    unmount();

    renderPage(<DreamsPage />);
    await screen.findByText('Write a book');
    await userEvent.click(screen.getByRole('checkbox', { name: 'Moonshots only' }));
    expect(screen.queryByText('Write a book')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('checkbox', { name: 'Moonshots only' }));
    await userEvent.click(screen.getByRole('checkbox', { name: 'Overdue only' }));
    expect(screen.getByText('Become a researcher')).toBeInTheDocument();
    expect(screen.queryByText('Write a book')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('checkbox', { name: 'Overdue only' }));
    await userEvent.type(screen.getByRole('searchbox', { name: 'Search dreams' }), 'novel');
    expect(await screen.findByText(/No dreams match/)).toBeInTheDocument();
  });

  it('shows why a board move failed', async () => {
    vi.mocked(dreamApi.updateDream).mockRejectedValue(new Error('Target date is before its goals.'));
    renderPage(<DreamsPage />);
    await screen.findByText('Write a book');
    await userEvent.click(screen.getByRole('button', { name: 'Board' }));
    const card = screen.getByText('Write a book').closest('article') as HTMLElement;
    await userEvent.click(within(card).getByRole('combobox', { name: 'Move to status' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Paused' }));
    expect(await screen.findByText('Target date is before its goals.')).toBeInTheDocument();
  });
});
