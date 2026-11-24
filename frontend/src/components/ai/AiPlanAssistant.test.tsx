import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getAiStatus } from '../../api/aiApi';
import { getStepResourceIdeas } from '../../api/aiDailyApi';
import { addPlan, analyseBlockers, checkPlan, ratePlanAnswer, suggestGoals, suggestSteps, suggestTasks, undoPlan } from '../../api/aiPlanApi';
import { markBlocked } from '../../api/blockApi';
import type { AiStatus, Dream, Goal, TaskItem, VisionStep } from '../../types/vision';
import { AiPlanAssistant } from './AiPlanAssistant';

vi.mock('../../api/aiApi', () => ({ getAiStatus: vi.fn() }));
vi.mock('../../api/aiPlanApi', () => ({
  suggestGoals: vi.fn(), suggestSteps: vi.fn(), suggestTasks: vi.fn(), checkPlan: vi.fn(), addPlan: vi.fn(), undoPlan: vi.fn(),
  ratePlanAnswer: vi.fn(), analyseBlockers: vi.fn(), suggestSmallerTasks: vi.fn(),
}));
vi.mock('../../api/aiDailyApi', () => ({ getResourceIdeas: vi.fn(), getStepResourceIdeas: vi.fn() }));
vi.mock('../../api/blockApi', () => ({ markBlocked: vi.fn() }));

const ready: AiStatus = { available: true, reason: 'OK', consentGiven: true, dailyLimit: 10, messagesLeftToday: 10 };
const dream = { id: 5, title: 'Own a home', scheduleMode: 'BOTTOM_UP' } as Dream;
const goal = { id: 9, dreamId: 5, title: 'Save a deposit', archived: false } as Goal;

const step = { id: 31, goalId: 9, title: 'Visit three banks', sequenceNumber: 1, archived: false } as VisionStep;

function renderAssistant(onDataChange = vi.fn().mockResolvedValue(undefined), steps: VisionStep[] = []) {
  render(<AiPlanAssistant dream={dream} goals={[goal]} steps={steps} token="token" onDataChange={onDataChange} />);
  return onDataChange;
}

describe('AiPlanAssistant (AI-FR-07 to AI-FR-10)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('adds only the goals the participant keeps, with their edits, in one request that can be undone', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(suggestGoals).mockResolvedValue({ requestId: 77, goals: [
      { title: 'Save $5,000', priority: 'HIGH', moonshot: false, targetDate: '2027-06-01', reason: 'A deposit first' },
      { title: 'Check my credit', priority: 'MEDIUM', moonshot: false },
    ] });
    vi.mocked(addPlan).mockResolvedValue({ goalIds: [41], stepIds: [] });
    vi.mocked(undoPlan).mockResolvedValue(undefined);
    const onDataChange = renderAssistant();

    await userEvent.click(await screen.findByRole('button', { name: 'Suggest goals' }));
    expect(await screen.findByDisplayValue('Save $5,000')).toBeInTheDocument();
    expect(addPlan).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('checkbox', { name: 'Add Check my credit' }));
    const title = screen.getByDisplayValue('Save $5,000');
    await userEvent.clear(title);
    await userEvent.type(title, 'Save $6,000');
    await userEvent.click(screen.getByRole('button', { name: 'Add 1 selected goal(s)' }));

    expect(addPlan).toHaveBeenCalledTimes(1);
    expect(addPlan).toHaveBeenCalledWith('token', {
      requestId: 77,
      goals: [{ edited: true, goal: expect.objectContaining({ dreamId: 5, title: 'Save $6,000', targetDate: '2027-06-01' }) }],
    });
    expect(onDataChange).toHaveBeenCalled();
    expect(await screen.findByText('1 goal(s) added to your plan.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Undo' }));
    expect(undoPlan).toHaveBeenCalledWith('token', { goalIds: [41], stepIds: [], requestId: 77 });
    expect(await screen.findByText('Undone. What was just added is archived.')).toBeInTheDocument();
  });

  it('lets the participant rate a set of suggestions', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(suggestGoals).mockResolvedValue({ requestId: 12, goals: [{ title: 'Save $5,000', priority: 'HIGH', moonshot: false }] });
    vi.mocked(ratePlanAnswer).mockResolvedValue(undefined);
    renderAssistant();

    await userEvent.click(await screen.findByRole('button', { name: 'Suggest goals' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Helpful' }));
    expect(ratePlanAnswer).toHaveBeenCalledWith('token', 12, 1);
    expect(screen.getByRole('button', { name: 'Helpful' })).toHaveAttribute('aria-pressed', 'true');
    await userEvent.click(screen.getByRole('button', { name: 'Helpful' }));
    expect(ratePlanAnswer).toHaveBeenLastCalledWith('token', 12, 0);
  });

  it('needs a date on every step before adding, and keeps resources and people', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(addPlan).mockResolvedValue({ goalIds: [], stepIds: [3] });
    vi.mocked(suggestSteps).mockResolvedValue({ steps: [
      { title: 'Visit three banks', owner: 'Me', targetDate: null, resources: 'Payslips', people: 'A loan officer' },
    ] });
    renderAssistant();

    await userEvent.click(await screen.findByRole('combobox', { name: 'Goal to plan' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Save a deposit' }));
    await userEvent.click(screen.getByRole('button', { name: 'Suggest steps' }));
    expect(await screen.findByDisplayValue('Visit three banks')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add 0 selected step(s)' })).toBeDisabled();

    await userEvent.type(screen.getByLabelText('Target date'), '2027-01-15');
    await userEvent.click(screen.getByRole('button', { name: 'Add 1 selected step(s)' }));
    expect(addPlan).toHaveBeenCalledWith('token', expect.objectContaining({
      steps: [{ edited: true, step: expect.objectContaining({
        goalId: 9, sequenceNumber: 1, targetDate: '2027-01-15', resources: 'Payslips\nPeople: A loan officer',
      }) }],
    }));
  });

  it('runs the plan check without AI and shows a clarifying question instead of guessing', async () => {
    vi.mocked(getAiStatus).mockResolvedValue({ ...ready, available: false, reason: 'OFF' });
    vi.mocked(checkPlan).mockResolvedValue({ aiChecked: false, issues: [
      { kind: 'MISSING_RESOURCE', itemType: 'STEP', itemId: 3, itemTitle: 'Call the bank', problem: 'This step does not say what it needs.', fromAi: false },
    ] });
    renderAssistant();

    expect(await screen.findByRole('button', { name: 'Suggest goals' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Check my plan' }));
    expect(await screen.findByText('No resources')).toBeInTheDocument();
    expect(screen.getByText('Step: Call the bank')).toBeInTheDocument();
    expect(screen.getByText('Showing the built-in checks only. The AI review was not available.')).toBeInTheDocument();

    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(suggestGoals).mockResolvedValue({ goals: [], clarifyingQuestion: 'Which city?' });
    renderAssistant();
    const [, second] = await screen.findAllByRole('button', { name: 'Suggest goals' });
    await vi.waitFor(() => expect(second).toBeEnabled());
    await userEvent.click(second);
    expect(await screen.findByText('Which city?')).toBeInTheDocument();
  });

  it('suggests tasks for a chosen step and adds them only once each has a due date', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(suggestTasks).mockResolvedValue({ requestId: 55, tasks: [
      { title: 'Book a meeting with a loan officer', owner: 'Me', priority: 'HIGH', dueDate: null, reason: 'Gets a loan answer' },
    ] });
    vi.mocked(addPlan).mockResolvedValue({ goalIds: [], stepIds: [], taskIds: [61] });
    renderAssistant(undefined, [step]);

    expect(screen.queryByRole('combobox', { name: 'Step to plan' })).not.toBeInTheDocument();
    await userEvent.click(await screen.findByRole('combobox', { name: 'Goal to plan' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Save a deposit' }));
    expect(screen.getByRole('button', { name: 'Suggest tasks' })).toBeDisabled();
    await userEvent.click(screen.getByRole('combobox', { name: 'Step to plan' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Visit three banks' }));
    await userEvent.click(screen.getByRole('button', { name: 'Suggest tasks' }));

    expect(suggestTasks).toHaveBeenCalledWith('token', 31, 'en');
    expect(await screen.findByDisplayValue('Book a meeting with a loan officer')).toBeInTheDocument();
    expect(screen.getByText('Gets a loan answer')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add 0 selected task(s)' })).toBeDisabled();

    await userEvent.type(screen.getByLabelText('Due date'), '2027-01-10');
    await userEvent.click(screen.getByRole('button', { name: 'Add 1 selected task(s)' }));
    expect(addPlan).toHaveBeenCalledWith('token', {
      requestId: 55,
      tasks: [{ edited: true, task: expect.objectContaining({
        stepId: 31, title: 'Book a meeting with a loan officer', owner: 'Me', dueDate: '2027-01-10', status: 'NOT_STARTED', progressPercent: 0,
      }) }],
    });
    expect(await screen.findByText('1 task(s) added to your plan.')).toBeInTheDocument();
  });

  it('asks for resource ideas for the chosen step', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(getStepResourceIdeas).mockResolvedValue({ resources: [
      { kind: 'PERSON', name: 'A mortgage adviser', why: 'Explains which loan fits', checkBeforeUse: true },
    ] });
    renderAssistant(undefined, [step]);

    await userEvent.click(await screen.findByRole('combobox', { name: 'Goal to plan' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Save a deposit' }));
    await userEvent.click(screen.getByRole('combobox', { name: 'Step to plan' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Visit three banks' }));
    const [, forStep] = screen.getAllByRole('button', { name: 'Resource ideas' });
    await userEvent.click(forStep);

    expect(getStepResourceIdeas).toHaveBeenCalledWith('token', 31, 'en');
    expect(await screen.findByText('A mortgage adviser')).toBeInTheDocument();
    expect(screen.getByText('Explains which loan fits')).toBeInTheDocument();
  });
  it('analyses blockers without AI and ranks what holds the dream back', async () => {
    vi.mocked(getAiStatus).mockResolvedValue({ ...ready, available: false, reason: 'NO_CONSENT' });
    vi.mocked(analyseBlockers).mockResolvedValue({
      blockedCount: 1, waitingCount: 0, gapCount: 0, heldUpSteps: 1, aiChecked: false, requestId: null, candidates: [],
      blockers: [{ itemType: 'TASK', itemId: 70, title: 'Ask for the dataset', kind: 'BLOCKED', goalId: 9, goalTitle: 'Save a deposit',
        stepId: 31, stepTitle: 'Visit three banks', reason: 'No reply', overdue: false, timesBlocked: 1, heldUpSteps: 1,
        goalAtRisk: false, obstacleTypeGuessed: false, hasHelper: false, score: 20 }],
    });
    render(
      <MemoryRouter>
        <AiPlanAssistant dream={dream} goals={[goal]} steps={[step]} token="token" onDataChange={vi.fn()} />
      </MemoryRouter>,
    );

    await userEvent.click(await screen.findByRole('button', { name: 'Analyse blockers' }));
    expect(analyseBlockers).toHaveBeenCalledWith('token', 5, 'en');
    expect(await screen.findByLabelText('Blocker 1: Ask for the dataset')).toBeInTheDocument();
    expect(screen.getByText('Holds up 1 later step(s)')).toBeInTheDocument();
    // No AI request, so nothing to rate.
    expect(screen.queryByRole('button', { name: 'Helpful' })).not.toBeInTheDocument();
  });
  it('marks a task of the chosen step as blocked, without AI', async () => {
    vi.mocked(getAiStatus).mockResolvedValue({ ...ready, available: false, reason: 'NO_CONSENT' });
    vi.mocked(markBlocked).mockResolvedValue({ itemType: 'TASK', itemId: 70, obstacleId: 4, obstacleCreated: true });
    const onDataChange = vi.fn().mockResolvedValue(undefined);
    const tasks = [{ id: 70, stepId: 31, title: 'Ask the bank for a quote', status: 'IN_PROGRESS', archived: false } as TaskItem];
    render(
      <MemoryRouter>
        <AiPlanAssistant dream={dream} goals={[goal]} steps={[step]} tasks={tasks} token="token" onDataChange={onDataChange} />
      </MemoryRouter>,
    );

    await userEvent.click(await screen.findByRole('combobox', { name: 'Goal to plan' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Save a deposit' }));
    await userEvent.click(await screen.findByRole('combobox', { name: 'Step to plan' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Visit three banks' }));
    await userEvent.click(await screen.findByRole('combobox', { name: 'What to mark as blocked' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Ask the bank for a quote' }));
    await userEvent.click(screen.getByRole('button', { name: 'Mark as blocked' }));

    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('combobox', { name: /What is missing/ }));
    await userEvent.click(await screen.findByRole('option', { name: 'Money' }));
    await userEvent.type(within(dialog).getByRole('textbox', { name: /Why is it blocked/ }), 'No deposit saved yet');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Mark as blocked' }));

    expect(markBlocked).toHaveBeenCalledWith('token', { itemType: 'TASK', itemId: 70, obstacleType: 'MONEY',
      reason: 'No deposit saved yet', severity: 'MEDIUM' });
    expect(await screen.findByText('Marked as blocked.')).toBeInTheDocument();
    expect(onDataChange).toHaveBeenCalled();
  });
});
