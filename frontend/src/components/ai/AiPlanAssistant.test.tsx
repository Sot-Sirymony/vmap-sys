import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getAiStatus } from '../../api/aiApi';
import { addPlan, checkPlan, ratePlanAnswer, suggestGoals, suggestSteps, undoPlan } from '../../api/aiPlanApi';
import type { AiStatus, Dream, Goal } from '../../types/vision';
import { AiPlanAssistant } from './AiPlanAssistant';

vi.mock('../../api/aiApi', () => ({ getAiStatus: vi.fn() }));
vi.mock('../../api/aiPlanApi', () => ({
  suggestGoals: vi.fn(), suggestSteps: vi.fn(), checkPlan: vi.fn(), addPlan: vi.fn(), undoPlan: vi.fn(), ratePlanAnswer: vi.fn(),
}));

const ready: AiStatus = { available: true, reason: 'OK', consentGiven: true, dailyLimit: 10, messagesLeftToday: 10 };
const dream = { id: 5, title: 'Own a home', scheduleMode: 'BOTTOM_UP' } as Dream;
const goal = { id: 9, dreamId: 5, title: 'Save a deposit', archived: false } as Goal;

function renderAssistant(onDataChange = vi.fn().mockResolvedValue(undefined)) {
  render(<AiPlanAssistant dream={dream} goals={[goal]} steps={[]} token="token" onDataChange={onDataChange} />);
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
});
