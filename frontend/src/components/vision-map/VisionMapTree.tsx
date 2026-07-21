import { FormEvent, useRef, useState } from 'react';
import { ChevronDown, ChevronRight, Rocket } from 'lucide-react';
import Chip from '@mui/material/Chip';
import FormControl from '@mui/material/FormControl';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import { updateDream } from '../../api/dreamApi';
import { createGoal, updateGoal, updateGoalStatus } from '../../api/goalApi';
import { createStep, updateStep } from '../../api/stepApi';
import { createTask, updateTask, updateTaskStatus } from '../../api/taskApi';
import { useAuth } from '../../context/AuthContext';
import { useStoredState } from '../../hooks/useStoredState';
import { moonshotTint, moonshotViolet, moonshotVioletDeep, visionAreaDotColor } from '../../theme';
import type {
  Dream, DreamRequest, DreamStatus, Goal, GoalRequest, TaskItem, TaskItemRequest,
  VisionStep, VisionStepRequest, WorkStatus,
} from '../../types/vision';
import { useToast } from '../../context/ToastContext';
import { dreamRequest, goalRequest, stepRequest, taskRequest } from '../../utils/entityRequests';
import { nudgeAfterTaskComplete } from '../../utils/completionNudge';
import { Button } from '../common/Button';
import { Input } from '../common/Input';
import { PriorityBadge } from '../common/PriorityBadge';
import { ProgressBar } from '../common/ProgressBar';
import { RelativeDate } from '../common/RelativeDate';

type VisionMapTreeProps = {
  dream: Dream;
  visionAreaName: string;
  goals: Goal[];
  steps: VisionStep[];
  tasks: TaskItem[];
  token: string;
  onDataChange: () => Promise<void>;
};

const WORK_STATUSES: { value: WorkStatus; label: string }[] = [
  { value: 'NOT_STARTED', label: 'Not Started' },
  { value: 'IN_PROGRESS', label: 'In Progress' },
  { value: 'WAITING', label: 'Waiting' },
  { value: 'BLOCKED', label: 'Blocked' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'PAUSED', label: 'Paused' },
];

const DREAM_STATUSES: { value: DreamStatus; label: string }[] = [
  { value: 'IDEA', label: 'Idea' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'PAUSED', label: 'Paused' },
  { value: 'COMPLETED', label: 'Completed' },
];

type RowInfo = {
  key: string;
  kind: 'dream' | 'goal' | 'step' | 'task';
  level: number;
  parentKey: string | null;
  collapsible: boolean;
};

/**
 * FR-24: the Vision Map as a working surface, not a viewer. One accessible
 * tree (role=tree / treeitem, roving tabindex) where every level supports
 * inline rename (Enter / double-click), quick status, quick-add of a child
 * (N), and remembered expand/collapse per dream.
 *
 * Keyboard: ↑/↓ move · → expand or first child · ← collapse or parent ·
 * Enter rename · N add child · Esc cancels an edit.
 */
export function VisionMapTree({ dream, visionAreaName, goals, steps, tasks, token, onDataChange }: VisionMapTreeProps) {
  const { user } = useAuth();
  const { showToast } = useToast();
  const dreamGoals = goals.filter((goal) => goal.dreamId === dream.id);
  const [collapsedKeys, setCollapsedKeys] = useStoredState<string[]>(`vms-map-collapsed-${dream.id}`, []);
  const collapsed = new Set(collapsedKeys);
  const [focusedKey, setFocusedKey] = useState<string>('dream');
  const [editingKey, setEditingKey] = useState<string | null>(null);
  const [editValue, setEditValue] = useState('');
  const [error, setError] = useState('');
  const rowRefs = useRef(new Map<string, HTMLDivElement>());
  const addRefs = useRef(new Map<string, HTMLInputElement>());

  // Flat list of visible rows in reading order — the keyboard walks this.
  const visibleRows: RowInfo[] = [{ key: 'dream', kind: 'dream', level: 0, parentKey: null, collapsible: true }];
  if (!collapsed.has('dream')) {
    for (const goal of dreamGoals) {
      const goalKey = `g${goal.id}`;
      visibleRows.push({ key: goalKey, kind: 'goal', level: 1, parentKey: 'dream', collapsible: true });
      if (!collapsed.has(goalKey)) {
        for (const step of steps.filter((item) => item.goalId === goal.id)) {
          const stepKey = `s${step.id}`;
          visibleRows.push({ key: stepKey, kind: 'step', level: 2, parentKey: goalKey, collapsible: true });
          if (!collapsed.has(stepKey)) {
            for (const task of tasks.filter((item) => item.stepId === step.id)) {
              visibleRows.push({ key: `t${task.id}`, kind: 'task', level: 3, parentKey: stepKey, collapsible: false });
            }
          }
        }
      }
    }
  }

  function toggleCollapsed(key: string) {
    setCollapsedKeys(collapsed.has(key) ? collapsedKeys.filter((item) => item !== key) : [...collapsedKeys, key]);
  }

  function expand(key: string) {
    if (collapsed.has(key)) {
      setCollapsedKeys(collapsedKeys.filter((item) => item !== key));
    }
  }

  function focusRow(key: string) {
    setFocusedKey(key);
    rowRefs.current.get(key)?.focus();
  }

  function entityTitle(row: RowInfo): string {
    if (row.kind === 'dream') {
      return dream.title;
    }
    const id = Number(row.key.slice(1));
    if (row.kind === 'goal') {
      return goals.find((item) => item.id === id)?.title ?? '';
    }
    if (row.kind === 'step') {
      return steps.find((item) => item.id === id)?.title ?? '';
    }
    return tasks.find((item) => item.id === id)?.title ?? '';
  }

  function startEdit(row: RowInfo) {
    setEditingKey(row.key);
    setEditValue(entityTitle(row));
  }

  async function saveEdit(row: RowInfo) {
    const title = editValue.trim();
    setEditingKey(null);
    if (!title || title === entityTitle(row)) {
      setTimeout(() => focusRow(row.key), 30);
      return;
    }
    setError('');
    const id = Number(row.key.slice(1));
    try {
      if (row.kind === 'dream') {
        await updateDream(token, dream.id, { ...dreamRequest(dream), title });
      } else if (row.kind === 'goal') {
        const goal = goals.find((item) => item.id === id);
        if (goal) {
          await updateGoal(token, goal.id, { ...goalRequest(goal), title });
        }
      } else if (row.kind === 'step') {
        const step = steps.find((item) => item.id === id);
        if (step) {
          await updateStep(token, step.id, { ...stepRequest(step), title });
        }
      } else {
        const task = tasks.find((item) => item.id === id);
        if (task) {
          await updateTask(token, task.id, { ...taskRequest(task), title });
        }
      }
      await onDataChange();
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Unable to rename.');
    }
    setTimeout(() => focusRow(row.key), 50);
  }

  async function changeStatus(row: RowInfo, status: string) {
    setError('');
    const id = Number(row.key.slice(1));
    try {
      if (row.kind === 'dream') {
        await updateDream(token, dream.id, { ...dreamRequest(dream), status: status as DreamStatus });
      } else if (row.kind === 'goal') {
        await updateGoalStatus(token, id, status);
      } else if (row.kind === 'step') {
        const step = steps.find((item) => item.id === id);
        if (step) {
          await updateStep(token, step.id, { ...stepRequest(step), status: status as WorkStatus });
        }
      } else {
        await updateTaskStatus(token, id, status);
      }
      await onDataChange();
      if (row.kind === 'task' && status === 'COMPLETED') {
        nudgeAfterTaskComplete({ token, completedTaskId: id, tasks, steps, goals, showToast, onApplied: () => void onDataChange() });
      }
    } catch (statusError) {
      setError(statusError instanceof Error ? statusError.message : 'Unable to update status.');
    }
  }

  // N on a node jumps to its child quick-add (expanding first if needed).
  function focusAdd(row: RowInfo) {
    const addKey = row.kind === 'task' ? row.parentKey : row.key;
    if (!addKey) {
      return;
    }
    expand(addKey);
    setTimeout(() => addRefs.current.get(addKey)?.focus(), 60);
  }

  function handleTreeKeyDown(event: React.KeyboardEvent) {
    const target = event.target as HTMLElement;
    // Navigation only from the rows themselves — typing in an edit input or
    // quick-add field must behave like a normal input.
    if (!target.dataset.rowKey) {
      return;
    }
    const index = visibleRows.findIndex((row) => row.key === target.dataset.rowKey);
    if (index === -1) {
      return;
    }
    const row = visibleRows[index];
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      if (index + 1 < visibleRows.length) {
        focusRow(visibleRows[index + 1].key);
      }
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      if (index > 0) {
        focusRow(visibleRows[index - 1].key);
      }
    } else if (event.key === 'ArrowRight') {
      event.preventDefault();
      if (row.collapsible && collapsed.has(row.key)) {
        expand(row.key);
      } else if (index + 1 < visibleRows.length && visibleRows[index + 1].parentKey === row.key) {
        focusRow(visibleRows[index + 1].key);
      }
    } else if (event.key === 'ArrowLeft') {
      event.preventDefault();
      if (row.collapsible && !collapsed.has(row.key)) {
        toggleCollapsed(row.key);
      } else if (row.parentKey) {
        focusRow(row.parentKey);
      }
    } else if (event.key === 'Enter' || event.key === 'F2') {
      event.preventDefault();
      startEdit(row);
    } else if (event.key === 'n' || event.key === 'N') {
      event.preventDefault();
      focusAdd(row);
    }
  }

  function rowShellProps(row: RowInfo) {
    return {
      role: 'treeitem',
      'aria-level': row.level + 1,
      'aria-selected': focusedKey === row.key,
      ...(row.collapsible ? { 'aria-expanded': !collapsed.has(row.key) } : {}),
      tabIndex: focusedKey === row.key ? 0 : -1,
      'data-row-key': row.key,
      ref: (element: HTMLDivElement | null) => {
        if (element) {
          rowRefs.current.set(row.key, element);
        } else {
          rowRefs.current.delete(row.key);
        }
      },
      onFocus: () => setFocusedKey(row.key),
    };
  }

  function chevron(row: RowInfo) {
    if (!row.collapsible) {
      return <span className="map-chevron map-chevron--spacer" />;
    }
    const isCollapsed = collapsed.has(row.key);
    return (
      <button
        type="button"
        className="map-chevron"
        tabIndex={-1}
        aria-hidden="true"
        onClick={(event) => {
          event.stopPropagation();
          toggleCollapsed(row.key);
        }}
      >
        {isCollapsed ? <ChevronRight size={16} /> : <ChevronDown size={16} />}
      </button>
    );
  }

  function titleOrEdit(row: RowInfo, title: string) {
    if (editingKey !== row.key) {
      return <span className="map-title" onDoubleClick={() => startEdit(row)}>{title}</span>;
    }
    return (
      <input
        className="map-title-edit"
        value={editValue}
        autoFocus
        aria-label="Rename"
        onChange={(event) => setEditValue(event.target.value)}
        onBlur={() => void saveEdit(row)}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            event.preventDefault();
            void saveEdit(row);
          }
          if (event.key === 'Escape') {
            setEditingKey(null);
            setTimeout(() => focusRow(row.key), 30);
          }
        }}
      />
    );
  }

  function statusSelect(row: RowInfo, value: string) {
    const options = row.kind === 'dream' ? DREAM_STATUSES : WORK_STATUSES;
    return (
      <FormControl size="small" className="map-status">
        <Select value={value} onChange={(event) => void changeStatus(row, event.target.value)} SelectDisplayProps={{ 'aria-label': `Status of ${entityTitle(row)}` }}>
          {options.map((option) => <MenuItem value={option.value} key={option.value}>{option.label}</MenuItem>)}
        </Select>
      </FormControl>
    );
  }

  function rail(progress: number) {
    return (
      <div className="map-rail">
        <ProgressBar value={progress} />
        <span className="map-rail-value">{Math.round(progress)}%</span>
      </div>
    );
  }

  const registerAdd = (key: string) => (element: HTMLInputElement | null) => {
    if (element) {
      addRefs.current.set(key, element);
    } else {
      addRefs.current.delete(key);
    }
  };

  const dreamProgress = dreamGoals.length === 0
    ? 0
    : dreamGoals.reduce((sum, goal) => sum + Number(goal.progressPercent), 0) / dreamGoals.length;
  const dreamRow = visibleRows[0];

  return (
    <div>
      {error && <p className="map-error" role="alert">{error}</p>}
      <p className="map-hint">↑↓ move · → ← expand / collapse · Enter rename · N add child</p>
    <div className="map-tree" role="tree" aria-label={`Vision map for ${dream.title}`} onKeyDown={handleTreeKeyDown}>
      <div className="map-node map-node--dream" role="none">
        <div className="map-row" {...rowShellProps(dreamRow)}>
          {chevron(dreamRow)}
          <div className="map-main">
            <p className="map-area"><span className="area-dot" style={{ backgroundColor: visionAreaDotColor(dream.visionAreaId) }} aria-hidden="true" /> {visionAreaName}</p>
            <div className="map-line">
              <span className="map-code">{dream.code}</span>
              {titleOrEdit(dreamRow, dream.title)}
              {dream.moonshot && (
                <Chip
                  size="small"
                  icon={<Rocket size={14} />}
                  label="Moonshot"
                  sx={{ bgcolor: moonshotTint, color: moonshotVioletDeep, fontWeight: 700, '& .MuiChip-icon': { color: moonshotViolet } }}
                />
              )}
              <PriorityBadge priority={dream.priority} />
            </div>
            {dream.successDefinition && <p className="map-meta">Success looks like: {dream.successDefinition}</p>}
          </div>
          {statusSelect(dreamRow, dream.status)}
          {rail(dreamProgress)}
        </div>

        {!collapsed.has('dream') && (
          <div className="map-children map-children--1" role="group">
            {dreamGoals.map((goal) => {
              const goalKey = `g${goal.id}`;
              const goalRow = visibleRows.find((row) => row.key === goalKey);
              const goalSteps = steps.filter((step) => step.goalId === goal.id);
              if (!goalRow) {
                return null;
              }
              return (
                <div className="map-node map-node--goal" role="none" key={goal.id}>
                  <div className="map-row" {...rowShellProps(goalRow)}>
                    {chevron(goalRow)}
                    <div className="map-main">
                      <div className="map-line">
                        <span className="map-code">{goal.code}</span>
                        {titleOrEdit(goalRow, goal.title)}
                        {goal.moonshot && (
                          <Chip
                            size="small"
                            icon={<Rocket size={14} />}
                            label="Moonshot"
                            sx={{ bgcolor: moonshotTint, color: moonshotVioletDeep, fontWeight: 700, '& .MuiChip-icon': { color: moonshotViolet } }}
                          />
                        )}
                        <PriorityBadge priority={goal.priority} />
                      </div>
                    </div>
                    {statusSelect(goalRow, goal.status)}
                    {rail(Number(goal.progressPercent))}
                  </div>
                  {!collapsed.has(goalKey) && (
                    <div className="map-children map-children--2" role="group">
                      {goalSteps.map((step) => {
                        const stepKey = `s${step.id}`;
                        const stepRow = visibleRows.find((row) => row.key === stepKey);
                        const stepTasks = tasks.filter((task) => task.stepId === step.id);
                        if (!stepRow) {
                          return null;
                        }
                        return (
                          <div className="map-node map-node--step" role="none" key={step.id}>
                            <div className="map-row" {...rowShellProps(stepRow)}>
                              {chevron(stepRow)}
                              <div className="map-main">
                                <div className="map-line">
                                  <span className="map-code">{step.code}</span>
                                  {titleOrEdit(stepRow, step.title)}
                                  {step.complex && <Chip variant="outlined" size="small" label="Complex" />}
                                  <PriorityBadge priority={step.priority} />
                                </div>
                              </div>
                              {statusSelect(stepRow, step.status)}
                              {rail(Number(step.progressPercent))}
                            </div>
                            {!collapsed.has(stepKey) && (
                              <div className="map-children map-children--3" role="group">
                                {stepTasks.map((task) => {
                                  const taskKey = `t${task.id}`;
                                  const taskRow = visibleRows.find((row) => row.key === taskKey);
                                  if (!taskRow) {
                                    return null;
                                  }
                                  return (
                                    <div className="map-node map-node--task" role="none" key={task.id}>
                                      <div className={`map-row${task.status === 'COMPLETED' ? ' map-row--done' : ''}`} {...rowShellProps(taskRow)}>
                                        <span className="map-chevron map-chevron--spacer" />
                                        <div className="map-main">
                                          <div className="map-line">
                                            <span className="map-code">{task.code}</span>
                                            {titleOrEdit(taskRow, task.title)}
                                            <PriorityBadge priority={task.priority} />
                                          </div>
                                          <p className="map-meta">{task.owner} · Due <RelativeDate date={task.dueDate} completed={task.status === 'COMPLETED'} /></p>
                                          {task.blockerReason && <p className="map-blocker">{task.blockerReason}</p>}
                                        </div>
                                        {statusSelect(taskRow, task.status)}
                                        {rail(Number(task.progressPercent))}
                                      </div>
                                    </div>
                                  );
                                })}
                                <TaskAddRow
                                  stepId={step.id}
                                  token={token}
                                  defaultOwner={user?.fullName ?? ''}
                                  onDataChange={onDataChange}
                                  inputRef={registerAdd(stepKey)}
                                />
                              </div>
                            )}
                          </div>
                        );
                      })}
                      <TitleAddRow
                        placeholder="New step title"
                        level={3}
                        onAdd={async (title) => {
                          await createStep(token, {
                            goalId: goal.id,
                            title,
                            sequenceNumber: goalSteps.reduce((max, step) => Math.max(max, step.sequenceNumber), 0) + 1,
                            complex: false,
                            priority: 'MEDIUM',
                            status: 'NOT_STARTED',
                          });
                          await onDataChange();
                        }}
                        inputRef={registerAdd(goalKey)}
                      />
                    </div>
                  )}
                </div>
              );
            })}
            <TitleAddRow
              placeholder="New goal title"
              level={2}
              onAdd={async (title) => {
                await createGoal(token, { dreamId: dream.id, title, priority: 'MEDIUM', status: 'NOT_STARTED', moonshot: false });
                await onDataChange();
              }}
              inputRef={registerAdd('dream')}
            />
          </div>
        )}
      </div>
    </div>
    </div>
  );
}

function TitleAddRow({ placeholder, level, onAdd, inputRef }: {
  placeholder: string;
  level: number;
  onAdd: (title: string) => Promise<void>;
  inputRef: (element: HTMLInputElement | null) => void;
}) {
  const [title, setTitle] = useState('');
  const [busy, setBusy] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!title.trim()) {
      return;
    }
    setBusy(true);
    try {
      await onAdd(title.trim());
      setTitle('');
    } finally {
      setBusy(false);
    }
  }

  // A treeitem in its own right — the "add a child here" affordance — so the
  // enclosing group's children stay valid for the ARIA tree pattern.
  return (
    <div role="treeitem" aria-level={level} aria-selected={false}>
      <form className="map-add" onSubmit={(event) => void handleSubmit(event)}>
        <Input value={title} onChange={(event) => setTitle(event.target.value)} placeholder={placeholder} aria-label={placeholder} inputRef={inputRef} />
        <Button type="submit" variant="secondary" disabled={busy || !title.trim()}>{busy ? 'Adding...' : 'Add'}</Button>
      </form>
    </div>
  );
}

function TaskAddRow({ stepId, token, defaultOwner, onDataChange, inputRef }: {
  stepId: number;
  token: string;
  defaultOwner: string;
  onDataChange: () => Promise<void>;
  inputRef: (element: HTMLInputElement | null) => void;
}) {
  const [title, setTitle] = useState('');
  const [owner, setOwner] = useState(defaultOwner);
  const [dueDate, setDueDate] = useState('');
  const [busy, setBusy] = useState(false);

  // BR-16: a task needs title + owner + due date — owner defaults to the
  // signed-in user, the due date is asked inline (FR-22.2).
  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!title.trim() || !owner.trim() || !dueDate) {
      return;
    }
    setBusy(true);
    try {
      await createTask(token, {
        stepId,
        title: title.trim(),
        owner: owner.trim(),
        priority: 'MEDIUM',
        dueDate,
        status: 'NOT_STARTED',
        progressPercent: 0,
      });
      setTitle('');
      setDueDate('');
      await onDataChange();
    } finally {
      setBusy(false);
    }
  }

  return (
    <div role="treeitem" aria-level={4} aria-selected={false}>
      <form className="map-add map-add--task" onSubmit={(event) => void handleSubmit(event)}>
        <Input value={title} onChange={(event) => setTitle(event.target.value)} placeholder="New task title" aria-label="New task title" inputRef={inputRef} />
        <Input value={owner} onChange={(event) => setOwner(event.target.value)} placeholder="Owner" aria-label="Owner" />
        <Input type="date" value={dueDate} onChange={(event) => setDueDate(event.target.value)} aria-label="Due date" />
        <Button type="submit" variant="secondary" disabled={busy || !title.trim() || !owner.trim() || !dueDate}>
          {busy ? 'Adding...' : 'Add task'}
        </Button>
      </form>
    </div>
  );
}
