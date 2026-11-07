import { useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { tr } from '../../../i18n/translate';
import type { AssistantChange } from '../../../types/vision';
import { Button } from '../../common/Button';
import { changeLabel } from './sectionHelp';

const OBSTACLE_TYPES = ['KNOWLEDGE', 'SKILL', 'TIME', 'MONEY', 'MOTIVATION', 'PARTNER', 'SYSTEM', 'DECISION', 'OTHER'];
const SEVERITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const TYPE_LABELS: Record<string, string> = {
  KNOWLEDGE: 'Knowledge', SKILL: 'Skill', TIME: 'Time', MONEY: 'Money', MOTIVATION: 'Motivation', PARTNER: 'Partner',
  SYSTEM: 'System', DECISION: 'Decision', OTHER: 'Other', LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High', CRITICAL: 'Critical',
};

type Props = {
  change: AssistantChange;
  applyLabel: string;
  onApply: (change: AssistantChange, edited: boolean) => Promise<void>;
  onDismiss: () => void;
};

/**
 * V11 Phase 7 (PB-FR-46): one proposed change, shown as a small form the
 * participant can edit. Apply does what the page says (change the draft, or
 * save to the plan); Dismiss drops it. Nothing happens without a choice.
 */
export function ChangeCard({ change, applyLabel, onApply, onDismiss }: Props) {
  const [value, setValue] = useState<AssistantChange>(change);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const edited = JSON.stringify(value) !== JSON.stringify(change);
  const label = tr(changeLabel(change.kind, change.field));
  const set = (patch: Partial<AssistantChange>) => setValue((current) => ({ ...current, ...patch }));

  async function handleApply() {
    setBusy(true);
    setError('');
    try {
      await onApply(value, edited);
    } catch (applyError) {
      setError(applyError instanceof Error ? tr(applyError.message) : tr('Unable to save this.'));
      setBusy(false);
    }
  }

  const missing = change.kind === 'EDIT' ? !value.value?.trim()
    : !value.title?.trim() || ((change.kind === 'ADD_STEP' || change.kind === 'ADD_TASK') && !value.date);

  return (
    <Card variant="outlined" component="section" aria-label={label}>
      <CardContent sx={{ display: 'grid', gap: 1 }}>
        <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
          <Chip size="small" label={tr('AI suggestion')} />
          <Typography variant="subtitle2" component="h4">{label}</Typography>
        </Box>
        {change.itemTitle && (
          <Typography variant="caption" color="text.secondary">{tr('For: {title}', { title: change.itemTitle })}</Typography>
        )}
        {change.parentTitle && (
          <Typography variant="caption" color="text.secondary">{tr('Under: {title}', { title: change.parentTitle })}</Typography>
        )}
        {change.kind === 'EDIT' && (
          <TextField
            size="small"
            label={label}
            type={change.field === 'DATE' ? 'date' : 'text'}
            multiline={change.field === 'SUCCESS' || change.field === 'WHY'}
            value={value.value ?? ''}
            onChange={(event) => set({ value: event.target.value })}
            slotProps={{ inputLabel: { shrink: true } }}
          />
        )}
        {change.kind !== 'EDIT' && (
          <Stack spacing={1}>
            <TextField size="small" label={change.kind === 'ADD_RISK' ? tr('What could get in the way') : tr('Title')}
              value={value.title ?? ''} onChange={(event) => set({ title: event.target.value })} />
            {(change.kind === 'ADD_STEP' || change.kind === 'ADD_TASK') && (
              <>
                <TextField size="small" label={tr('Owner')} value={value.owner ?? ''} onChange={(event) => set({ owner: event.target.value })} />
                <TextField size="small" type="date" label={tr('Due date')} value={value.date ?? ''}
                  onChange={(event) => set({ date: event.target.value || null })} slotProps={{ inputLabel: { shrink: true } }}
                  helperText={value.date ? undefined : tr('Pick a date')} />
              </>
            )}
            {change.kind === 'ADD_RISK' && (
              <>
                <TextField select size="small" label={tr('Type')} value={value.obstacleType ?? 'OTHER'}
                  onChange={(event) => set({ obstacleType: event.target.value })}>
                  {OBSTACLE_TYPES.map((item) => <MenuItem key={item} value={item}>{tr(TYPE_LABELS[item])}</MenuItem>)}
                </TextField>
                <TextField select size="small" label={tr('Severity')} value={value.severity ?? 'MEDIUM'}
                  onChange={(event) => set({ severity: event.target.value })}>
                  {SEVERITIES.map((item) => <MenuItem key={item} value={item}>{tr(TYPE_LABELS[item])}</MenuItem>)}
                </TextField>
                <TextField size="small" multiline label={tr('Solution')} value={value.solution ?? ''}
                  onChange={(event) => set({ solution: event.target.value })} />
              </>
            )}
          </Stack>
        )}
        {change.reason && <Typography variant="caption" color="text.secondary">{change.reason}</Typography>}
        {error && <Alert severity="error">{error}</Alert>}
        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
          <Button disabled={busy || missing} onClick={() => void handleApply()}>{applyLabel}</Button>
          <Button variant="secondary" disabled={busy} onClick={onDismiss}>{tr('Dismiss')}</Button>
        </Box>
      </CardContent>
    </Card>
  );
}
