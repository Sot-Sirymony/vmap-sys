import { useState } from 'react';
import { Button } from './Button';
import { ConfirmDialog } from './ConfirmDialog';

type BulkArchiveActionProps = {
  selectedIds: Set<number>;
  /** Plural entity name for the confirmation text, e.g. "goals". */
  entityLabel: string;
  onArchive: (ids: number[]) => Promise<void>;
};

/** "Archive selected" for a DataTable selection toolbar. Archiving is a soft delete: rows come back with "Show archived". */
export function BulkArchiveAction({ selectedIds, entityLabel, onArchive }: BulkArchiveActionProps) {
  const [confirming, setConfirming] = useState(false);
  const [archiving, setArchiving] = useState(false);
  const count = selectedIds.size;

  async function handleConfirm() {
    setConfirming(false);
    setArchiving(true);
    try {
      await onArchive([...selectedIds]);
    } finally {
      setArchiving(false);
    }
  }

  return (
    <>
      <Button type="button" variant="danger" disabled={archiving} onClick={() => setConfirming(true)}>
        {archiving ? 'Archiving...' : 'Archive selected'}
      </Button>
      {confirming && (
        <ConfirmDialog
          title="Archive selected"
          message={`Archive ${count} ${entityLabel}? They can be restored later with "Show archived".`}
          confirmLabel="Archive"
          danger
          onConfirm={() => void handleConfirm()}
          onClose={() => setConfirming(false)}
        />
      )}
    </>
  );
}
