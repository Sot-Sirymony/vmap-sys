import { type ReactNode, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import MuiButton from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { Lock, LockOpen, RefreshCw, Trash2 } from 'lucide-react';
import { tr } from '../../i18n/translate';
import type { Priority, RefineAction } from '../../types/vision';
import { Button } from '../common/Button';

/** Questions to think through at each stage: the program's own prompts, so the guide works without AI (PB-FR-05). */
export function Prompts({ questions }: { questions: string[] }) {
  return (
    <Box component="ul" sx={{ m: 0, pl: 2.5, color: 'text.secondary' }}>
      {questions.map((question) => (
        <Typography component="li" variant="body2" key={question}>{tr(question)}</Typography>
      ))}
    </Box>
  );
}

/**
 * One new or existing item. Existing items are shown, never changed. An AI
 * suggestion can be locked so refining leaves it alone (PB-FR-20), or swapped
 * for another option (PB-FR-21).
 */
export function ItemCard({ title, fromAi, existing, edited, locked, onToggleLock, onAnother, busy, onRemove, children }: {
  title: string;
  fromAi?: boolean;
  existing?: boolean;
  edited?: boolean;
  locked?: boolean;
  onToggleLock?: () => void;
  onAnother?: () => void;
  busy?: boolean;
  onRemove?: () => void;
  children?: ReactNode;
}) {
  const name = title || tr('this item');
  return (
    <Box sx={{ display: 'grid', gap: 1, p: 1.5, border: 1, borderColor: 'divider', borderRadius: 2,
      bgcolor: existing ? 'action.hover' : 'background.paper' }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
        {existing && <Chip size="small" variant="outlined" label={tr('Already in your plan')} />}
        {fromAi && <Chip size="small" label={tr('AI suggestion')} />}
        {fromAi && edited && <Chip size="small" variant="outlined" label={tr('Edited')} />}
        {existing && <Typography variant="body2" sx={{ fontWeight: 600 }}>{title}</Typography>}
        <Box sx={{ ml: 'auto', display: 'flex', alignItems: 'center', gap: 0.5 }}>
          {onAnother && (
            <MuiButton size="small" disabled={busy} startIcon={<RefreshCw size={14} />} onClick={onAnother}>
              {tr('Another option')}
            </MuiButton>
          )}
          {onToggleLock && (
            <IconButton size="small" aria-pressed={!!locked} color={locked ? 'primary' : 'default'}
              aria-label={locked ? tr('Unlock {title}', { title: name }) : tr('Keep {title} when refining', { title: name })}
              onClick={onToggleLock}>
              {locked ? <Lock size={16} /> : <LockOpen size={16} />}
            </IconButton>
          )}
          {onRemove && (
            <IconButton size="small" aria-label={tr('Remove {title}', { title: name })} onClick={onRemove}>
              <Trash2 size={16} />
            </IconButton>
          )}
        </Box>
      </Box>
      {children}
    </Box>
  );
}

export function PrioritySelect({ value, onChange }: { value: Priority; onChange: (value: Priority) => void }) {
  return (
    <TextField select size="small" label={tr('Priority')} value={value} onChange={(event) => onChange(event.target.value as Priority)}>
      {(['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as Priority[]).map((priority) => (
        <MenuItem key={priority} value={priority}>{tr(priority.charAt(0) + priority.slice(1).toLowerCase())}</MenuItem>
      ))}
    </TextField>
  );
}

export function DateField({ label, value, onChange }: { label: string; value?: string | null; onChange: (value: string | null) => void }) {
  return (
    <TextField size="small" type="date" label={tr(label)} value={value ?? ''} onChange={(event) => onChange(event.target.value || null)}
      slotProps={{ inputLabel: { shrink: true } }} />
  );
}

/**
 * PB-FR-08, PB-FR-11: Claude's clarifying question, answered in place. The
 * answer is kept in the draft and used by the next suggestion; Suggest anyway
 * skips further questions.
 */
export function AnswerQuestion({ question, busy, onAnswer, onSuggestAnyway }: {
  question?: string | null;
  busy: boolean;
  onAnswer: (answer: string) => void;
  onSuggestAnyway: () => void;
}) {
  const [answer, setAnswer] = useState('');
  if (!question) {
    return null;
  }
  return (
    <Alert severity="info" icon={false}>
      <Stack spacing={1}>
        <Typography variant="body2" sx={{ fontWeight: 600 }}>{question}</Typography>
        <TextField size="small" label={tr('Your answer')} value={answer} multiline minRows={2}
          slotProps={{ htmlInput: { maxLength: 1000 } }} onChange={(event) => setAnswer(event.target.value)} />
        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
          <Button disabled={busy || !answer.trim()} onClick={() => onAnswer(answer.trim())}>{tr('Answer and suggest')}</Button>
          <Button variant="secondary" disabled={busy} onClick={onSuggestAnyway}>{tr('Suggest anyway')}</Button>
        </Box>
      </Stack>
    </Alert>
  );
}

const REFINE_CHOICES: { action: RefineAction; label: string }[] = [
  { action: 'FEWER', label: 'Fewer' },
  { action: 'MORE', label: 'More' },
  { action: 'SMALLER', label: 'Smaller' },
  { action: 'BOLDER', label: 'Bolder' },
  { action: 'EARLIER', label: 'Earlier' },
  { action: 'LATER', label: 'Later' },
];

/**
 * PB-FR-19, PB-FR-20: change the AI suggestions the participant has not
 * edited or locked. Edited and locked ones stay as they are.
 */
export function RefineBar({ replaceable, busy, onRefine }: {
  replaceable: number;
  busy: boolean;
  onRefine: (action: RefineAction, note: string) => void;
}) {
  const [action, setAction] = useState<RefineAction | null>(null);
  const [note, setNote] = useState('');
  if (replaceable === 0) {
    return null;
  }
  return (
    <Box sx={{ display: 'grid', gap: 1, p: 1.5, borderRadius: 2, bgcolor: 'action.hover' }}>
      <Typography variant="body2" sx={{ fontWeight: 600 }}>
        {tr('Change the {count} suggestion(s) you have not edited or locked', { count: replaceable })}
      </Typography>
      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }} role="group" aria-label={tr('How to change them')}>
        {REFINE_CHOICES.map((choice) => (
          <Chip key={choice.action} label={tr(choice.label)} clickable aria-pressed={action === choice.action}
            color={action === choice.action ? 'primary' : 'default'} onClick={() => setAction(choice.action)} />
        ))}
      </Box>
      <TextField size="small" label={tr('Anything else? (optional)')} value={note}
        slotProps={{ htmlInput: { maxLength: 300 } }} onChange={(event) => setNote(event.target.value)} />
      <Box>
        <Button disabled={busy || !action} onClick={() => action && onRefine(action, note.trim())}>{tr('Refine')}</Button>
      </Box>
    </Box>
  );
}

export const twoColumns = { display: 'grid', gap: 1, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } } as const;
