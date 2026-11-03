import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getAiStatus } from '../../api/aiApi';
import type { AiStatus } from '../../types/vision';
import { DreamCoachLauncher } from './DreamCoachLauncher';

vi.mock('../../context/AuthContext', () => ({ useAuth: () => ({ token: 'token' }) }));
vi.mock('../../api/aiApi', () => ({
  getAiStatus: vi.fn(),
  setAiConsent: vi.fn(),
  getAiConversation: vi.fn(),
  streamAiMessage: vi.fn(),
  rateAiMessage: vi.fn(),
}));

const ready: AiStatus = { available: true, reason: 'OK', consentGiven: true, dailyLimit: 20, messagesLeftToday: 5 };

const at = (path: string) => render(<MemoryRouter initialEntries={[path]}><DreamCoachLauncher /></MemoryRouter>);

describe('DreamCoachLauncher (AI-FR-01)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('stays hidden while AI is switched off for everyone', async () => {
    vi.mocked(getAiStatus).mockResolvedValue({ ...ready, available: false, reason: 'OFF' });
    at('/today');
    await vi.waitFor(() => expect(getAiStatus).toHaveBeenCalled());
    expect(screen.queryByRole('button', { name: 'Open the Dream Coach' })).not.toBeInTheDocument();
  });

  it('opens a panel with three questions for the page on screen', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    at('/modules/3');
    await userEvent.click(await screen.findByRole('button', { name: 'Open the Dream Coach' }));
    expect(await screen.findByText('Help me start this exercise')).toBeInTheDocument();
    expect(screen.getByText('Improve my answer')).toBeInTheDocument();
    expect(screen.getByText('5 messages left today')).toBeInTheDocument();
  });
});
