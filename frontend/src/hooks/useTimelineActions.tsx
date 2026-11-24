import { useState, type FormEvent, type ReactNode } from 'react';
import { archiveDream, getDreamArchiveImpact, restoreDream } from '../api/dreamApi';
import { archiveGoal, createGoal, getGoalArchiveImpact, restoreGoal } from '../api/goalApi';
import { archiveStep, createStep, getStepArchiveImpact, restoreStep } from '../api/stepApi';
import { archiveTask, createTask, restoreTask } from '../api/taskApi';
import { Button } from '../components/common/Button';
import { ConfirmDialog } from '../components/common/ConfirmDialog';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Input } from '../components/common/Input';
import { Modal } from '../components/common/Modal';
import { Select } from '../components/common/Select';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { tr } from '../i18n/translate';
import type { Priority, VisionStep } from '../types/vision';
import type { TimelineKind } from '../utils/timeline';

/** A timeline row the actions work on: enough to find the record and say its name. */
export type TimelineActionTarget = { kind: TimelineKind; id: number; dreamId: number; title: string };

const CHILD_KIND: Record<Exclude<TimelineKind, 'task'>, Exclude<TimelineKind, 'dream'>> = {
  dream: 'goal',
  goal: 'step',
  step: 'task',
};

const ADD_TITLE: Record<Exclude<TimelineKind, 'dream'>, string> = {
  goal: tr('Add a goal'),
  step: tr('Add a step'),
  task: tr('Add a task'),
};

const PRIORITIES: { value: Priority; label: string }[] = [
  { value: 'LOW', label: tr('Low') },
  { value: 'MEDIUM', label: tr('Medium') },
  { value: 'HIGH', label: tr('High') },
  { value: 'CRITICAL', label: tr('Critical') },
];

type Options = {
  token: string | null;
  /** The plan's steps, to number a new step after its siblings. */
  steps: VisionStep[];
  /** Reloads the plan after a change. */
  onChanged: () => Promise<void>;
  /** Called after a dream is archived, e.g. to leave a page that showed only that dream. */
  onDreamArchived?: (dreamId: number) => void;
};

type AddState = { parent: TimelineActionTarget; kind: Exclude<TimelineKind, 'dream'> };

/**
 * Add, archive and restore from the timeline, without leaving it. Editing
 * still opens the existing edit forms, so every rule lives in one place.
 * The server checks every change (business rules 1 to 6, BR-40, FR-64).
 */
export function useTimelineActions({ token, steps, onChanged, onDreamArchived }: Options) {
  const { user } = useAuth();
  const { showToast } = useToast();
  const [adding, setAdding] = useState<AddState | null>(null);
  const [title, setTitle] = useState('');
  const [owner, setOwner] = useState('');
  const [priority, setPriority] = useState<Priority>('MEDIUM');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [saving, setSaving] = useState(false);
  const [addError, setAddError] = useState('');
  const [archiving, setArchiving] = useState<{ target: TimelineActionTarget; message: string } | null>(null);
  const [actionError, setActionError] = useState('');

  function addChild(parent: TimelineActionTarget) {
    if (parent.kind === 'task') return;
    setAdding({ parent, kind: CHILD_KIND[parent.kind] });
    setTitle('');
    setOwner(user?.fullName ?? '');
    setPriority('MEDIUM');
    setStartDate('');
    setEndDate('');
    setAddError('');
  }

  async function submitAdd(event: FormEvent) {
    event.preventDefault();
    if (!adding || !token) return;
    const { parent, kind } = adding;
    setSaving(true);
    setAddError('');
    try {
      if (kind === 'goal') {
        await createGoal(token, {
          dreamId: parent.id, title: title.trim(), priority, status: 'NOT_STARTED',
          moonshot: false, scheduleMode: 'BOTTOM_UP', targetDate: endDate || undefined,
        });
      } else if (kind === 'step') {
        const nextSequence = steps.filter((step) => step.goalId === parent.id)
          .reduce((max, step) => Math.max(max, step.sequenceNumber), 0) + 1;
        await createStep(token, {
          goalId: parent.id, title: title.trim(), owner: owner.trim(), sequenceNumber: nextSequence,
          complex: false, priority, targetDate: endDate, status: 'NOT_STARTED',
        });
      } else {
        await createTask(token, {
          stepId: parent.id, title: title.trim(), owner: owner.trim(), priority,
          startDate: startDate || undefined, dueDate: endDate, status: 'NOT_STARTED', progressPercent: 0,
        });
      }
      setAdding(null);
      showToast(tr('Added "{title}".', { title: title.trim() }));
      await onChanged();
    } catch (saveError) {
      setAddError(saveError instanceof Error ? saveError.message : tr('Unable to add.'));
    } finally {
      setSaving(false);
    }
  }

  async function archiveMessage(target: TimelineActionTarget) {
    if (!token) return '';
    try {
      if (target.kind === 'dream') {
        const impact = await getDreamArchiveImpact(token, target.id);
        return tr('Archiving "{title}" also archives {goals} goal(s), {steps} step(s), and {tasks} task(s). Everything can be restored later with "Show archived".', { title: target.title, goals: impact.goals, steps: impact.steps, tasks: impact.tasks });
      }
      if (target.kind === 'goal') {
        const impact = await getGoalArchiveImpact(token, target.id);
        return tr('Archiving "{title}" also archives {steps} step(s) and {tasks} task(s). Everything can be restored later with "Show archived".', { title: target.title, steps: impact.steps, tasks: impact.tasks });
      }
      if (target.kind === 'step') {
        const impact = await getStepArchiveImpact(token, target.id);
        return tr('Archiving "{title}" also archives {tasks} task(s). Everything can be restored later with "Show archived".', { title: target.title, tasks: impact.tasks });
      }
    } catch {
      // Fall through to the plain question.
    }
    return tr('Archive "{title}"? You can bring it back later with "Show archived".', { title: target.title });
  }

  async function archive(target: TimelineActionTarget) {
    setActionError('');
    const fallback = tr('Archive "{title}"? You can bring it back later with "Show archived".', { title: target.title });
    // Show the question at once; swap in the cascade counts when they arrive.
    setArchiving({ target, message: fallback });
    const message = await archiveMessage(target);
    setArchiving((current) => (current && current.target === target ? { target, message } : current));
  }

  async function confirmArchive(target: TimelineActionTarget) {
    setArchiving(null);
    if (!token) return;
    try {
      if (target.kind === 'dream') await archiveDream(token, target.id);
      else if (target.kind === 'goal') await archiveGoal(token, target.id);
      else if (target.kind === 'step') await archiveStep(token, target.id);
      else await archiveTask(token, target.id);
      showToast(tr('Archived "{title}".', { title: target.title }), {
        action: { label: tr('Undo'), onClick: () => void restore(target) },
      });
      await onChanged();
      if (target.kind === 'dream') onDreamArchived?.(target.id);
    } catch (archiveError) {
      setActionError(archiveError instanceof Error ? archiveError.message : tr('Unable to archive.'));
    }
  }

  async function restore(target: TimelineActionTarget) {
    setActionError('');
    if (!token) return;
    try {
      if (target.kind === 'dream') await restoreDream(token, target.id);
      else if (target.kind === 'goal') await restoreGoal(token, target.id);
      else if (target.kind === 'step') await restoreStep(token, target.id);
      else await restoreTask(token, target.id);
      showToast(tr('Restored "{title}".', { title: target.title }));
      await onChanged();
    } catch (restoreError) {
      setActionError(restoreError instanceof Error ? restoreError.message : tr('Unable to restore.'));
    }
  }

  const needsOwner = adding?.kind === 'step' || adding?.kind === 'task';
  const endRequired = adding?.kind === 'step' || adding?.kind === 'task';
  const endLabel = adding?.kind === 'task' ? tr('Due Date') : tr('Target Date');

  const dialogs: ReactNode = (
    <>
      {actionError && <ErrorMessage message={actionError} />}
      {adding && (
        <Modal title={`${ADD_TITLE[adding.kind]} · ${adding.parent.title}`} onClose={() => setAdding(null)}>
          <form className="form-grid" onSubmit={(event) => void submitAdd(event)}>
            {addError && <div className="field-full"><ErrorMessage message={addError} /></div>}
            <label className="field-full">
              {tr('Title')}
              <Input value={title} onChange={(event) => setTitle(event.target.value)} required autoFocus />
            </label>
            {needsOwner && (
              <label>
                {tr('Owner')}
                <Input value={owner} onChange={(event) => setOwner(event.target.value)} required />
              </label>
            )}
            <label>
              {tr('Priority')}
              <Select aria-label={tr('Priority')} value={priority} onChange={(event) => setPriority(event.target.value as Priority)}>
                {PRIORITIES.map((option) => <option value={option.value} key={option.value}>{option.label}</option>)}
              </Select>
            </label>
            {adding.kind === 'task' && (
              <label>
                {tr('Start Date')}
                <Input type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} />
              </label>
            )}
            <label>
              {endLabel}
              <Input type="date" value={endDate} onChange={(event) => setEndDate(event.target.value)} required={endRequired} />
            </label>
            <div className="field-full row-actions">
              <Button type="submit" disabled={saving || !title.trim() || (needsOwner && !owner.trim()) || (endRequired && !endDate)}>
                {saving ? tr('Saving...') : ADD_TITLE[adding.kind]}
              </Button>
              <Button type="button" variant="secondary" onClick={() => setAdding(null)}>{tr('Cancel')}</Button>
            </div>
          </form>
        </Modal>
      )}
      {archiving && (
        <ConfirmDialog
          title={tr('Confirm archive')}
          message={archiving.message}
          confirmLabel={tr('Archive')}
          onConfirm={() => void confirmArchive(archiving.target)}
          onClose={() => setArchiving(null)}
        />
      )}
    </>
  );

  return { addChild, archive, restore, dialogs };
}
