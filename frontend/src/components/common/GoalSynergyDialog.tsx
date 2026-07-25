import { FormEvent, useEffect, useState } from 'react';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import FormControl from '@mui/material/FormControl';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { Trash2 } from 'lucide-react';
import { createGoalSynergyLink, deleteGoalSynergyLink, listGoalSynergyLinks } from '../../api/goalSynergyLinkApi';
import type { Goal, GoalSynergyLink } from '../../types/vision';
import { Button } from './Button';
import { EmptyState } from './EmptyState';
import { Textarea } from './Textarea';

type GoalSynergyDialogProps = {
  goal: Goal;
  /** All the user's goals, for the link picker. */
  goals: Goal[];
  token: string;
  onClose: () => void;
};

/**
 * FR-35.2: the cross-pollination view for one goal — its synergy links (each
 * marked when it crosses Vision Areas), plus a form to add a link to another
 * goal and remove existing ones. Informational only: it never changes a goal.
 */
export function GoalSynergyDialog({ goal, goals, token, onClose }: GoalSynergyDialogProps) {
  const [links, setLinks] = useState<GoalSynergyLink[]>([]);
  const [relatedGoalId, setRelatedGoalId] = useState('');
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    listGoalSynergyLinks(token, goal.id)
      .then((data) => active && setLinks(data))
      .catch(() => active && setLinks([]));
    return () => {
      active = false;
    };
  }, [token, goal.id]);

  function reload() {
    listGoalSynergyLinks(token, goal.id).then(setLinks).catch(() => setLinks([]));
  }

  const linkedIds = new Set(links.map((link) => link.relatedGoalId));
  // Can't link a goal to itself, to an archived goal, or to one already linked.
  const options = goals.filter((candidate) => candidate.id !== goal.id && !candidate.archived && !linkedIds.has(candidate.id));

  async function handleAdd(event: FormEvent) {
    event.preventDefault();
    if (!relatedGoalId) {
      return;
    }
    setBusy(true);
    setError('');
    try {
      await createGoalSynergyLink(token, goal.id, { relatedGoalId: Number(relatedGoalId), note: note.trim() || undefined });
      setRelatedGoalId('');
      setNote('');
      reload();
    } catch (addError) {
      setError(addError instanceof Error ? addError.message : 'Unable to link goals.');
    } finally {
      setBusy(false);
    }
  }

  async function handleRemove(linkId: number) {
    try {
      await deleteGoalSynergyLink(token, linkId);
    } finally {
      reload();
    }
  }

  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Synergy links — {goal.title}</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          Record how this goal reinforces another — the same work can move more than one Vision Area forward.
        </DialogContentText>

        {links.length === 0 ? (
          <EmptyState>No synergy links yet.</EmptyState>
        ) : (
          <Stack spacing={1.25} sx={{ mb: 2 }}>
            {links.map((link) => (
              <Stack key={link.id} direction="row" sx={{ alignItems: 'flex-start', gap: 1 }}>
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <Stack direction="row" sx={{ alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>{link.relatedGoalTitle}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      {link.relatedGoalCode} · {link.relatedGoalVisionAreaName}
                    </Typography>
                    {link.crossVisionArea && (
                      <Chip size="small" variant="outlined" color="primary" label="Cross-area" sx={{ height: 20, fontWeight: 700 }} />
                    )}
                  </Stack>
                  {link.note && <Typography variant="caption" color="text.secondary">{link.note}</Typography>}
                </Box>
                <IconButton size="small" aria-label={`Remove link to ${link.relatedGoalTitle}`} onClick={() => void handleRemove(link.id)}>
                  <Trash2 size={16} />
                </IconButton>
              </Stack>
            ))}
          </Stack>
        )}

        <form onSubmit={(event) => void handleAdd(event)}>
          <Stack spacing={1}>
            <FormControl size="small" fullWidth>
              <Select
                SelectDisplayProps={{ 'aria-label': 'Link to another goal' }}
                displayEmpty
                value={relatedGoalId}
                onChange={(event) => setRelatedGoalId(event.target.value)}
              >
                <MenuItem value="" disabled><em>Link to another goal…</em></MenuItem>
                {options.map((candidate) => (
                  <MenuItem value={String(candidate.id)} key={candidate.id}>{candidate.title}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <Textarea
              value={note}
              onChange={(event) => setNote(event.target.value)}
              placeholder="How do they reinforce each other? (optional)"
              aria-label="Synergy note"
            />
            {error && <Typography variant="caption" color="error">{error}</Typography>}
            <Box>
              <Button type="submit" variant="secondary" disabled={busy || !relatedGoalId}>
                {busy ? 'Adding…' : 'Add link'}
              </Button>
            </Box>
          </Stack>
        </form>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button variant="secondary" onClick={onClose}>Done</Button>
      </DialogActions>
    </Dialog>
  );
}
