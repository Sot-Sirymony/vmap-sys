import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { BlockerRollupLink } from './BlockerRollupLink';
import { OutcomeOnlyHint } from './OutcomeOnlyHint';
import { PreDreamCard, preDreamCardSkipped } from './PreDreamCard';

vi.mock('../../api/chainAssessmentApi', () => ({ getChainAssessments: vi.fn() }));

describe('outcome-only hint (V17 BL-FR-11 to BL-FR-13)', () => {
  beforeEach(() => localStorage.clear());

  it('always shows the outcome line, and the blocker note only while the text names a limit', () => {
    const { rerender } = render(<OutcomeOnlyHint level="dream" texts={['Own a home']} recordKey="dream-1" />);
    expect(screen.getByText(/Describe what success looks like/)).toBeInTheDocument();
    expect(screen.queryByText(/This sounds like a blocker/)).not.toBeInTheDocument();

    rerender(<OutcomeOnlyHint level="dream" texts={['Own a home if I can afford it']} recordKey="dream-1" />);
    expect(screen.getByText(/Keep the dream bold/)).toBeInTheDocument();
  });

  it('remembers a dismissal for that record only', async () => {
    const { unmount } = render(<OutcomeOnlyHint level="goal" texts={["Save, but I don't earn much"]} recordKey="goal-7" />);
    expect(screen.getByText(/Keep the goal about the result/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Hide this note' }));
    expect(screen.queryByText(/Keep the goal about the result/)).not.toBeInTheDocument();
    unmount();

    render(<OutcomeOnlyHint level="goal" texts={["Save, but I don't earn much"]} recordKey="goal-7" />);
    expect(screen.queryByText(/Keep the goal about the result/)).not.toBeInTheDocument();
    render(<OutcomeOnlyHint level="goal" texts={["Save, but I don't earn much"]} recordKey="goal-8" />);
    expect(screen.getByText(/Keep the goal about the result/)).toBeInTheDocument();
  });
});

describe('blocker roll-up link (V17 BL-FR-15, BL-FR-16)', () => {
  it('counts blockers and Blocked tasks and links to the filtered Obstacles page', () => {
    render(<MemoryRouter><BlockerRollupLink count={{ blockers: 2, blockedTasks: 1 }} goalId={4} /></MemoryRouter>);
    const link = screen.getByRole('link', { name: 'Blockers under this: 2 blockers · 1 blocked task' });
    expect(link).toHaveAttribute('href', '/obstacles?goalId=4');
  });

  it('shows nothing when nothing is blocked', () => {
    const { container } = render(<MemoryRouter><BlockerRollupLink count={{ blockers: 0, blockedTasks: 0 }} dreamId={3} /></MemoryRouter>);
    expect(container).toBeEmptyDOMElement();
  });
});

describe('pre-dream chain check card (V17 BL-FR-33, BLC-19)', () => {
  beforeEach(() => localStorage.clear());

  it('shows with no dreams and no chain check, and Skip for now hides it for 30 days', async () => {
    const { getChainAssessments } = await import('../../api/chainAssessmentApi');
    vi.mocked(getChainAssessments).mockResolvedValue({ assessments: [], improvementPercent: null, retakeSuggested: false });
    const { unmount } = render(<MemoryRouter><PreDreamCard token="t" hasDreams={false} /></MemoryRouter>);
    expect(await screen.findByText('Before you map a dream, take two minutes to see what might hold you back inside.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Take the 2-minute check' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Skip for now' }));
    expect(screen.queryByText(/take two minutes/)).not.toBeInTheDocument();
    unmount();

    render(<MemoryRouter><PreDreamCard token="t" hasDreams={false} /></MemoryRouter>);
    expect(preDreamCardSkipped()).toBe(true);
    expect(preDreamCardSkipped(Date.now() + 31 * 86_400_000)).toBe(false);
    expect(screen.queryByText(/take two minutes/)).not.toBeInTheDocument();
  });

  it('stays hidden once a chain check exists, or when there are dreams', async () => {
    const { getChainAssessments } = await import('../../api/chainAssessmentApi');
    vi.mocked(getChainAssessments).mockResolvedValue({
      assessments: [{ id: 1, takenOn: '2026-11-01', settlingScore: 3, fearScore: 3, criticismScore: 3, visionScore: 3, averageScore: 3 }],
      improvementPercent: null, retakeSuggested: false,
    });
    render(<MemoryRouter><PreDreamCard token="t" hasDreams={false} /></MemoryRouter>);
    await waitFor(() => expect(getChainAssessments).toHaveBeenCalled());
    expect(screen.queryByText(/take two minutes/)).not.toBeInTheDocument();
    render(<MemoryRouter><PreDreamCard token="t" hasDreams /></MemoryRouter>);
    expect(screen.queryByText(/take two minutes/)).not.toBeInTheDocument();
  });
});
