import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { FilterPanel } from './FilterPanel';

/**
 * The collapsible list-page filter card. Collapsing is a CSS concern (the
 * content is display:none below the md breakpoint until opened; always
 * visible above it), so jsdom can only assert the state wiring: the toggle
 * flips aria-expanded and the collapsed class, and the badge reflects how
 * many filters are narrowing the list.
 */
describe('FilterPanel', () => {
  it('renders its controls and starts collapsed for the mobile toggle', () => {
    render(<FilterPanel><label>Status<input /></label></FilterPanel>);

    expect(screen.getByText('Status')).toBeInTheDocument();
    const toggle = screen.getByRole('button', { name: /filters/i });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
  });

  it('expands and collapses from the toggle', async () => {
    const user = userEvent.setup();
    render(<FilterPanel><span>controls</span></FilterPanel>);
    const toggle = screen.getByRole('button', { name: /filters/i });

    await user.click(toggle);
    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    const content = document.getElementById(toggle.getAttribute('aria-controls') ?? '');
    expect(content?.className).not.toContain('filter-bar--collapsed');

    await user.click(toggle);
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    expect(content?.className).toContain('filter-bar--collapsed');
  });

  it('shows how many filters are active, and hides a zero', () => {
    const { rerender } = render(<FilterPanel activeCount={3}><span /></FilterPanel>);
    expect(screen.getByText('3')).toBeInTheDocument();

    rerender(<FilterPanel activeCount={0}><span /></FilterPanel>);
    expect(screen.queryByText('0')).not.toBeInTheDocument();
  });
});
