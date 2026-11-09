import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ProgressBar } from './ProgressBar';

describe('ProgressBar', () => {
  it('renders the progress label', () => {
    render(<ProgressBar value={45} />);

    expect(screen.getByLabelText('Progress 45%')).toBeInTheDocument();
  });

  it('clamps progress above 100', () => {
    render(<ProgressBar value={130} />);

    expect(screen.getByLabelText('Progress 100%')).toBeInTheDocument();
  });

  it('stays the accent colour unless asked to colour by progress', () => {
    render(<ProgressBar value={45} />);
    expect(screen.getByLabelText('Progress 45%').className).not.toMatch(/progress-bar--/);
  });

  it('takes the colour of its progress level when coloured', () => {
    render(
      <>
        <ProgressBar value={10} colored />
        <ProgressBar value={50} colored />
        <ProgressBar value={80} colored />
        <ProgressBar value={100} colored />
      </>,
    );
    expect(screen.getByLabelText('Progress 10%')).toHaveClass('progress-bar--low');
    expect(screen.getByLabelText('Progress 50%')).toHaveClass('progress-bar--mid');
    expect(screen.getByLabelText('Progress 80%')).toHaveClass('progress-bar--high');
    expect(screen.getByLabelText('Progress 100%')).toHaveClass('progress-bar--done');
  });
});
