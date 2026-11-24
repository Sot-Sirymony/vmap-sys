import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ArchiveConfirmDialog } from './ArchiveConfirmDialog';

describe('ArchiveConfirmDialog (V29 D-29-1)', () => {
  it('archives with no reason, or with the trimmed reason', async () => {
    const onConfirm = vi.fn();
    const { unmount } = render(<ArchiveConfirmDialog message="Archive this?" onConfirm={onConfirm} onClose={vi.fn()} />);
    await userEvent.click(screen.getByRole('button', { name: 'Archive' }));
    expect(onConfirm).toHaveBeenLastCalledWith('');
    unmount();

    render(<ArchiveConfirmDialog message="Archive this?" onConfirm={onConfirm} onClose={vi.fn()} />);
    await userEvent.type(screen.getByRole('textbox', { name: 'Why are you archiving this? (optional)' }), '  No longer needed  ');
    await userEvent.click(screen.getByRole('button', { name: 'Archive' }));
    expect(onConfirm).toHaveBeenLastCalledWith('No longer needed');
  });

  it('cancels without archiving', async () => {
    const onConfirm = vi.fn();
    const onClose = vi.fn();
    render(<ArchiveConfirmDialog message="Archive this?" onConfirm={onConfirm} onClose={onClose} />);
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onClose).toHaveBeenCalled();
    expect(onConfirm).not.toHaveBeenCalled();
  });
});
