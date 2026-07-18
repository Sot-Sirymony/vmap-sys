import { FormEvent, useState } from 'react';
import { createTask } from '../../api/taskApi';
import { useAuth } from '../../context/AuthContext';
import Chip from '@mui/material/Chip';
import type { TaskItem, VisionStep } from '../../types/vision';
import { Button } from '../common/Button';
import { Input } from '../common/Input';
import { PriorityBadge } from '../common/PriorityBadge';
import { ProgressBar } from '../common/ProgressBar';
import { StatusBadge } from '../common/StatusBadge';
import { TaskNode } from './TaskNode';

type StepNodeProps = {
  step: VisionStep;
  tasks: TaskItem[];
  token: string;
  onDataChange: () => Promise<void>;
};

export function StepNode({ step, tasks, token, onDataChange }: StepNodeProps) {
  const { user } = useAuth();
  const stepTasks = tasks.filter((task) => task.stepId === step.id);
  const [title, setTitle] = useState('');
  // FR-22.2: owner defaults to the signed-in user; still editable inline.
  const [owner, setOwner] = useState(user?.fullName ?? '');
  const [dueDate, setDueDate] = useState('');
  const [busy, setBusy] = useState(false);

  async function handleAddTask(event: FormEvent) {
    event.preventDefault();
    if (!title.trim() || !owner.trim() || !dueDate) {
      return;
    }
    setBusy(true);
    try {
      await createTask(token, {
        stepId: step.id,
        title: title.trim(),
        owner: owner.trim(),
        priority: 'MEDIUM',
        dueDate,
        status: 'NOT_STARTED',
        progressPercent: 0,
      });
      setTitle('');
      setOwner('');
      setDueDate('');
      await onDataChange();
    } finally {
      setBusy(false);
    }
  }

  return (
    <article className="vision-tree-step">
      <div className="vision-tree-row">
        <span className="vision-tree-code">{step.code}</span>
        <strong>{step.title}</strong>
        {step.complex && <Chip variant="outlined" size="small" label="Complex" />}
        <PriorityBadge priority={step.priority} />
        <StatusBadge status={step.status} />
      </div>
      <ProgressBar value={Number(step.progressPercent)} />
      <div className="vision-tree-children">
        {stepTasks.length === 0 ? (
          <p className="vision-tree-empty">No tasks yet.</p>
        ) : (
          stepTasks.map((task) => <TaskNode task={task} key={task.id} />)
        )}
        <form className="quick-add quick-add-task" onSubmit={handleAddTask}>
          <Input value={title} onChange={(event) => setTitle(event.target.value)} placeholder="New task title" />
          <Input value={owner} onChange={(event) => setOwner(event.target.value)} placeholder="Owner" />
          <Input type="date" value={dueDate} onChange={(event) => setDueDate(event.target.value)} />
          <Button type="submit" variant="secondary" disabled={busy || !title.trim() || !owner.trim() || !dueDate}>
            {busy ? 'Adding...' : 'Add Task'}
          </Button>
        </form>
      </div>
    </article>
  );
}
