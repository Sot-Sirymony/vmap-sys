import { useState } from 'react';
import { Button } from './Button';
import { ConfirmDialog } from './ConfirmDialog';

type BulkArchiveActionProps = {
  selectedIds: Set<number>;
  /** The rows currently loaded, used to tell which selected rows are archived. */
  items: { id: number; archived: boolean }[];
  /** Plural entity name for the confirmation text, e.g. "goals". */
  entityLabel: string;
  onArchive: (ids: number[]) => Promise<void>;
  onRestore?: (ids: number[]) => Promise<void>;
  onDeletePermanently?: (ids: number[]) => Promise<void>;
};

type PendingAction = 'archive' | 'restore' | 'delete';

/**
 * Archive-lifecycle buttons for a DataTable selection toolbar. Active rows in
 * the selection can be archived (a soft delete); archived rows (visible with
 * "Show archived") can be restored or permanently deleted. Each button acts
 * only on the part of the selection it applies to, so a mixed selection is safe.
 */
export function BulkArchiveAction({ selectedIds, items, entityLabel, onArchive, onRestore, onDeletePermanently }: BulkArchiveActionProps) {
  const [confirming, setConfirming] = useState<PendingAction | null>(null);
  const [running, setRunning] = useState<PendingAction | null>(null);

  const selectedRows = items.filter((item) => selectedIds.has(item.id));
  const activeIds = selectedRows.filter((item) => !item.archived).map((item) => item.id);
  const archivedIds = selectedRows.filter((item) => item.archived).map((item) => item.id);

  async function handleConfirm(action: PendingAction) {
    setConfirming(null);
    setRunning(action);
    try {
      if (action === 'archive') {
        await onArchive(activeIds);
      } else if (action === 'restore') {
        await onRestore?.(archivedIds);
      } else {
        await onDeletePermanently?.(archivedIds);
      }
    } finally {
      setRunning(null);
    }
  }

  const dialogs: Record<PendingAction, { title: string; message: string; confirmLabel: string; danger: boolean }> = {
    archive: {
      title: 'Archive selected',
      message: `Archive ${activeIds.length} ${entityLabel}? They can be restored later with "Show archived".`,
      confirmLabel: 'Archive',
      danger: true,
    },
    restore: {
      title: 'Restore selected',
      message: `Restore ${archivedIds.length} archived ${entityLabel}?`,
      confirmLabel: 'Restore',
      danger: false,
    },
    delete: {
      title: 'Delete permanently',
      message: `Permanently delete ${archivedIds.length} archived ${entityLabel} and everything under them? This cannot be undone.`,
      confirmLabel: 'Delete permanently',
      danger: true,
    },
  };

  return (
    <>
      {activeIds.length > 0 && (
        <Button type="button" variant="danger" disabled={running !== null} onClick={() => setConfirming('archive')}>
          {running === 'archive' ? 'Archiving...' : `Archive selected (${activeIds.length})`}
        </Button>
      )}
      {onRestore && archivedIds.length > 0 && (
        <Button type="button" variant="secondary" disabled={running !== null} onClick={() => setConfirming('restore')}>
          {running === 'restore' ? 'Restoring...' : `Restore selected (${archivedIds.length})`}
        </Button>
      )}
      {onDeletePermanently && archivedIds.length > 0 && (
        <Button type="button" variant="danger" disabled={running !== null} onClick={() => setConfirming('delete')}>
          {running === 'delete' ? 'Deleting...' : `Delete permanently (${archivedIds.length})`}
        </Button>
      )}
      {confirming && (
        <ConfirmDialog
          {...dialogs[confirming]}
          onConfirm={() => void handleConfirm(confirming)}
          onClose={() => setConfirming(null)}
        />
      )}
    </>
  );
}
