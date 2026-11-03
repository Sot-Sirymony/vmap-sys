import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { listSlippingSteps, rescueStep } from '../../api/aiPlanApi';
import { createStep, listSteps, updateStep } from '../../api/stepApi';
import type { VisionStep } from '../../types/vision';
import { SlippingStepsCard } from './SlippingStepsCard';

vi.mock('../../context/AuthContext', () => ({ useAuth: () => ({ token: 'token', user: { fullName: 'Sokha Chan' } }) }));
vi.mock('../../api/aiPlanApi', () => ({ listSlippingSteps: vi.fn(), rescueStep: vi.fn() }));
vi.mock('../../api/stepApi', () => ({ createStep: vi.fn(), listSteps: vi.fn(), updateStep: vi.fn() }));

const slipping = { stepId: 4, title: 'Call the bank', goalId: 9, goalTitle: 'Save a deposit', targetDate: '2026-10-30', overdueReschedules: 2 };

const card = () => render(<MemoryRouter><SlippingStepsCard token="token" /></MemoryRouter>);

describe('SlippingStepsCard (AI-FR-11)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('stays hidden when nothing is slipping', async () => {
    vi.mocked(listSlippingSteps).mockResolvedValue([]);
    const { container } = card();
    await vi.waitFor(() => expect(listSlippingSteps).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });

  it('offers ideas, and only saves the one the participant picks', async () => {
    vi.mocked(listSlippingSteps).mockResolvedValue([slipping]);
    vi.mocked(rescueStep).mockResolvedValue({ stepId: 4, smallerStep: 'Find the number', newDate: '2026-11-20', helper: 'A friend', reason: 'Calls feel heavy' });
    vi.mocked(listSteps).mockResolvedValue([{ id: 4, goalId: 9, title: 'Call the bank', owner: 'Me', sequenceNumber: 1, complex: false,
      priority: 'HIGH', status: 'NOT_STARTED', targetDate: '2026-10-30', resources: 'Phone' } as VisionStep]);
    card();

    expect(await screen.findByText('Moved 2 time(s)', { exact: false })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Get ideas' }));
    expect(await screen.findByText('Smaller step: Find the number')).toBeInTheDocument();
    expect(createStep).not.toHaveBeenCalled();
    expect(updateStep).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: 'Use this date' }));
    expect(updateStep).toHaveBeenCalledWith('token', 4, expect.objectContaining({ targetDate: '2026-11-20', resources: 'Phone' }));
    expect(createStep).not.toHaveBeenCalled();
  });
});
