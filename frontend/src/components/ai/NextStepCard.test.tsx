import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getAiStatus } from '../../api/aiApi';
import { suggestNextStep } from '../../api/aiDailyApi';
import type { AiStatus } from '../../types/vision';
import { NextStepCard } from './NextStepCard';

vi.mock('../../api/aiApi', () => ({ getAiStatus: vi.fn() }));
vi.mock('../../api/aiDailyApi', () => ({ suggestNextStep: vi.fn() }));

const ready: AiStatus = { available: true, reason: 'OK', consentGiven: true, dailyLimit: 10, messagesLeftToday: 10 };
const card = () => render(<MemoryRouter><NextStepCard token="token" /></MemoryRouter>);

describe('NextStepCard (AI-FR-12)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('stays hidden while AI is off for everyone', async () => {
    vi.mocked(getAiStatus).mockResolvedValue({ ...ready, available: false, reason: 'OFF' });
    const { container } = card();
    await vi.waitFor(() => expect(getAiStatus).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });

  it('asks for the time chosen and shows the best step first, linked to its page', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    vi.mocked(suggestNextStep).mockResolvedValue({ picks: [
      { itemType: 'STEP', itemId: 4, title: 'Call the bank', reason: 'Overdue and quick', minutes: 15 },
      { itemType: 'TASK', itemId: 7, title: 'Bring passport' },
    ], note: 'One step at a time.' });
    card();

    await userEvent.click(await screen.findByRole('button', { name: '1 h' }));
    await userEvent.click(screen.getByRole('button', { name: 'Suggest' }));
    expect(suggestNextStep).toHaveBeenCalledWith('token', 60, 'en');
    expect(await screen.findByText('Best next step')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Call the bank' })).toHaveAttribute('href', '/steps');
    expect(screen.getByRole('link', { name: 'Bring passport' })).toHaveAttribute('href', '/tasks');
    expect(screen.getByText('about 15 min')).toBeInTheDocument();
  });
});
