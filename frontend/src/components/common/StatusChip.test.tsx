import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { setCurrentLanguage } from '../../i18n/translate';
import { familyOf, StatusChip, statusLabel } from './StatusChip';

const chip = (text: string) => screen.getByText(text).closest('[data-status-family]') as HTMLElement;

describe('StatusChip (BRD 25 §5.2, D-25-5)', () => {
  afterEach(() => setCurrentLanguage('en'));

  it('pairs a colour with an icon and a label, never colour alone', () => {
    render(<StatusChip status="IN_PROGRESS" />);
    const element = chip('In progress');
    expect(element).toHaveAttribute('data-status-family', 'moving');
    expect(element.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
  });

  it('gives Open obstacles the berry family and Blocked work the orange one', () => {
    render(
      <>
        <StatusChip status="OPEN" />
        <StatusChip status="BLOCKED" />
        <StatusChip status="WAITING" />
      </>,
    );
    expect(chip('Open')).toHaveAttribute('data-status-family', 'open');
    expect(chip('Blocked')).toHaveAttribute('data-status-family', 'blocked');
    expect(chip('Waiting')).toHaveAttribute('data-status-family', 'waiting');
  });

  it('accepts lower-case values and treats unknown ones as no state', () => {
    expect(familyOf('completed')).toBe('done');
    expect(familyOf('SOMETHING_NEW')).toBe('idle');
  });

  it('takes a custom label when the caller has a better name', () => {
    render(<StatusChip status="CONTACTED" label="Emailed" />);
    expect(chip('Emailed')).toHaveAttribute('data-status-family', 'moving');
  });

  it('translates the label in Khmer', () => {
    setCurrentLanguage('km');
    expect(statusLabel('COMPLETED')).toBe('បានបញ្ចប់');
    expect(statusLabel('IN_PROGRESS')).toBe('កំពុងធ្វើ');
  });
});
