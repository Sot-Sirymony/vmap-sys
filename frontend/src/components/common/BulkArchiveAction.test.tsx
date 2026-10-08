import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { BulkArchiveAction } from './BulkArchiveAction';

const items = [
  { id: 1, archived: false },
  { id: 2, archived: true },
  { id: 3, archived: true },
];

function renderAction(selected: number[]) {
  const handlers = {
    onArchive: vi.fn().mockResolvedValue(undefined),
    onRestore: vi.fn().mockResolvedValue(undefined),
    onDeletePermanently: vi.fn().mockResolvedValue(undefined),
  };
  render(<BulkArchiveAction selectedIds={new Set(selected)} items={items} entityLabel="goal(s)" {...handlers} />);
  return handlers;
}

describe('BulkArchiveAction', () => {
  it('offers only archive when every selected row is active', () => {
    renderAction([1]);
    expect(screen.getByRole('button', { name: 'Archive selected (1)' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Restore selected/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Delete permanently/ })).not.toBeInTheDocument();
  });

  it('restores only the archived part of a mixed selection', async () => {
    const { onRestore, onArchive } = renderAction([1, 2, 3]);
    await userEvent.click(screen.getByRole('button', { name: 'Restore selected (2)' }));
    await userEvent.click(screen.getByRole('button', { name: 'Restore' }));
    expect(onRestore).toHaveBeenCalledWith([2, 3]);
    expect(onArchive).not.toHaveBeenCalled();
  });

  it('permanently deletes archived rows only after confirmation', async () => {
    const { onDeletePermanently } = renderAction([2, 3]);
    await userEvent.click(screen.getByRole('button', { name: 'Delete permanently (2)' }));
    expect(screen.getByText(/This cannot be undone/)).toBeInTheDocument();
    expect(onDeletePermanently).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: 'Delete permanently' }));
    expect(onDeletePermanently).toHaveBeenCalledWith([2, 3]);
  });
});
