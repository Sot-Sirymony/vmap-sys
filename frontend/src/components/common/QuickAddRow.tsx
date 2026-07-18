import { FormEvent, useState } from 'react';
import Card from '@mui/material/Card';
import FormControl from '@mui/material/FormControl';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import { Button } from './Button';
import { Input } from './Input';

type QuickAddRowProps = {
  /** Label for the parent select ("Dream", "Goal", "Step"). */
  parentLabel: string;
  parents: { value: string; label: string }[];
  parentValue: string;
  onParentChange: (value: string) => void;
  placeholder: string;
  /** Tasks require a due date (business rule 5) — asked inline, not defaulted. */
  withDueDate?: boolean;
  onAdd: (title: string, dueDate?: string) => Promise<void>;
};

/**
 * FR-22.1 list-page quick-add: pick the parent once, then type-and-Enter to
 * batch-create records with sensible defaults (priority Medium, status Not
 * Started). Everything else stays editable through the full form. Fields
 * that defaults cannot supply (a task's due date) are asked inline so
 * quick-add never bypasses validation (BR-16).
 */
export function QuickAddRow({ parentLabel, parents, parentValue, onParentChange, placeholder, withDueDate = false, onAdd }: QuickAddRowProps) {
  const [title, setTitle] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [busy, setBusy] = useState(false);

  const ready = Boolean(title.trim() && parentValue && (!withDueDate || dueDate));

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!ready) {
      return;
    }
    setBusy(true);
    try {
      await onAdd(title.trim(), withDueDate ? dueDate : undefined);
      setTitle('');
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card className="quick-add-row" component="form" onSubmit={(event: FormEvent) => void handleSubmit(event)}>
      <label>
        {parentLabel}
        <FormControl fullWidth size="small">
          <Select displayEmpty value={parentValue} onChange={(event) => onParentChange(event.target.value)}>
            <MenuItem value="" disabled><em>Select…</em></MenuItem>
            {parents.map((parent) => <MenuItem value={parent.value} key={parent.value}>{parent.label}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      <label className="quick-add-title">
        Quick add
        <Input value={title} onChange={(event) => setTitle(event.target.value)} placeholder={placeholder} />
      </label>
      {withDueDate && (
        <label>
          Due date
          <Input type="date" value={dueDate} onChange={(event) => setDueDate(event.target.value)} />
        </label>
      )}
      <Button type="submit" variant="secondary" disabled={busy || !ready}>
        {busy ? 'Adding...' : 'Add'}
      </Button>
    </Card>
  );
}
