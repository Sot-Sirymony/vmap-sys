import { Link } from 'react-router-dom';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogActions from '@mui/material/DialogActions';
import MuiButton from '@mui/material/Button';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Typography from '@mui/material/Typography';
import { Button } from './Button';
import { RelativeDate } from './RelativeDate';
import type { TaskItem } from '../../types/vision';

type EnergyOvercommitDialogProps = {
  open: boolean;
  weekStart: string;
  weekEnd: string;
  /** The week's other draining tasks — candidates to drop or move. */
  candidates: TaskItem[];
  onClose: () => void;
};

function formatDay(dateIso: string): string {
  if (!dateIso) {
    return '';
  }
  return new Date(`${dateIso}T00:00:00`).toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

/**
 * FR-34.4 / FR-34.5: coaching, not validation. Shown after a DRAIN task is
 * saved into a week that now leans heavily draining, naming the other draining
 * tasks as candidates to drop or move. The task is already saved — this never
 * blocks it; "Got it" just dismisses.
 */
export function EnergyOvercommitDialog({ open, weekStart, weekEnd, candidates, onClose }: EnergyOvercommitDialogProps) {
  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>This week is filling up with draining work</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: candidates.length ? 1.5 : 0 }}>
          The week of {formatDay(weekStart)}–{formatDay(weekEnd)} now has noticeably more draining work than
          charging work. Nothing is blocked — but what could you drop or move to make room?
        </DialogContentText>
        {candidates.length > 0 && (
          <>
            <Typography variant="overline" sx={{ color: 'text.secondary', fontWeight: 600 }}>
              Other draining tasks this week
            </Typography>
            <List dense disablePadding>
              {candidates.map((task) => (
                <ListItem key={task.id} disableGutters>
                  <ListItemText
                    primary={task.title}
                    secondary={<>Due <RelativeDate date={task.dueDate} /></>}
                  />
                </ListItem>
              ))}
            </List>
          </>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <MuiButton
          variant="contained"
          color="primary"
          size="small"
          component={Link}
          to={`/tasks?dueFrom=${weekStart}&dueTo=${weekEnd}`}
          onClick={onClose}
        >
          Review this week
        </MuiButton>
        <Button variant="secondary" onClick={onClose}>Got it</Button>
      </DialogActions>
    </Dialog>
  );
}
