import { render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { getPlanAccuracy } from '../../api/aiPlanApi';
import { PlanAccuracyCard } from './PlanAccuracyCard';

vi.mock('../../api/aiPlanApi', () => ({ getPlanAccuracy: vi.fn() }));

describe('PlanAccuracyCard (V31 DC-FR-65)', () => {
  it('shows the headline rates and each rule\'s first-check pass rate', async () => {
    vi.mocked(getPlanAccuracy).mockResolvedValue({
      days: 30, checkedSessions: 4, firstCheckPassRate: 0.5, savedPlans: 3, savedValidRate: 1,
      aiItemsChecked: 40, aiDateConflictRate: 0.025, aiOwners: 0, inventedOwnerRate: null,
      rules: [
        { rule: 'R-01', firstRuns: 4, firstPassRate: 1, saves: 3, acceptedShould: 0 },
        { rule: 'R-02', firstRuns: 4, firstPassRate: 0.75, saves: 3, acceptedShould: 2 },
      ],
    });
    render(<PlanAccuracyCard token="token" />);

    expect(await screen.findByText('Drafts passing the first check')).toBeInTheDocument();
    expect(screen.getByText('50%')).toBeInTheDocument();
    expect(screen.getAllByText('100%')).toHaveLength(2);
    expect(screen.getByText('3%')).toBeInTheDocument();
    expect(screen.getByText('–')).toBeInTheDocument();
    const goals = screen.getByRole('row', { name: /R-02/ });
    expect(within(goals).getByText('75%')).toBeInTheDocument();
    expect(within(goals).getByText('2')).toBeInTheDocument();
    expect(getPlanAccuracy).toHaveBeenCalledWith('token', 30);
  });
});
