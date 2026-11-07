import { act, renderHook, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../context/ToastContext';
import { useCrudEntity } from './useCrudEntity';

type Item = { id: number; title: string };
type ItemRequest = { title: string };

function api() {
  return {
    list: vi.fn<(token: string, includeArchived: boolean) => Promise<Item[]>>().mockResolvedValue([{ id: 1, title: 'Learn AI tools' }]),
    create: vi.fn<(token: string, request: ItemRequest) => Promise<Item>>().mockResolvedValue({ id: 2, title: 'New' }),
    update: vi.fn<(token: string, id: number, request: ItemRequest) => Promise<Item>>().mockResolvedValue({ id: 1, title: 'Changed' }),
    archive: vi.fn<(token: string, id: number) => Promise<void>>().mockResolvedValue(undefined),
    restore: vi.fn<(token: string, id: number) => Promise<void>>().mockResolvedValue(undefined),
    permanentlyDelete: vi.fn<(token: string, id: number) => Promise<void>>().mockResolvedValue(undefined),
  };
}

const wrapper = ({ children }: { children: ReactNode }) => <ToastProvider>{children}</ToastProvider>;

function renderCrud(calls = api(), extra: Partial<Parameters<typeof useCrudEntity<Item, ItemRequest>>[0]> = {}) {
  const hook = renderHook(() => useCrudEntity<Item, ItemRequest>({ token: 'token', entityLabel: 'goals', ...calls, ...extra }), { wrapper });
  return { ...hook, calls };
}

describe('useCrudEntity', () => {
  beforeEach(() => localStorage.clear());

  it('waits for reload, then loads the list without archived items', async () => {
    const { result, calls } = renderCrud();
    expect(result.current.loading).toBe(true);
    expect(calls.list).not.toHaveBeenCalled();

    await act(() => result.current.reload());
    expect(calls.list).toHaveBeenCalledWith('token', false);
    expect(result.current.items).toEqual([{ id: 1, title: 'Learn AI tools' }]);
    expect(result.current.loading).toBe(false);
    expect(result.current.error).toBe('');
  });

  it('does nothing without a session token', async () => {
    const calls = api();
    const { result } = renderHook(() => useCrudEntity<Item, ItemRequest>({ token: null, entityLabel: 'goals', ...calls }), { wrapper });
    await act(() => result.current.reload());
    await act(async () => expect(await result.current.save({ title: 'x' })).toBe(false));
    await act(() => result.current.archive(1));
    expect(calls.list).not.toHaveBeenCalled();
    expect(calls.create).not.toHaveBeenCalled();
    expect(calls.archive).not.toHaveBeenCalled();
  });

  it('shows the server message, or a fallback, when loading fails', async () => {
    const calls = api();
    calls.list.mockRejectedValueOnce(new Error('Server is down.')).mockRejectedValueOnce('no reason');
    const { result } = renderCrud(calls);
    await act(() => result.current.reload());
    expect(result.current.error).toBe('Server is down.');
    await act(() => result.current.reload());
    expect(result.current.error).toBe('Unable to load goals.');
  });

  it('creates a new record, or updates the one being edited, then reloads and confirms', async () => {
    const { result, calls } = renderCrud();
    await act(async () => expect(await result.current.save({ title: 'New' })).toBe(true));
    expect(calls.create).toHaveBeenCalledWith('token', { title: 'New' });
    expect(await screen.findByText('Saved.')).toBeInTheDocument();

    act(() => result.current.startEdit(1));
    expect(result.current.editingId).toBe(1);
    await act(() => result.current.save({ title: 'Changed' }));
    expect(calls.update).toHaveBeenCalledWith('token', 1, { title: 'Changed' });
    expect(result.current.editingId).toBeNull();
    expect(calls.list).toHaveBeenCalledTimes(2);

    act(() => result.current.startEdit(1));
    act(() => result.current.cancelEdit());
    expect(result.current.editingId).toBeNull();
  });

  it('keeps the form open with the reason when saving fails', async () => {
    const calls = api();
    calls.create.mockRejectedValueOnce(new Error('Title is required.')).mockRejectedValueOnce(42);
    const { result } = renderCrud(calls);
    await act(async () => expect(await result.current.save({ title: '' })).toBe(false));
    expect(result.current.error).toBe('Title is required.');
    await act(() => result.current.save({ title: '' }));
    expect(result.current.error).toBe('Unable to save goals.');
    expect(result.current.saving).toBe(false);
  });

  it('archives with an Undo that restores the record (FR-29.3)', async () => {
    const { result, calls } = renderCrud(api(), { undoableArchive: true });
    await act(() => result.current.archive(1));
    expect(calls.archive).toHaveBeenCalledWith('token', 1);

    await userEvent.click(await screen.findByRole('button', { name: 'Undo' }));
    await waitFor(() => expect(calls.restore).toHaveBeenCalledWith('token', 1));
    expect(await screen.findByText('Restored.')).toBeInTheDocument();
  });

  it('archives without Undo for records with children, and reports a failed archive', async () => {
    const calls = api();
    const { result } = renderCrud(calls, { archiveMessage: 'Goal archived.' });
    await act(() => result.current.archive(1));
    expect(await screen.findByText('Goal archived.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Undo' })).not.toBeInTheDocument();

    calls.archive.mockRejectedValueOnce(new Error('Goal has open steps.'));
    await act(() => result.current.archive(1));
    expect(result.current.error).toBe('Goal has open steps.');
  });

  it('archives several records at once, with Undo restoring them all', async () => {
    const { result, calls } = renderCrud(api(), { undoableArchive: true });
    await act(() => result.current.archiveMany([]));
    expect(calls.archive).not.toHaveBeenCalled();

    await act(() => result.current.archiveMany([1, 2]));
    expect(calls.archive).toHaveBeenCalledTimes(2);
    expect(await screen.findByText('Archived 2 items.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Undo' }));
    await waitFor(() => expect(calls.restore).toHaveBeenCalledTimes(2));
  });

  it('reports a failed bulk archive, and names one item correctly', async () => {
    const calls = api();
    const { result } = renderCrud(calls);
    await act(() => result.current.archiveMany([1]));
    expect(await screen.findByText('Archived 1 item.')).toBeInTheDocument();
    calls.archive.mockRejectedValueOnce('nope');
    await act(() => result.current.archiveMany([1, 2]));
    expect(result.current.error).toBe('Unable to archive goals.');
  });

  it('restores and permanently deletes one record', async () => {
    const calls = api();
    const { result } = renderCrud(calls, { deleteMessage: 'Goal deleted.' });
    await act(() => result.current.restore(3));
    expect(calls.restore).toHaveBeenCalledWith('token', 3);
    await act(() => result.current.permanentlyDelete(3));
    expect(calls.permanentlyDelete).toHaveBeenCalledWith('token', 3);
    expect(await screen.findByText('Goal deleted.')).toBeInTheDocument();

    calls.restore.mockRejectedValueOnce(new Error('Parent is archived.'));
    await act(() => result.current.restore(3));
    expect(result.current.error).toBe('Parent is archived.');
    calls.permanentlyDelete.mockRejectedValueOnce('x');
    await act(() => result.current.permanentlyDelete(3));
    expect(result.current.error).toBe('Unable to delete goals.');
  });

  it('runs bulk restore and delete for each record, keeping the successes and counting the failures', async () => {
    const calls = api();
    calls.restore.mockResolvedValueOnce(undefined).mockRejectedValueOnce(new Error('Parent is archived.'));
    const { result } = renderCrud(calls);
    await act(() => result.current.restoreMany([1, 2]));
    expect(await screen.findByText('Restored 1 item.')).toBeInTheDocument();
    expect(result.current.error).toBe('Unable to restore 1 of 2 goals. Parent is archived.');

    await act(() => result.current.permanentlyDeleteMany([1, 2]));
    expect(calls.permanentlyDelete).toHaveBeenCalledTimes(2);
    expect(await screen.findByText('Permanently deleted 2 items.')).toBeInTheDocument();
  });

  it('skips restore and delete when the page does not offer them', async () => {
    const { list, create, update, archive } = api();
    const { result } = renderHook(
      () => useCrudEntity<Item, ItemRequest>({ token: 'token', entityLabel: 'reviews', list, create, update, archive }),
      { wrapper },
    );
    await act(async () => {
      await result.current.restore(1);
      await result.current.restoreMany([1]);
      await result.current.permanentlyDelete(1);
      await result.current.permanentlyDeleteMany([1]);
    });
    expect(list).not.toHaveBeenCalled();
  });

  it('remembers the show-archived choice and reloads with it (FR-23.3)', async () => {
    const { result, calls } = renderCrud();
    act(() => result.current.toggleShowArchived());
    await waitFor(() => expect(calls.list).toHaveBeenCalledWith('token', true));
    expect(result.current.showArchived).toBe(true);
    expect(localStorage.getItem('vms-show-archived-goals')).toBe('true');
  });
});
