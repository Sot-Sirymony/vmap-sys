import { useEffect, useRef, type ReactNode } from 'react';
import { X } from 'lucide-react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import IconButton from '@mui/material/IconButton';

type ModalProps = {
  title: string;
  children: ReactNode;
  onClose: () => void;
};

export function Modal({ title, children, onClose }: ModalProps) {
  const contentRef = useRef<HTMLDivElement>(null);

  // FR-22.3: MUI's focus trap focuses the dialog paper on open, overriding
  // React's autoFocus. Once the trap has settled, hand focus to the field
  // marked data-autofocus (set by Input/Textarea autoFocus) so typing can
  // start immediately.
  useEffect(() => {
    const timer = setTimeout(() => {
      contentRef.current?.querySelector<HTMLElement>('[data-autofocus]')?.focus();
    }, 100);
    return () => clearTimeout(timer);
  }, []);

  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2 }}>
        {title}
        <IconButton aria-label="Close" onClick={onClose} size="small">
          <X size={18} />
        </IconButton>
      </DialogTitle>
      <DialogContent ref={contentRef}>{children}</DialogContent>
    </Dialog>
  );
}
