import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it } from 'vitest';
import { BlockerRollupLink } from './BlockerRollupLink';
import { OutcomeOnlyHint } from './OutcomeOnlyHint';

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
