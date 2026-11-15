import { useEffect, useState, type FormEvent } from 'react';
import Checkbox from '@mui/material/Checkbox';
import FormControlLabel from '@mui/material/FormControlLabel';
import Radio from '@mui/material/Radio';
import RadioGroup from '@mui/material/RadioGroup';
import { getAiStatus } from '../../api/aiApi';
import { suggestSmallerTasks } from '../../api/aiPlanApi';
import { splitTask } from '../../api/taskApi';
import { useI18n } from '../../i18n/I18nContext';
import { Button } from '../common/Button';
import { ErrorMessage } from '../common/ErrorMessage';
import { Input } from '../common/Input';
import { Modal } from '../common/Modal';
import type { TaskItem, TaskSplitOutcome, TaskSplitResult } from '../../types/vision';
import { MAX_SPLIT_ROWS, MIN_SPLIT_ROWS, spreadDueDates } from '../../utils/taskBreakdown';
import { tr } from '../../i18n/translate';

type Row = { title: string; dueDate: string; startable: boolean };

function emptyRows(task: TaskItem, count: number): Row[] {
  return spreadDueDates(count, task.dueDate).map((dueDate) => ({ title: '', dueDate, startable: false }));
}

/**
 * V17 BL-FR-24 to BL-FR-26, BL-FR-29: breaks a stuck task into 2 to 5
 * smaller tasks under the same step. Due dates are spread between today and
 * the task's due date; owner and priority come from the original. With AI on,
 * "Suggest smaller tasks" fills the rows, which the participant edits before
 * anything is saved. The first-action tick box never blocks saving.
 */
export function BreakDownDialog({ token, task, onClose, onSaved }: {
  token: string;
  task: TaskItem;
  onClose: () => void;
  onSaved: (result: TaskSplitResult) => void;
}) {
  const { language } = useI18n();
  const [rows, setRows] = useState<Row[]>(() => emptyRows(task, 3));
  // Q-04 (draft): keep the original by default.
  const [outcome, setOutcome] = useState<TaskSplitOutcome>('KEEP');
  const [aiOn, setAiOn] = useState(false);
  const [suggesting, setSuggesting] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    getAiStatus(token).then((status) => setAiOn(status.available)).catch(() => setAiOn(false));
  }, [token]);

  function updateRow(index: number, change: Partial<Row>) {
    setRows((current) => current.map((row, position) => (position === index ? { ...row, ...change } : row)));
  }

  function addRow() {
    setRows((current) => [...current, { title: '', dueDate: task.dueDate, startable: false }]);
  }

  function removeRow(index: number) {
    setRows((current) => current.filter((_, position) => position !== index));
  }

  async function suggest() {
    setSuggesting(true);
    setError('');
    try {
      const suggestions = await suggestSmallerTasks(token, task.id, language);
      const picked = suggestions.tasks.slice(0, MAX_SPLIT_ROWS);
      if (picked.length < MIN_SPLIT_ROWS) {
        setError(tr('No suggestions this time. Write the smaller tasks yourself.'));
        return;
      }
      const dates = spreadDueDates(picked.length, task.dueDate);
      setRows(picked.map((suggestion, index) => ({
        title: suggestion.title,
        dueDate: suggestion.dueDate ?? dates[index],
        startable: false,
      })));
    } catch (suggestError) {
      setError(suggestError instanceof Error ? suggestError.message : tr('Unable to suggest smaller tasks.'));
    } finally {
      setSuggesting(false);
    }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError('');
    try {
      const result = await splitTask(token, task.id, {
        rows: rows.map((row) => ({ title: row.title.trim(), dueDate: row.dueDate })),
        originalOutcome: outcome,
      });
      onSaved(result);
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : tr('Unable to break this task down.'));
    } finally {
      setSaving(false);
    }
  }

  const noneStartable = rows.every((row) => !row.startable);

  return (
    <Modal title={tr('Break into smaller tasks')} onClose={onClose}>
      <form className="stack-list" onSubmit={(event) => void handleSubmit(event)}>
        <p className="field-hint">
          {tr('A stuck task is usually still too big. Split "{title}" until the next action is easy to start.', { title: task.title })}
        </p>
        {task.blockerReason && <p className="field-hint"><strong>{tr('What blocks it:')}</strong> {task.blockerReason}</p>}
        {aiOn && (
          <div className="row-actions">
            <Button type="button" variant="secondary" onClick={() => void suggest()} disabled={suggesting || saving}>
              {suggesting ? tr('Thinking...') : tr('Suggest smaller tasks')}
            </Button>
          </div>
        )}
        <ol className="break-down-rows">
          {rows.map((row, index) => (
            <li key={index} className="break-down-row">
              <label>
                {tr('Smaller task {number}', { number: index + 1 })}
                <Input value={row.title} maxLength={220} required onChange={(event) => updateRow(index, { title: event.target.value })} />
              </label>
              <label>
                {tr('Due Date')}
                <Input type="date" value={row.dueDate} required onChange={(event) => updateRow(index, { dueDate: event.target.value })} />
              </label>
              <label className="checkbox-field">
                <Checkbox checked={row.startable} onChange={(event) => updateRow(index, { startable: event.target.checked })} />
                {tr('Could you start this in the next 15 minutes?')}
              </label>
              {rows.length > MIN_SPLIT_ROWS && (
                <Button type="button" variant="secondary" onClick={() => removeRow(index)}>
                  {tr('Remove task {number}', { number: index + 1 })}
                </Button>
              )}
            </li>
          ))}
        </ol>
        {rows.length < MAX_SPLIT_ROWS && (
          <div className="row-actions">
            <Button type="button" variant="secondary" onClick={addRow}>{tr('Add another')}</Button>
          </div>
        )}
        {noneStartable && rows.some((row) => row.title.trim()) && (
          <p className="field-hint" role="status">
            {tr('None of these feels startable yet. Try making the first one smaller.')}
          </p>
        )}
        <fieldset className="break-down-outcome">
          <legend>{tr('And the original task?')}</legend>
          <RadioGroup value={outcome} onChange={(event) => setOutcome(event.target.value as TaskSplitOutcome)}>
            <FormControlLabel value="KEEP" control={<Radio />} label={tr('Keep it as a checklist parent')} />
            <FormControlLabel value="REPLACE" control={<Radio />} label={tr('Replace it (archived, can be restored)')} />
          </RadioGroup>
        </fieldset>
        {error && <ErrorMessage message={error} />}
        <div className="row-actions">
          <Button type="submit" disabled={saving}>{saving ? tr('Saving...') : tr('Save smaller tasks')}</Button>
          <Button type="button" variant="secondary" onClick={onClose} disabled={saving}>{tr('Cancel')}</Button>
        </div>
      </form>
    </Modal>
  );
}
