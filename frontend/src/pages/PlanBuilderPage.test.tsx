import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getAiStatus } from '../api/aiApi';
import { addPlanDraft, getPlanDraft, savePlanDraft, suggestGoals, undoPlan } from '../api/aiPlanApi';
import { listDreams } from '../api/dreamApi';
import { listGoals } from '../api/goalApi';
import { listSteps } from '../api/stepApi';
import { listTasks } from '../api/taskApi';
import type { AiStatus, Dream, Goal } from '../types/vision';
import { PlanBuilderPage } from './PlanBuilderPage';

vi.mock('../context/AuthContext', () => ({ useAuth: () => ({ token: 'token', user: { fullName: 'Ana' } }) }));
vi.mock('../api/aiApi', () => ({ getAiStatus: vi.fn() }));
vi.mock('../api/aiPlanApi', () => ({
  getPlanDraft: vi.fn(), savePlanDraft: vi.fn(), discardPlanDraft: vi.fn(), addPlanDraft: vi.fn(), undoPlan: vi.fn(),
  suggestGoals: vi.fn(), suggestSteps: vi.fn(), suggestDraftGoalSteps: vi.fn(), ratePlanAnswer: vi.fn(),
  getPlanCapacity: vi.fn().mockResolvedValue({ hoursPerWeek: null, planned: [] }), savePlanCapacity: vi.fn(),
  checkPlanDraft: vi.fn(), fixPlanDraft: vi.fn(),
}));
vi.mock('../api/dreamApi', () => ({ listDreams: vi.fn() }));
vi.mock('../api/goalApi', () => ({ listGoals: vi.fn() }));
vi.mock('../api/stepApi', () => ({ listSteps: vi.fn() }));
vi.mock('../api/taskApi', () => ({ listTasks: vi.fn() }));

const dream = { id: 5, title: 'Own a home', whyImportant: 'Family', successDefinition: 'Keys', archived: false } as Dream;
const ready: AiStatus = { available: true, reason: 'OK', consentGiven: true, dailyLimit: 10, messagesLeftToday: 10 };

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/dreams/5/build']}>
      <Routes><Route path="/dreams/:dreamId/build" element={<PlanBuilderPage />} /></Routes>
    </MemoryRouter>,
  );
}

describe('PlanBuilderPage (V11 Phase 2)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listDreams).mockResolvedValue([dream]);
    vi.mocked(listGoals).mockResolvedValue([]);
    vi.mocked(listSteps).mockResolvedValue([]);
    vi.mocked(listTasks).mockResolvedValue([]);
    vi.mocked(savePlanDraft).mockResolvedValue({} as never);
  });

  it('works as a guided form without AI, saves the draft, and adds the plan in one go with undo', async () => {
    vi.mocked(getAiStatus).mockResolvedValue({ ...ready, available: false, reason: 'NO_CONSENT' });
    vi.mocked(getPlanDraft).mockResolvedValue(null);
    vi.mocked(addPlanDraft).mockResolvedValue({ goalIds: [11], stepIds: [21], taskIds: [] });
    vi.mocked(undoPlan).mockResolvedValue(undefined);
    renderPage();

    expect(await screen.findByText('Target date', { selector: 'span' })).toBeInTheDocument();
    expect(await screen.findByText(/AI suggestions are off/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));

    expect(screen.queryByRole('button', { name: 'Suggest goals' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Add a goal' }));
    await userEvent.type(screen.getByRole('textbox', { name: 'Goal' }), 'Save a deposit');
    await vi.waitFor(() => expect(savePlanDraft).toHaveBeenCalled(), { timeout: 2000 });
    expect(vi.mocked(savePlanDraft).mock.lastCall?.[3].goals[0].title).toBe('Save a deposit');

    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    await userEvent.click(screen.getByRole('button', { name: 'Add a step' }));
    await userEvent.type(screen.getByRole('textbox', { name: 'Step' }), 'Visit three banks');
    await userEvent.type(screen.getByLabelText('Target date'), '2027-01-15');

    await userEvent.click(screen.getByRole('tab', { name: /Check and add/ }));
    expect(screen.getByText('Ready to add 1 goal(s), 1 step(s) and 0 task(s).')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Add plan' }));

    expect(addPlanDraft).toHaveBeenCalledWith('token', 5);
    // The latest edits are saved before adding.
    expect(vi.mocked(savePlanDraft).mock.lastCall?.[3].goals[0].steps[0].targetDate).toBe('2027-01-15');
    const notice = await screen.findByText(/Your plan is added: 1 goal\(s\), 1 step\(s\) and 0 task\(s\)/);
    await userEvent.click(within(notice.closest('[role="alert"]') as HTMLElement).getByRole('button', { name: 'Undo' }));
    expect(undoPlan).toHaveBeenCalledWith('token', { goalIds: [11], stepIds: [21], taskIds: [] });
  }, 30_000); // a long page test; slower when the whole suite runs at once

  it('picks up a saved draft where the participant left off (PB-FR-30)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(listGoals).mockResolvedValue([{ id: 3, dreamId: 5, title: 'Check my credit', priority: 'HIGH', archived: false } as Goal]);
    vi.mocked(getPlanDraft).mockResolvedValue({
      dreamId: 5, stage: 'GOALS', updatedAt: '', expiresAt: '',
      content: { goals: [{ key: 'g1', title: 'Save a deposit', priority: 'HIGH', moonshot: false, edited: false, steps: [] }] },
    });
    renderPage();

    expect(await screen.findByText('Picked up where you left off.')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Save a deposit')).toBeInTheDocument();
    // PB-FR-03: the existing goal is shown, not editable.
    expect(screen.getByText('Check my credit')).toBeInTheDocument();
    expect(screen.getByText('Already in your plan')).toBeInTheDocument();
  });

  it('adds AI goal suggestions to the draft as editable items', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(getPlanDraft).mockResolvedValue({ dreamId: 5, stage: 'GOALS', updatedAt: '', expiresAt: '', content: { goals: [] } });
    vi.mocked(suggestGoals).mockResolvedValue({ requestId: 9, goals: [{ title: 'Save $5,000', priority: 'HIGH', moonshot: false }] });
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Suggest goals' }));
    expect(await screen.findByDisplayValue('Save $5,000')).toBeInTheDocument();
    expect(screen.getByText('AI suggestion')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Helpful' })).toBeInTheDocument();
  });

  it('blocks adding while something new is missing an owner or date', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(getPlanDraft).mockResolvedValue({
      dreamId: 5, stage: 'REVIEW', updatedAt: '', expiresAt: '',
      content: { goals: [{ key: 'g1', title: 'Save', priority: 'HIGH', moonshot: false, edited: false, steps: [
        { key: 's1', title: 'Visit banks', owner: '', priority: 'MEDIUM', complex: false, edited: false, tasks: [] },
      ] }] },
    });
    renderPage();

    expect(await screen.findByText(/Step "Visit banks" needs an owner./)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add plan' })).toBeDisabled();
    await userEvent.click(screen.getAllByRole('button', { name: 'Go to Steps' })[0]);
    expect(screen.getByRole('textbox', { name: 'Owner' })).toBeInTheDocument();
  });
});
