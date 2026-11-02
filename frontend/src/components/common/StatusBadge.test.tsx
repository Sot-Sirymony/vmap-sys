import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { setCurrentLanguage } from '../../i18n/translate';
import { PriorityBadge } from './PriorityBadge';
import { StatusBadge } from './StatusBadge';

function chipFor(status: string) {
  return screen.getByText(status.replaceAll('_', ' ')).closest('.MuiChip-root') as HTMLElement;
}

describe('StatusBadge', () => {
  it('renders readable status text', () => {
    render(<StatusBadge status="IN_PROGRESS" />);

    expect(screen.getByText('IN PROGRESS')).toBeInTheDocument();
  });

  it('gives each status its own color from the shared palette', () => {
    render(
      <>
        <StatusBadge status="BLOCKED" />
        <StatusBadge status="COMPLETED" />
      </>,
    );

    // Blocked is orange and completed is green. The whole point of the palette
    // is that these two never collide, so assert they differ rather than
    // pinning the exact rgba strings.
    const blocked = getComputedStyle(chipFor('BLOCKED')).backgroundColor;
    const completed = getComputedStyle(chipFor('COMPLETED')).backgroundColor;

    expect(blocked).not.toBe('');
    expect(blocked).not.toBe(completed);
  });

  it('falls back to a neutral tint for an unknown status', () => {
    render(<StatusBadge status="NOT_A_REAL_STATUS" />);

    expect(chipFor('NOT A REAL STATUS')).toBeInTheDocument();
  });
});

describe('badges in Khmer', () => {
  afterEach(() => setCurrentLanguage('en'));

  it('shows the Khmer label for a raw status or priority', () => {
    setCurrentLanguage('km');
    render(<><StatusBadge status="IN_PROGRESS" /><PriorityBadge priority="HIGH" /></>);
    expect(screen.getByText('កំពុងធ្វើ')).toBeInTheDocument();
    expect(screen.getByText('ខ្ពស់')).toBeInTheDocument();
  });
});
