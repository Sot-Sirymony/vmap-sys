import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getSharedPlan } from '../api/planShareApi';
import { SharedPlanPage } from './SharedPlanPage';

vi.mock('../api/planShareApi', () => ({ getSharedPlan: vi.fn() }));

const page = () => render(
  <MemoryRouter initialEntries={['/shared/abc123']}>
    <Routes><Route path="/shared/:token" element={<SharedPlanPage />} /></Routes>
  </MemoryRouter>,
);

describe('SharedPlanPage (v0.1 FR-10)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('shows the shared dream, the ask and its steps without signing in', async () => {
    vi.mocked(getSharedPlan).mockResolvedValue({
      ownerFirstName: 'Sokha',
      partnerName: 'Dara',
      message: 'Could you check my first steps?',
      dreamTitle: 'Open a bakery',
      progressPercent: 40,
      goals: [{ title: 'Plan the shop', status: 'IN_PROGRESS', progressPercent: 40,
        steps: [{ title: 'Write the menu', status: 'COMPLETED', targetDate: '2026-12-01' }] }],
    });
    page();

    expect(await screen.findByRole('heading', { name: 'Open a bakery' })).toBeInTheDocument();
    expect(getSharedPlan).toHaveBeenCalledWith('abc123');
    expect(screen.getByText('Sokha shared this plan with you')).toBeInTheDocument();
    expect(screen.getByText('Could you check my first steps?')).toBeInTheDocument();
    expect(screen.getByText(/Write the menu/)).toBeInTheDocument();
  });

  it('can show the shared goals and steps as a view-only timeline (TL-FR-48)', async () => {
    vi.mocked(getSharedPlan).mockResolvedValue({
      ownerFirstName: 'Sokha', dreamTitle: 'Open a bakery', progressPercent: 40, targetDate: '2027-06-30',
      goals: [{ title: 'Plan the shop', status: 'IN_PROGRESS', progressPercent: 40, targetDate: '2027-03-31',
        steps: [{ title: 'Write the menu', status: 'COMPLETED', targetDate: '2026-12-01' }] }],
    });
    page();
    await screen.findByRole('heading', { name: 'Open a bakery' });

    await userEvent.click(screen.getByRole('button', { name: 'Timeline' }));
    const timeline = within(screen.getByRole('region', { name: 'Timeline of Open a bakery' }));
    expect(timeline.getByRole('img', { name: /^Goal\s+Plan the shop/ })).toBeInTheDocument();
    expect(timeline.getByRole('img', { name: /^Step\s+Write the menu/ })).toBeInTheDocument();
    expect(timeline.queryAllByRole('button', { name: /^(Goal|Step)\s/ })).toHaveLength(0);

    await userEvent.click(screen.getByRole('button', { name: 'List' }));
    expect(screen.getByText(/Write the menu/)).toBeInTheDocument();
  });

  it('says plainly when the link no longer works', async () => {
    vi.mocked(getSharedPlan).mockRejectedValue(new Error('Share not found'));
    page();
    expect(await screen.findByText('This link has ended or was stopped.')).toBeInTheDocument();
  });
});
