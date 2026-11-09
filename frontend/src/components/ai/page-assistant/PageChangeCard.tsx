import { useState } from 'react';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { tr } from '../../../i18n/translate';
import type { PageChange } from '../../../types/vision';
import { Button } from '../../common/Button';
import { FIELD_CHOICES, SHORT_FIELDS } from './pageHelp';

type Props = {
  change: PageChange;
  label: string;
  /** Fills the form field; nothing is saved until the page's own Save. */
  onApply: (value: string, edited: boolean) => void;
  onDismiss: () => void;
};

const CHOICE_LABELS: Record<string, string> = {
  KNOWLEDGE: 'Knowledge', SKILL: 'Skill', TIME: 'Time', MONEY: 'Money', MOTIVATION: 'Motivation', PARTNER: 'Partner',
  SYSTEM: 'System', DECISION: 'Decision', OTHER: 'Other', LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High', CRITICAL: 'Critical',
  DRAFT: 'Draft', SENT: 'Sent', FOLLOWED_UP: 'Followed Up', REPLIED: 'Replied', CLOSED: 'Closed',
};

/**
 * V15 (AH-FR-06): one proposed change to one form field. The participant can
 * edit the value first. Apply fills the form on screen; Dismiss drops it.
 */
export function PageChangeCard({ change, label, onApply, onDismiss }: Readonly<Props>) {
  const [value, setValue] = useState(change.value);
  const choices = FIELD_CHOICES[change.field] ?? (/^row\d\./.test(change.field) && /^[1-5]$/.test(change.value) ? ['1', '2', '3', '4', '5'] : null);
  const edited = value !== change.value;

  return (
    <Card variant="outlined" component="section" aria-label={tr('Suggestion for {field}', { field: label })}>
      <CardContent sx={{ display: 'grid', gap: 1 }}>
        <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
          <Chip size="small" label={tr('AI suggestion')} />
          <Typography variant="subtitle2" component="h4">{label}</Typography>
        </Box>
        {choices ? (
          <TextField select size="small" label={label} value={value} onChange={(event) => setValue(event.target.value)}>
            {choices.map((choice) => <MenuItem key={choice} value={choice}>{tr(CHOICE_LABELS[choice] ?? choice)}</MenuItem>)}
          </TextField>
        ) : (
          <TextField
            size="small"
            label={label}
            multiline={!SHORT_FIELDS.has(change.field)}
            minRows={SHORT_FIELDS.has(change.field) ? undefined : 2}
            value={value}
            onChange={(event) => setValue(event.target.value)}
            // AH-NFR-10: 16 px text so phones do not zoom into the box.
            slotProps={{ htmlInput: { style: { fontSize: 16 } } }}
          />
        )}
        {change.reason && <Typography variant="caption" color="text.secondary">{change.reason}</Typography>}
        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
          <Button disabled={!value.trim()} onClick={() => onApply(value, edited)}>{tr('Apply to the form')}</Button>
          <Button variant="secondary" onClick={onDismiss}>{tr('Dismiss')}</Button>
        </Box>
      </CardContent>
    </Card>
  );
}
