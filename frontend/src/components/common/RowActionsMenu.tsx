import { useState, type MouseEvent } from 'react';
import { MoreVertical } from 'lucide-react';
import IconButton from '@mui/material/IconButton';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import { ConfirmDialog } from './ConfirmDialog';

const DEFAULT_CONFIRM_MESSAGE = 'Archive this record? You can bring it back later with "Show archived".';
const DEFAULT_DELETE_MESSAGE = 'Permanently delete this record and everything under it? This cannot be undone.';

type RowActionsMenuProps = {
  onEdit: () => void;
  onArchive: () => void;
  /** When set and `archived` is true, the menu offers Restore instead of Edit/Archive. */
  onRestore?: () => void;
  /**
   * When set and `archived` is true, the menu also offers "Delete permanently",
   * gated behind a strong confirmation. Only archived records can be deleted.
   */
  onDeletePermanently?: () => void;
  archived?: boolean;
  /**
   * Confirmation text shown before archiving. A function may fetch it lazily
   * (e.g. cascade counts from the archive-impact endpoint). Falls back to a
   * generic message.
   */
  confirmArchive?: string | (() => Promise<string>);
  /** Confirmation text shown before permanently deleting. Falls back to a generic warning. */
  confirmDelete?: string;
  /**
   * Extra menu items shown (for a live record) above Edit — e.g. "Add goal" on a
   * dream, which jumps to creating its child. Hidden once archived, where the
   * only sensible actions are restore/delete.
   */
  extraActions?: { label: string; onClick: () => void }[];
  label?: string;
};

export function RowActionsMenu({ onEdit, onArchive, onRestore, onDeletePermanently, archived = false, confirmArchive, confirmDelete, extraActions = [], label = 'Row actions' }: RowActionsMenuProps) {
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const [confirmMessage, setConfirmMessage] = useState<string | null>(null);
  const [deleteMessage, setDeleteMessage] = useState<string | null>(null);

  function handleOpen(event: MouseEvent<HTMLElement>) {
    event.stopPropagation();
    setAnchorEl(event.currentTarget);
  }

  function handleClose() {
    setAnchorEl(null);
  }

  async function handleArchiveClick() {
    handleClose();
    if (typeof confirmArchive === 'function') {
      // Show the dialog immediately with the fallback text, then swap in the
      // fetched message so a slow impact call never blocks the dialog.
      setConfirmMessage(DEFAULT_CONFIRM_MESSAGE);
      try {
        setConfirmMessage(await confirmArchive());
      } catch {
        // Keep the fallback message if the impact fetch fails.
      }
    } else {
      setConfirmMessage(confirmArchive ?? DEFAULT_CONFIRM_MESSAGE);
    }
  }

  return (
    <>
      <IconButton size="small" onClick={handleOpen} aria-label={label}>
        <MoreVertical size={16} />
      </IconButton>
      <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={handleClose}>
        {archived ? ([
          onRestore && <MenuItem key="restore" onClick={() => { handleClose(); onRestore(); }}>Restore</MenuItem>,
          onDeletePermanently && (
            <MenuItem
              key="delete"
              onClick={() => { handleClose(); setDeleteMessage(confirmDelete ?? DEFAULT_DELETE_MESSAGE); }}
              sx={{ color: 'error.main' }}
            >
              Delete permanently
            </MenuItem>
          ),
        ]) : ([
          ...extraActions.map((action) => (
            <MenuItem key={action.label} onClick={() => { handleClose(); action.onClick(); }}>{action.label}</MenuItem>
          )),
          <MenuItem key="edit" onClick={() => { handleClose(); onEdit(); }}>Edit</MenuItem>,
          <MenuItem key="archive" onClick={() => void handleArchiveClick()}>Archive</MenuItem>,
        ])}
      </Menu>
      {confirmMessage !== null && (
        <ConfirmDialog
          title="Confirm archive"
          message={confirmMessage}
          confirmLabel="Archive"
          onConfirm={() => { setConfirmMessage(null); onArchive(); }}
          onClose={() => setConfirmMessage(null)}
        />
      )}
      {deleteMessage !== null && onDeletePermanently && (
        <ConfirmDialog
          title="Delete permanently"
          message={deleteMessage}
          confirmLabel="Delete permanently"
          danger
          onConfirm={() => { setDeleteMessage(null); onDeletePermanently(); }}
          onClose={() => setDeleteMessage(null)}
        />
      )}
    </>
  );
}
