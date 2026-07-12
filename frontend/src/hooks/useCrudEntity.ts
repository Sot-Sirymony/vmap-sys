import { useState } from 'react';
import { useToast } from '../context/ToastContext';

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
    deleteMessage = 'Permanently deleted.',
  } = config;
  const { showToast } = useToast();
  const [items, setItems] = useState<T[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [editingId, setEditingId] = useState<number | null>(null);
  const [showArchived, setShowArchived] = useState(false);

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

  async function remove(id: number) {
    if (!token) {
      return;
    }
    try {
      await archive(token, id);
      await reload();
      showToast(archiveMessage);
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
    restore: restoreItem,
    permanentlyDelete: destroy,
    startEdit,
    cancelEdit,
  };
}
