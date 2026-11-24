import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { currentLocation, renderPage } from '../test/render';
import type { StreamlineCandidate, StreamlineListItem } from '../types/streamline';

vi.mock('../api/streamlineApi');

const api = await import('../api/streamlineApi');
const { StreamlineListPage, StreamlineStartPage } = await import('./StreamlineListPage');
const { StreamlineCard } = await import('../components/dashboard/StreamlineCard');
const { CoachStreamlineCard } = await import('../components/streamline/CoachStreamlineCard');

const reviews: StreamlineListItem[] = [
  { id: 4, code: 'SR-004', anchorType: 'GOAL', goalId: 3, stepId: null, anchorTitle: 'Save a deposit', status: 'COMPLETED', currentStage: 5,
    startedAt: '2026-11-10T00:00:00Z', completedAt: '2026-11-12T00:00:00Z', deleted: 5, addedBack: 1, stepsBefore: 9, stepsAfter: 4, tasksBefore: 30, tasksAfter: 20 },
  { id: 5, code: 'SR-005', anchorType: 'STEP', goalId: null, stepId: 8, anchorTitle: 'Compare loans', status: 'OPEN', currentStage: 2,
    startedAt: '2026-11-20T00:00:00Z', completedAt: null, deleted: 0, addedBack: 0, stepsBefore: 0, stepsAfter: 0, tasksBefore: 6, tasksAfter: 6 },
  { id: 6, code: 'SR-006', anchorType: 'STEP', goalId: null, stepId: 9, anchorTitle: 'Old idea', status: 'ABANDONED', currentStage: 3,
    startedAt: '2026-11-01T00:00:00Z', completedAt: null, deleted: 1, addedBack: 0, stepsBefore: 0, stepsAfter: 0, tasksBefore: 2, tasksAfter: 1 },
];

const candidates: StreamlineCandidate[] = [
  { anchorType: 'GOAL', anchorId: 3, code: 'G-003', title: 'Save a deposit', reason: 'MANY_STEPS', detail: '9 open steps' },
  { anchorType: 'STEP', anchorId: 8, code: 'S-008', title: 'Compare loans', reason: 'REPEATED_WORK', detail: '"Call the bank" done 4 times in 60 days' },
];

describe('StreamlineListPage (SR-FR-11)', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.mocked(api.listStreamlineReviews).mockResolvedValue(reviews);
    vi.mocked(api.listStreamlineCandidates).mockResolvedValue(candidates);
    vi.mocked(api.listTaskRepeatRules).mockResolvedValue([]);
  });

  it('lists reviews with their stage or result, and suggestion cards', async () => {
    renderPage(<StreamlineListPage />);
    expect(await screen.findByText('Save a deposit', { selector: 'p' })).toBeInTheDocument();
    expect(screen.getByText('Completed')).toBeInTheDocument();
    expect(screen.getByText('Stage 2: Delete')).toBeInTheDocument();
    expect(screen.getByText('Abandoned')).toBeInTheDocument();
    expect(screen.getByText('Steps 9 → 4 · 5 cut · 1 brought back')).toBeInTheDocument();
    const cards = screen.getByRole('region', { name: 'Worth streamlining' });
    expect(within(cards).getByText(/Many open steps — 9 open steps/)).toBeInTheDocument();
  });

  it('starts a review from a suggestion and opens it', async () => {
    const user = userEvent.setup();
    vi.mocked(api.startStreamlineReview).mockResolvedValue({ id: 12 } as never);
    renderPage(<StreamlineListPage />, { path: '/streamline', route: '/streamline' });
    const cards = await screen.findByRole('region', { name: 'Worth streamlining' });
    await user.click(within(cards).getAllByRole('button', { name: 'Run a Streamline review' })[1]);
    expect(api.startStreamlineReview).toHaveBeenCalledWith('token', { stepId: 8 });
    expect(currentLocation()).toBe('/streamline/12');
  });

  it('hides a dismissed suggestion, and says why a review could not start', async () => {
    const user = userEvent.setup();
    vi.mocked(api.startStreamlineReview).mockRejectedValue(new Error('This already has an open Streamline review. Finish or abandon it first.'));
    renderPage(<StreamlineListPage />);
    const cards = await screen.findByRole('region', { name: 'Worth streamlining' });
    await user.click(within(cards).getAllByRole('button', { name: 'Not now' })[0]);
    expect(within(cards).queryByText(/9 open steps/)).not.toBeInTheDocument();
    await user.click(within(cards).getByRole('button', { name: 'Run a Streamline review' }));
    expect(await screen.findByText(/already has an open Streamline review/)).toBeInTheDocument();
  });

  it('shows an empty state with no reviews', async () => {
    vi.mocked(api.listStreamlineReviews).mockResolvedValue([]);
    vi.mocked(api.listStreamlineCandidates).mockResolvedValue([]);
    renderPage(<StreamlineListPage />);
    expect(await screen.findByText(/No Streamline reviews yet/)).toBeInTheDocument();
  });

  it('shows the error when reviews cannot load', async () => {
    vi.mocked(api.listStreamlineReviews).mockRejectedValue(new Error('Could not load reviews.'));
    renderPage(<StreamlineListPage />);
    expect(await screen.findByText('Could not load reviews.')).toBeInTheDocument();
  });
});

describe('StreamlineStartPage (SR-FR-01)', () => {
  it('starts a review of the goal in the address and opens it', async () => {
    vi.mocked(api.startStreamlineReview).mockResolvedValue({ id: 21 } as never);
    renderPage(<StreamlineStartPage />, { route: '/streamline/new?goalId=3', path: '/streamline/new' });
    await vi.waitFor(() => expect(currentLocation()).toBe('/streamline/21'));
    expect(api.startStreamlineReview).toHaveBeenCalledWith('token', { goalId: 3 });
  });

  it('explains why it could not start, with a way back to all reviews', async () => {
    const user = userEvent.setup();
    vi.mocked(api.startStreamlineReview).mockRejectedValue(new Error('This step has no open tasks to streamline yet.'));
    renderPage(<StreamlineStartPage />, { route: '/streamline/new?stepId=8', path: '/streamline/new' });
    expect(await screen.findByText('This step has no open tasks to streamline yet.')).toBeInTheDocument();
    expect(api.startStreamlineReview).toHaveBeenCalledWith('token', { stepId: 8 });
    await user.click(screen.getByRole('button', { name: 'All reviews' }));
    expect(currentLocation()).toBe('/streamline');
  });
});

describe('StreamlineCard (SR-FR-13)', () => {
  it('shows the quarter counts, the median, a table of weeks and one suggestion', async () => {
    const user = userEvent.setup();
    vi.mocked(api.listStreamlineCandidates).mockResolvedValue(candidates);
    renderPage(<StreamlineCard token="token" streamline={{
      openReviews: 1, cutThisQuarter: 6, addedBackThisQuarter: 1, medianCycleDays: 3.5,
      cycleTrend: [{ weekEnd: '2026-11-17', medianDays: null, tasks: 1 }, { weekEnd: '2026-11-24', medianDays: 3.5, tasks: 4 }],
    }} />);
    expect(screen.getByText('Cut this quarter').nextSibling).toHaveTextContent('6');
    expect(screen.getByText('Median days, start to done').nextSibling).toHaveTextContent('3.5 days');
    expect(await screen.findAllByRole('button', { name: 'Run a Streamline review' })).toHaveLength(1);
    await user.click(screen.getByRole('button', { name: /View as table/ }));
    expect(screen.getByRole('table')).toHaveTextContent('Tasks finished');
  });

  it('renders nothing when the dashboard has no Streamline figures', () => {
    vi.mocked(api.listStreamlineCandidates).mockResolvedValue([]);
    const { container } = renderPage(<StreamlineCard token="token" streamline={null} />);
    expect(container.textContent).not.toContain('Streamline');
  });
});

describe('CoachStreamlineCard (D-27-10)', () => {
  it('lists the participant’s reviews with read-only links', async () => {
    vi.mocked(api.listParticipantStreamlineReviews).mockResolvedValue(reviews);
    renderPage(<CoachStreamlineCard token="token" userId={7} />);
    const link = await screen.findByRole('link', { name: 'Save a deposit' });
    expect(link).toHaveAttribute('href', '/coach/participants/7/streamline/4');
    expect(screen.getByText(/Stage 2: Delete/)).toBeInTheDocument();
  });
});
