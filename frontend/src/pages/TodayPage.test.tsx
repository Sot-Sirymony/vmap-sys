import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getDailyPlan, saveAttitudeCheckIn } from '../api/dailyPlanApi';
import { updateStepStatus } from '../api/stepApi';
import { updateTaskStatus } from '../api/taskApi';
import type { DailyPlan } from '../types/vision';
import { TodayPage } from './TodayPage';

vi.mock('../context/AuthContext', () => ({ useAuth: () => ({ token: 'token' }) }));
vi.mock('../context/ThemeModeContext', () => ({
  useThemeSettings: () => ({ settings: { highContrast: false }, resolvedMode: 'light' }),
}));
vi.mock('../api/dailyPlanApi', () => ({ getDailyPlan: vi.fn(), saveAttitudeCheckIn: vi.fn() }));
vi.mock('../api/habitApi', () => ({ archiveHabit: vi.fn(), createHabit: vi.fn(), setHabitChecked: vi.fn() }));
vi.mock('../api/stepApi', () => ({ updateStepStatus: vi.fn() }));
vi.mock('../api/taskApi', () => ({ updateTaskStatus: vi.fn() }));

const plan: DailyPlan = {
  date: '2026-11-01',
  items: [
    { kind: 'TASK', id: 7, code: 'T-007', title: 'Call the bank', owner: 'Me', priority: 'CRITICAL', dueDate: '2026-11-01', status: 'NOT_STARTED', overdue: false, dreamId: 1, dreamTitle: 'Own a home' },
    { kind: 'STEP', id: 3, code: 'S-003', title: 'Compare loans', owner: 'Me', priority: 'HIGH', dueDate: '2026-10-30', status: 'IN_PROGRESS', overdue: true, dreamId: 1, dreamTitle: 'Own a home' },
  ],
  attitude: null,
  habits: [],
  weeklyReviewDue: true,
  lastWeeklyReviewDate: null,
  undatedStepCount: 0,
};

function renderPage() {
  return render(<MemoryRouter><TodayPage /></MemoryRouter>);
}

describe('TodayPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getDailyPlan).mockResolvedValue(plan);
  });

  it('shows due work, marks what is overdue, and prompts the weekly review', async () => {
    renderPage();

    expect(await screen.findByText('Call the bank')).toBeInTheDocument();
    expect(screen.getByText('Overdue since 2026-10-30')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Start review' })).toBeInTheDocument();
  });

  it('completes a step and a task through their own endpoints', async () => {
    renderPage();

    await userEvent.click(await screen.findByRole('checkbox', { name: 'Complete Compare loans' }));
    await waitFor(() => expect(updateStepStatus).toHaveBeenCalledWith('token', 3, 'COMPLETED'));

    await userEvent.click(await screen.findByRole('checkbox', { name: 'Complete Call the bank' }));
    await waitFor(() => expect(updateTaskStatus).toHaveBeenCalledWith('token', 7, 'COMPLETED'));
  });

  it('saves the attitude score as soon as it is tapped', async () => {
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: '4' }));

    await waitFor(() => expect(saveAttitudeCheckIn).toHaveBeenCalledWith('token', expect.any(String), { score: 4, note: undefined }));
  });

  it('shows a save failure instead of swallowing it', async () => {
    vi.mocked(updateStepStatus).mockRejectedValueOnce(new Error('A complex step must have at least one task before it can be completed.'));
    renderPage();

    await userEvent.click(await screen.findByRole('checkbox', { name: 'Complete Compare loans' }));

    expect(await screen.findByText(/complex step must have at least one task/)).toBeInTheDocument();
  });

  it('links to the steps that have no date', async () => {
    vi.mocked(getDailyPlan).mockResolvedValue({ ...plan, undatedStepCount: 2 });
    renderPage();

    expect(await screen.findByText('2 steps have no date, so they cannot appear here.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Set dates' })).toBeInTheDocument();
  });
});
