import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getMyCohort, joinCohort, setJournalSharing } from '../../api/cohortApi';
import { CohortCard } from './CohortCard';

vi.mock('../../api/cohortApi', () => ({
  getMyCohort: vi.fn(),
  joinCohort: vi.fn(),
  leaveCohort: vi.fn(),
  setJournalSharing: vi.fn(),
}));

const joined = { cohortId: 3, cohortName: 'Pilot', coachName: 'Dara', startDate: null, shareJournalsWithCoach: false };

describe('CohortCard', () => {
  beforeEach(() => vi.clearAllMocks());

  it('joins with a code and then names the cohort and coach', async () => {
    vi.mocked(getMyCohort).mockResolvedValue({ shareJournalsWithCoach: false });
    vi.mocked(joinCohort).mockResolvedValue(joined);
    render(<CohortCard token="token" />);

    await userEvent.type(await screen.findByLabelText('Join code'), ' abcd2345 ');
    await userEvent.click(screen.getByRole('button', { name: 'Join' }));

    await waitFor(() => expect(joinCohort).toHaveBeenCalledWith('token', 'abcd2345'));
    expect(await screen.findByText('You are in Pilot, coached by Dara.')).toBeInTheDocument();
  });

  it('shows the server reason when a code is wrong', async () => {
    vi.mocked(getMyCohort).mockResolvedValue({ shareJournalsWithCoach: false });
    vi.mocked(joinCohort).mockRejectedValue(new Error('That join code was not found. Check it with your coach or administrator.'));
    render(<CohortCard token="token" />);

    await userEvent.type(await screen.findByLabelText('Join code'), 'NOPE');
    await userEvent.click(screen.getByRole('button', { name: 'Join' }));

    expect(await screen.findByText(/join code was not found/)).toBeInTheDocument();
  });

  it('keeps journals private until the switch is turned on', async () => {
    vi.mocked(getMyCohort).mockResolvedValue(joined);
    vi.mocked(setJournalSharing).mockResolvedValue({ ...joined, shareJournalsWithCoach: true });
    render(<CohortCard token="token" />);

    const toggle = await screen.findByRole('switch', { name: 'Share my fear and attitude journals with my coach' });
    expect(toggle).not.toBeChecked();
    await userEvent.click(toggle);

    await waitFor(() => expect(setJournalSharing).toHaveBeenCalledWith('token', true));
    expect(toggle).toBeChecked();
  });
});
