import { useState, type FormEvent } from 'react';
import Alert from '@mui/material/Alert';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { markBlocked } from '../../api/blockApi';
import { tr } from '../../i18n/translate';
import type { MarkBlockedResult, ObstacleType, Severity } from '../../types/vision';
import { obstacleTypeLabels, severityLabels } from '../../utils/enumLabels';
import { suggestPartnerFor } from '../../utils/partnerSuggestion';
import { Button } from '../common/Button';
import { Modal } from '../common/Modal';

export type BlockTarget = {
  itemType: 'TASK' | 'STEP';
  itemId: number;
  title: string;
  obstacleType?: ObstacleType | null;
  reason?: string | null;
};

const TYPES = Object.keys(obstacleTypeLabels) as ObstacleType[];
const SEVERITIES = Object.keys(severityLabels) as Severity[];
const MAX_REASON = 1000;

/**
 * V28 (D-28-9, BA-FR-11, BA-FR-12): marks one task or step as blocked with
 * what is missing and why. The server saves the Blocked status through the
 * normal rules and records an obstacle on the item, reusing an open one.
 */
export function MarkBlockedDialog({ token, target, onClose, onSaved }: {
  token: string;
  target: BlockTarget;
  onClose: () => void;
  onSaved: (result: MarkBlockedResult) => void;
}) {
  const [obstacleType, setObstacleType] = useState<ObstacleType | ''>(target.obstacleType ?? '');
  const [reason, setReason] = useState(target.reason ?? '');
  const [severity, setSeverity] = useState<Severity>('MEDIUM');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const suggestion = obstacleType ? suggestPartnerFor(obstacleType) : null;
  const ready = obstacleType !== '' && reason.trim().length > 0;

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!ready) return;
    setSaving(true);
    setError('');
    try {
      onSaved(await markBlocked(token, {
        itemType: target.itemType, itemId: target.itemId, obstacleType, reason: reason.trim(), severity,
      }));
    } catch (saveError) {
      setError(saveError instanceof Error ? tr(saveError.message) : tr('Unable to mark as blocked.'));
      setSaving(false);
    }
  }

  return (
    <Modal title={tr('Mark as blocked')} onClose={onClose}>
      <form onSubmit={(event) => void submit(event)}>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Typography variant="body2" sx={{ fontWeight: 600 }}>
            {target.itemType === 'TASK' ? tr('Task') : tr('Step')}: {target.title}
          </Typography>
          <TextField select size="small" required label={tr('What is missing?')} value={obstacleType}
            onChange={(event) => setObstacleType(event.target.value as ObstacleType)}>
            {TYPES.map((type) => <MenuItem key={type} value={type}>{tr(obstacleTypeLabels[type])}</MenuItem>)}
          </TextField>
          {suggestion && (
            <Typography variant="body2" color="text.secondary">
              {tr('Who can help')}: {tr(suggestion.label)}
            </Typography>
          )}
          <TextField size="small" required multiline minRows={3} label={tr('Why is it blocked?')} value={reason}
            onChange={(event) => setReason(event.target.value.slice(0, MAX_REASON))}
            helperText={tr('Saved as the blocker reason and as an obstacle on this item.')} />
          <TextField select size="small" label={tr('Severity')} value={severity}
            onChange={(event) => setSeverity(event.target.value as Severity)}>
            {SEVERITIES.map((value) => <MenuItem key={value} value={value}>{tr(severityLabels[value])}</MenuItem>)}
          </TextField>
          {error && <Alert severity="error">{error}</Alert>}
          <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
            <Button variant="secondary" onClick={onClose} disabled={saving}>{tr('Cancel')}</Button>
            <Button type="submit" disabled={saving || !ready}>{tr('Mark as blocked')}</Button>
          </Stack>
        </Stack>
      </form>
    </Modal>
  );
}
