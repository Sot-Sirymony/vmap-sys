import { useId, useState, type ReactNode } from 'react';
import Box from '@mui/material/Box';
import useMediaQuery from '@mui/material/useMediaQuery';
import { ChevronRight } from 'lucide-react';

type MobileCollapseProps = {
  /** The button text on phones, e.g. "Show the progress trend". */
  label: string;
  children: ReactNode;
};

/**
 * BRD 25 §10 — on phones, secondary insight cards sit behind one tap so the
 * screen stays in decision order; from 600px up the children render as they
 * are and this component adds nothing.
 */
export function MobileCollapse({ label, children }: MobileCollapseProps) {
  const phone = useMediaQuery('(max-width: 599px)');
  const [open, setOpen] = useState(false);
  const regionId = useId();
  if (!phone) {
    return <>{children}</>;
  }
  return (
    <Box sx={{ display: 'grid', gap: 1.5 }}>
      <Box
        component="button"
        type="button"
        aria-expanded={open}
        aria-controls={regionId}
        onClick={() => setOpen((value) => !value)}
        sx={{
          display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%', minHeight: 48, px: 2,
          border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', bgcolor: 'var(--surface)', color: 'var(--text-primary)',
          font: 'inherit', fontWeight: 600, textAlign: 'left', cursor: 'pointer',
          '&:active': { transform: 'scale(0.98)' },
          transition: 'transform var(--motion-fast) var(--motion-ease)',
          '& svg': { transition: 'transform var(--motion-base) var(--motion-ease)', transform: open ? 'rotate(90deg)' : 'none' },
        }}
      >
        {label}
        <ChevronRight size={18} aria-hidden="true" />
      </Box>
      <Box id={regionId} hidden={!open}>{open && children}</Box>
    </Box>
  );
}
