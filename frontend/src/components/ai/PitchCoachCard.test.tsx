import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getPitchFeedback } from '../../api/aiDailyApi';
import { getAiStatus } from '../../api/aiApi';
import { PitchCoachCard } from './PitchCoachCard';

vi.mock('../../api/aiDailyApi', () => ({ getPitchFeedback: vi.fn() }));
vi.mock('../../api/aiApi', () => ({ getAiStatus: vi.fn() }));

describe('PitchCoachCard (AI-FR-16)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getAiStatus).mockResolvedValue({ reason: 'OK' } as never);
  });

  it('is not shown while AI is off (AI-NFR-02)', async () => {
    vi.mocked(getAiStatus).mockResolvedValue({ reason: 'OFF' } as never);
    render(<PitchCoachCard token="token" />);
    await vi.waitFor(() => expect(getAiStatus).toHaveBeenCalledWith('token'));
    expect(screen.queryByText('Pitch coach')).not.toBeInTheDocument();
  });

  it('practises the saved pitch with the chosen listener and offers better wording to use', async () => {
    vi.mocked(getPitchFeedback).mockResolvedValue({
      audience: 'SPONSOR', reaction: 'I need to know the cost.', clarity: 4, benefit: 2, ask: 3,
      improvedPitch: 'Sponsor ten loaves a day for the school.', tip: 'Say what they get.',
    });
    render(<PitchCoachCard token="token" initialPitch="Help my bakery" />);

    await userEvent.click(await screen.findByRole('combobox', { name: 'Who is listening?' }));
    await userEvent.click(await screen.findByRole('option', { name: 'A possible sponsor' }));
    await userEvent.click(screen.getByRole('button', { name: 'Practise' }));

    expect(getPitchFeedback).toHaveBeenCalledWith('token', 'SPONSOR', 'Help my bakery', 'en');
    expect(await screen.findByText('Benefit 2 of 5')).toBeInTheDocument();
    expect(screen.getByText('Say what they get.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Use as my pitch' }));
    expect(screen.getByLabelText('Your pitch')).toHaveValue('Sponsor ten loaves a day for the school.');
  });
});
