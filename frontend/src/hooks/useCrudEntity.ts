import { useState } from 'react';
import { useToast } from '../context/ToastContext';
import { useStoredState } from './useStoredState';

type UseCrudEntityConfig<T, TRequest> = {
  token: string | null;
  entityLabel: string;
  list: (token: string, includeArchived: boolean) => Promise<T[]>;
  create: (token: string, request: TRequest) => Promise<T>;
  update: (token: string, id: number, request: TRequest) => Promise<T>;
  archive: (token: string, id: number) => Promise<void>;
  restore?: (token: string, id: number) => Promise<void>;
  permanentlyDelete?: (token: string, id: number) => Promise<void>;
  saveMessage?: string;
  archiveMessage?: string;
  /**
   * FR-29.3: offer Undo on the archive toast. Enabled only for leaf entities
   * (no cascade), where restore provably recreates the exact prior state —
   * parents with archived children keep the confirm + show-archived flow.
   */
  undoableArchive?: boolean;
  restoreMessage?: string;
  deleteMessage?: string;
};

/**
 * Owns the load/save/archive/restore/edit state machine shared by every CRUD
 * page: list state, loading/saving/error flags, which record (if any) is
 * being edited, the create-vs-update branching on submit, and the
 * show-archived toggle (the list function receives it as `includeArchived`).
 *
 * Does not fetch on its own — call `reload()` from the page's own effect
 * (alongside any reference-data fetches the page needs for its dropdowns),
 * the same way the page already orchestrates its own loading today.
 * Field-specific state (individual useState per form field) stays in the
 * page, since that genuinely differs per entity.
 */
export function useCrudEntity<T extends { id: number }, TRequest>(config: UseCrudEntityConfig<T, TRequest>) {
  const {
    token, entityLabel, list, create, update, archive, restore, permanentlyDelete,
    saveMessage = 'Saved.', archiveMessage = 'Archived.', restoreMessage = 'Restored.',
    deleteMessage = 'Permanently deleted.', undoableArchive = false,
  } = config;
  const { showToast } = useToast();
  const [items, setItems] = useState<T[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [editingId, setEditingId] = useState<number | null>(null);
  // FR-23.3: the show-archived choice is remembered per entity per browser.
  const [showArchived, setShowArchived] = useStoredState(`vms-show-archived-${entityLabel}`, false);

  async function reload(includeArchived = showArchived) {
    if (!token) {
      return;
    }
    setLoading(true);
    try {
      setItems(await list(token, includeArchived));
      setError('');
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : `Unable to load ${entityLabel}.`);
    } finally {
      setLoading(false);
    }
  }

  function toggleShowArchived() {
    const next = !showArchived;
    setShowArchived(next);
    void reload(next);
  }

  async function save(request: TRequest) {
    if (!token) {
      return false;
    }
    setSaving(true);
    try {
      if (editingId !== null) {
        await update(token, editingId, request);
        setEditingId(null);
      } else {
        await create(token, request);
      }
      await reload();
      showToast(saveMessage);
      return true;
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : `Unable to save ${entityLabel}.`);
      return false;
    } finally {
      setSaving(false);
    }
  }

  async function undoArchive(ids: number[]) {
    if (!token || !restore) {
      return;
    }
    try {
      for (const id of ids) {
        await restore(token, id);
      }
      await reload();
      showToast(restoreMessage);
    } catch (restoreError) {
      setError(restoreError instanceof Error ? restoreError.message : `Unable to restore ${entityLabel}.`);
    }
  }

  async function remove(id: number) {
    if (!token) {
      return;
    }
    try {
      await archive(token, id);
      await reload();
      if (undoableArchive) {
        showToast(archiveMessage, { action: { label: 'Undo', onClick: () => void undoArchive([id]) } });
      } else {
        showToast(archiveMessage);
      }
    } catch (archiveError) {
      setError(archiveError instanceof Error ? archiveError.message : `Unable to archive ${entityLabel}.`);
    }
  }

  async function removeMany(ids: number[]) {
    if (!token || ids.length === 0) {
      return;
    }
    try {
      await Promise.all(ids.map((id) => archive(token, id)));
      await reload();
      if (undoableArchive) {
        showToast(`Archived ${ids.length} item${ids.length === 1 ? '' : 's'}.`, { action: { label: 'Undo', onClick: () => void undoArchive(ids) } });
      } else {
        showToast(`Archived ${ids.length} item${ids.length === 1 ? '' : 's'}.`);
      }
    } catch (archiveError) {
      setError(archiveError instanceof Error ? archiveError.message : `Unable to archive ${entityLabel}.`);
    }
  }

  async function restoreItem(id: number) {
    if (!token || !restore) {
      return;
    }
    try {
      await restore(token, id);
      await reload();
      showToast(restoreMessage);
    } catch (restoreError) {
      setError(restoreError instanceof Error ? restoreError.message : `Unable to restore ${entityLabel}.`);
    }
  }

  /**
   * Runs one per-id request for each selected row. Unlike archiveMany, a
   * single failure does not hide the rest: the successes still land, the list
   * reloads, and the error names how many failed.
   */
  async function runForEach(ids: number[], request: (token: string, id: number) => Promise<void>, doneLabel: string, actionLabel: string) {
    if (!token || ids.length === 0) {
      return;
    }
    const results = await Promise.allSettled(ids.map((id) => request(token, id)));
    const failures = results.filter((result): result is PromiseRejectedResult => result.status === 'rejected');
    const succeeded = ids.length - failures.length;
    await reload();
    if (succeeded > 0) {
      showToast(`${doneLabel} ${succeeded} item${succeeded === 1 ? '' : 's'}.`);
    }
    if (failures.length > 0) {
      const reason = failures[0].reason instanceof Error ? ` ${failures[0].reason.message}` : '';
      setError(`Unable to ${actionLabel} ${failures.length} of ${ids.length} ${entityLabel}.${reason}`);
    }
  }

  async function restoreMany(ids: number[]) {
    if (restore) {
      await runForEach(ids, restore, 'Restored', 'restore');
    }
  }

  async function destroyMany(ids: number[]) {
    if (permanentlyDelete) {
      await runForEach(ids, permanentlyDelete, 'Permanently deleted', 'permanently delete');
    }
  }

  async function destroy(id: number) {
    if (!token || !permanentlyDelete) {
      return;
    }
    try {
      await permanentlyDelete(token, id);
      await reload();
      showToast(deleteMessage);
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : `Unable to delete ${entityLabel}.`);
    }
  }

  function startEdit(id: number) {
    setEditingId(id);
  }

  function cancelEdit() {
    setEditingId(null);
  }

  return {
    items,
    loading,
    saving,
    error,
    setError,
    editingId,
    showArchived,
    toggleShowArchived,
    reload,
    save,
    archive: remove,
    archiveMany: removeMany,
    restore: restoreItem,
    restoreMany,
    permanentlyDelete: destroy,
    permanentlyDeleteMany: destroyMany,
    startEdit,
    cancelEdit,
  };
}
