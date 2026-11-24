import { useState } from 'react';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import TextField from '@mui/material/TextField';
import { tr } from '../../i18n/translate';
import { Button } from './Button';

export const ARCHIVE_REASON_MAX = 1000;

type ArchiveConfirmDialogProps = {
  title?: string;
  message: string;
  confirmLabel?: string;
  /** Called with the trimmed reason; an empty box gives ''. */
  onConfirm: (reason: string) => void;
  onClose: () => void;
};

/**
 * V29 (AR-FR-01 to AR-FR-03, D-29-1): the archive confirmation for dreams,
 * goals, steps and tasks, with an optional reason saved on the archived item.
 * The confirm button never waits for a reason.
 */
export function ArchiveConfirmDialog({ title, message, confirmLabel, onConfirm, onClose }: ArchiveConfirmDialogProps) {
  const [reason, setReason] = useState('');
  return (
    <Dialog open onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{title ?? tr('Confirm archive')}</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>{message}</DialogContentText>
        <TextField
          fullWidth
          multiline
          minRows={2}
          size="small"
          label={tr('Why are you archiving this? (optional)')}
          value={reason}
          onChange={(event) => setReason(event.target.value.slice(0, ARCHIVE_REASON_MAX))}
          helperText={tr('Saved with the archived item. Restoring clears it.')}
        />
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button type="button" variant="danger" onClick={() => onConfirm(reason.trim())}>{confirmLabel ?? tr('Archive')}</Button>
        <Button type="button" variant="secondary" onClick={onClose}>{tr('Cancel')}</Button>
      </DialogActions>
    </Dialog>
  );
}
