import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ConfettiBurst, rememberCompletedGoals, shouldCelebrate } from './Celebration';
import { MobileCollapse } from './MobileCollapse';

function stubMatchMedia(matching: (query: string) => boolean) {
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: matching(query),
    media: query,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
  }));
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  window.localStorage.clear();
});

describe('goal celebration (DA-FR-14)', () => {
  it('celebrates only when more goals are complete than last time', () => {
    expect(shouldCelebrate(null, 3)).toBe(false);
    expect(shouldCelebrate(3, 3)).toBe(false);
    expect(shouldCelebrate(3, 2)).toBe(false);
    expect(shouldCelebrate(2, 3)).toBe(true);
  });

  it('remembers the count per person, returning the previous one', () => {
    expect(rememberCompletedGoals('7', 2)).toBeNull();
    expect(rememberCompletedGoals('7', 3)).toBe(2);
    expect(rememberCompletedGoals('8', 5)).toBeNull();
  });

  it('celebrates nothing when storage is unavailable', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    expect(rememberCompletedGoals('7', 3)).toBeNull();
  });

  it('never draws confetti under reduced motion (D-25-8)', () => {
    stubMatchMedia((query) => query.includes('prefers-reduced-motion'));
    const getContext = vi.spyOn(HTMLCanvasElement.prototype, 'getContext');
    render(<ConfettiBurst play={1} />);
    expect(screen.getByTestId('confetti')).toHaveAttribute('aria-hidden', 'true');
    expect(getContext).not.toHaveBeenCalled();
  });

  it('draws when motion is allowed and the burst is played', () => {
    stubMatchMedia(() => false);
    const getContext = vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null);
    render(<ConfettiBurst play={1} />);
    expect(getContext).toHaveBeenCalledWith('2d');
  });

  it('does nothing until it is played', () => {
    stubMatchMedia(() => false);
    const getContext = vi.spyOn(HTMLCanvasElement.prototype, 'getContext');
    render(<ConfettiBurst play={0} />);
    expect(getContext).not.toHaveBeenCalled();
  });
});

describe('MobileCollapse (BRD 25 §10)', () => {
  beforeEach(() => window.localStorage.clear());

  it('puts the card behind one tap on a phone', async () => {
    stubMatchMedia((query) => query.includes('max-width: 599px'));
    render(<MobileCollapse label="Show the progress trend"><p>trend card</p></MobileCollapse>);
    const toggle = screen.getByRole('button', { name: 'Show the progress trend' });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByText('trend card')).not.toBeInTheDocument();
    await userEvent.click(toggle);
    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByText('trend card')).toBeInTheDocument();
  });

  it('shows the card as it is from tablet width up', () => {
    stubMatchMedia(() => false);
    render(<MobileCollapse label="Show the progress trend"><p>trend card</p></MobileCollapse>);
    expect(screen.getByText('trend card')).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});
