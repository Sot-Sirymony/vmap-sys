import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { listGoals } from '../api/goalApi';
import { listSteps, updateStep } from '../api/stepApi';
import type { VisionStep } from '../types/vision';
import { UndatedStepsPage } from './UndatedStepsPage';

vi.mock('../context/AuthContext', () => ({ useAuth: () => ({ token: 'token' }) }));
vi.mock('../api/goalApi', () => ({ listGoals: vi.fn() }));
vi.mock('../api/stepApi', () => ({ listSteps: vi.fn(), updateStep: vi.fn() }));

const undated = { id: 1, code: 'S-001', goalId: 2, title: 'Old step', owner: 'Me', sequenceNumber: 1, complex: false, priority: 'HIGH', status: 'NOT_STARTED', progressPercent: 0, archived: false } as VisionStep;

describe('UndatedStepsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listGoals).mockResolvedValue([]);
  });

  it('lists only undated steps and saves the chosen date without changing the rest', async () => {
    vi.mocked(listSteps).mockResolvedValue([undated, { ...undated, id: 2, title: 'Dated step', targetDate: '2026-12-01' }]);
    render(<UndatedStepsPage />);

    expect(await screen.findByText('Old step')).toBeInTheDocument();
    expect(screen.queryByText('Dated step')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();

    await userEvent.type(screen.getByLabelText('Date for Old step'), '2026-12-25');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(updateStep).toHaveBeenCalledWith('token', 1, expect.objectContaining({ title: 'Old step', owner: 'Me', targetDate: '2026-12-25' })));
  });

  it('says so when nothing is left to date', async () => {
    vi.mocked(listSteps).mockResolvedValue([]);
    render(<UndatedStepsPage />);
    expect(await screen.findByText('Every step has a date.')).toBeInTheDocument();
  });
});
