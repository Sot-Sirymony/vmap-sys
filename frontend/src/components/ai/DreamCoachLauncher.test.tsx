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
    at('/goals');
    await userEvent.click(await screen.findByRole('button', { name: 'Open the Dream Coach' }));
    expect(await screen.findByText('Is my goal specific enough?')).toBeInTheDocument();
    expect(screen.getByText('Which goal should come first?')).toBeInTheDocument();
    expect(screen.getByText('5 AI requests left today')).toBeInTheDocument();
  });
});

describe('DreamCoachLauncher on plan pages (V11, PB-FR-50)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('steps aside on the Plan page and Build my plan, where the plan assistant is', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    at('/plan');
    await vi.waitFor(() => expect(getAiStatus).toHaveBeenCalled());
    expect(screen.queryByRole('button', { name: 'Open the Dream Coach' })).not.toBeInTheDocument();
  });

  it('steps aside on the five pages that have the page assistant (V15, AH-FR-11)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    for (const path of ['/modules', '/modules/3', '/assessment', '/obstacles', '/communication', '/work-style']) {
      const { unmount } = at(path);
      await vi.waitFor(() => expect(getAiStatus).toHaveBeenCalled());
      expect(screen.queryByRole('button', { name: 'Open the Dream Coach' }), path).not.toBeInTheDocument();
      unmount();
    }
  });

  it('is still there on a dream\'s own page', async () => {
    vi.mocked(getAiStatus).mockResolvedValue(ready);
    at('/dreams/4');
    expect(await screen.findByRole('button', { name: 'Open the Dream Coach' })).toBeInTheDocument();
  });
});
