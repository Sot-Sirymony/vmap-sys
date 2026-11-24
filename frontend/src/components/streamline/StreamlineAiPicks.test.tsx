import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { renderPage } from '../../test/render';
import { StreamlineAiPicks } from './StreamlineAiPicks';

vi.mock('../../api/aiApi', () => ({ getAiStatus: vi.fn() }));
vi.mock('../../api/streamlineApi');

const { getAiStatus } = await import('../../api/aiApi');
const streamlineApi = await import('../../api/streamlineApi');

describe('StreamlineAiPicks (V30 D-30-4)', () => {
  it('lists the AI picks, best first, and starts a review from one', async () => {
    const user = userEvent.setup();
    vi.mocked(getAiStatus).mockResolvedValue({ available: true, reason: 'OK', consentGiven: true, dailyLimit: 20, messagesLeftToday: 20 });
    vi.mocked(streamlineApi.pickStreamlineCandidates).mockResolvedValue({ requestId: 3, candidates: [
      { anchorType: 'STEP', anchorId: 8, code: 'S-008', title: 'Collect the data', reason: 'Three tasks wait on it.' },
      { anchorType: 'GOAL', anchorId: 2, code: 'G-002', title: 'Finish the study', reason: 'Two steps could become one.' },
    ] });
    const onStart = vi.fn();
    renderPage(<StreamlineAiPicks token="token" onStart={onStart} />);

    await user.click(await screen.findByRole('button', { name: 'Ask AI which to streamline' }));
    const first = await screen.findByLabelText('AI pick 1: Collect the data');
    expect(first).toHaveTextContent('Three tasks wait on it.');
    await user.click(screen.getAllByRole('button', { name: 'Start review' })[1]);
    expect(onStart).toHaveBeenCalledWith(expect.objectContaining({ anchorType: 'GOAL', anchorId: 2 }));
  });

  it('says why AI is missing, and offers no button', async () => {
    vi.mocked(getAiStatus).mockResolvedValue({ available: false, reason: 'NO_CONSENT', consentGiven: false, dailyLimit: 20, messagesLeftToday: 20 });
    renderPage(<StreamlineAiPicks token="token" onStart={vi.fn()} />);
    expect(await screen.findByText(/AI suggestions need your AI consent/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Ask AI which to streamline' })).not.toBeInTheDocument();
  });

  it('says so plainly when nothing stands out', async () => {
    const user = userEvent.setup();
    vi.mocked(getAiStatus).mockResolvedValue({ available: true, reason: 'OK', consentGiven: true, dailyLimit: 20, messagesLeftToday: 20 });
    vi.mocked(streamlineApi.pickStreamlineCandidates).mockResolvedValue({ requestId: null, candidates: [] });
    renderPage(<StreamlineAiPicks token="token" onStart={vi.fn()} />);
    await user.click(await screen.findByRole('button', { name: 'Ask AI which to streamline' }));
    expect(await screen.findByText('Nothing stands out right now. Your open goals and steps look lean.')).toBeInTheDocument();
  });
});
