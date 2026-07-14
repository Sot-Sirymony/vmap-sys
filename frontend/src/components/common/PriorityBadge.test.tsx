import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { PriorityBadge } from './PriorityBadge';

function chipFor(priority: string) {
  return screen.getByText(priority).closest('.MuiChip-root') as HTMLElement;
}

describe('PriorityBadge', () => {
  it('renders the priority label', () => {
    render(<PriorityBadge priority="CRITICAL" />);

    expect(screen.getByText('CRITICAL')).toBeInTheDocument();
  });

  it('gives every level its own color, including HIGH vs CRITICAL', () => {
    render(
      <>
        <PriorityBadge priority="LOW" />
        <PriorityBadge priority="MEDIUM" />
        <PriorityBadge priority="HIGH" />
        <PriorityBadge priority="CRITICAL" />
      </>,
    );

    // HIGH and CRITICAL used to render as the same red chip, which made the top
    // of the escalation scale unreadable. All four levels must now differ.
    const shades = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map(
      (level) => getComputedStyle(chipFor(level)).backgroundColor,
    );

    expect(new Set(shades).size).toBe(4);
  });
});
