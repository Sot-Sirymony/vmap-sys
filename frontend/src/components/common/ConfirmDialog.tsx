import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogActions from '@mui/material/DialogActions';
import { Button } from './Button';

type ConfirmDialogProps = {
  title: string;
  message: string;
  confirmLabel: string;
  onConfirm: () => void;
  onClose: () => void;
  /** Style the confirm button as a destructive (red) action. */
  danger?: boolean;
};

export function ConfirmDialog({ title, message, confirmLabel, onConfirm, onClose, danger = false }: ConfirmDialogProps) {
  return (
    <Dialog open onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <DialogContentText>{message}</DialogContentText>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button type="button" variant={danger ? 'danger' : 'primary'} onClick={onConfirm}>{confirmLabel}</Button>
        <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
      </DialogActions>
    </Dialog>
  );
}
