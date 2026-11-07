import { render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { getPlanMetrics } from '../../api/aiPlanApi';
import { PlanMetricsCard } from './PlanMetricsCard';

vi.mock('../../api/aiPlanApi', () => ({ getPlanMetrics: vi.fn() }));

describe('PlanMetricsCard (PB-FR-36)', () => {
  it('shows finished plans and each feature\'s requests and cost per finished plan', async () => {
    vi.mocked(getPlanMetrics).mockResolvedValue({
      days: 30,
      finishedPlans: 2,
      requestsPerFinishedPlan: 6.5,
      costPerFinishedPlan: 0.1234,
      features: [
        { feature: 'GOAL_BUILDER', requests: 9, suggestedItems: 20, addedItems: 8, editedItems: 2, acceptanceRate: 0.5, editRate: 0.2,
          ratings: 0, costUsd: 0.2, averageCostUsd: 0.0222, requestsPerFinishedPlan: 2.5, costPerFinishedPlan: 0.05 },
        { feature: 'AREA_BUILDER', requests: 1, suggestedItems: 3, addedItems: 1, editedItems: 0, ratings: 0, costUsd: 0.01,
          averageCostUsd: 0.01, requestsPerFinishedPlan: null, costPerFinishedPlan: null },
      ],
    });
    render(<PlanMetricsCard token="token" />);

    expect(await screen.findByText('2 plan(s) finished with Build my plan: 6.5 AI requests and $0.1234 each on average.')).toBeInTheDocument();
    const goals = screen.getByRole('row', { name: /^Goals/ });
    expect(within(goals).getByText('2.5')).toBeInTheDocument();
    expect(within(goals).getByText('$0.0500')).toBeInTheDocument();
    expect(within(screen.getByRole('row', { name: /^Vision areas/ })).getAllByText('–').length).toBeGreaterThanOrEqual(2);
  });
});
