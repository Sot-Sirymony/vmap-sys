import { FormEvent, useRef, useState } from 'react';
import { ChevronDown, ChevronRight, Rocket } from 'lucide-react';
import Checkbox from '@mui/material/Checkbox';
import Chip from '@mui/material/Chip';
import FormControl from '@mui/material/FormControl';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import {
  archiveDream, getDreamArchiveImpact, permanentlyDeleteDream, restoreDream, updateDream,
} from '../../api/dreamApi';
import {
  archiveGoal, createGoal, getGoalArchiveImpact, permanentlyDeleteGoal, restoreGoal, updateGoal, updateGoalStatus,
} from '../../api/goalApi';
import {
  archiveStep, createStep, getStepArchiveImpact, permanentlyDeleteStep, restoreStep, updateStep,
} from '../../api/stepApi';
import {
  archiveTask, createTask, permanentlyDeleteTask, restoreTask, updateTask, updateTaskStatus,
} from '../../api/taskApi';
import { useAuth } from '../../context/AuthContext';
import { useStoredState } from '../../hooks/useStoredState';
import { moonshotTint, moonshotViolet, moonshotVioletDeep, visionAreaDotColor } from '../../theme';
import type {
  Dream, DreamRequest, DreamStatus, DreamType, Goal, GoalRequest, ObstacleType, Priority, TaskItem, TaskItemRequest,
  VisionStep, VisionStepRequest, WorkStatus,
} from '../../types/vision';
import { useToast } from '../../context/ToastContext';
import { dreamRequest, goalRequest, stepRequest, taskRequest } from '../../utils/entityRequests';
import { nudgeAfterTaskComplete } from '../../utils/completionNudge';
import { suggestPartnerFor } from '../../utils/partnerSuggestion';
import { obstacleTypeLabels } from '../../utils/enumLabels';
import { Button } from '../common/Button';
import { Input } from '../common/Input';
import { CrudModalForm } from '../common/CrudModalForm';
import { PriorityBadge } from '../common/PriorityBadge';
import { ProgressBar } from '../common/ProgressBar';
import { RelativeDate } from '../common/RelativeDate';
import { RowActionsMenu } from '../common/RowActionsMenu';
import { StatusBadge } from '../common/StatusBadge';
import { Textarea } from '../common/Textarea';

type VisionMapTreeProps = {
  dream: Dream;
  visionAreaName: string;
  goals: Goal[];
  steps: VisionStep[];
  tasks: TaskItem[];
  token: string;
  onDataChange: () => Promise<void>;
  /** Fired after the dream itself is permanently deleted — nothing is left to show here. */
  onDreamPermanentlyDeleted?: () => void;
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

const PRIORITY_OPTIONS: { value: Priority; label: string }[] = [
  { value: 'LOW', label: 'Low' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'HIGH', label: 'High' },
  { value: 'CRITICAL', label: 'Critical' },
];

const BLOCKER_CATEGORIES: ObstacleType[] = ['KNOWLEDGE', 'SKILL', 'TIME', 'MONEY', 'DECISION', 'PARTNER', 'MOTIVATION'];

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
 * (N), a full edit modal, and archive/restore/permanent-delete — remembered
 * expand/collapse per dream.
 *
 * Keyboard: ↑/↓ move · → expand or first child · ← collapse or parent ·
 * Enter rename · N add child · Esc cancels an edit.
 *
 * Archived rows (visible only once the page's "Show archived" toggle loads
 * them in) drop inline rename/status/quick-add in favor of the row actions
 * menu's Restore / Delete permanently — the same rule every list page
 * already applies to archived records.
 */
export function VisionMapTree({ dream, visionAreaName, goals, steps, tasks, token, onDataChange, onDreamPermanentlyDeleted }: VisionMapTreeProps) {
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

  // --- Full edit modals: one shared field set per entity kind, repopulated
  // whenever a row's "Edit" action opens it (only one modal is open at a
  // time). Mirrors the field sets on the Dreams/Goals/Steps/Tasks pages,
  // minus the parent selector — a node's place in this tree already fixes
  // its parent, so reparenting stays a list-page action.
  const [editingDream, setEditingDream] = useState(false);
  const [dreamTitle, setDreamTitle] = useState('');
  const [dreamDescription, setDreamDescription] = useState('');
  const [dreamWhyImportant, setDreamWhyImportant] = useState('');
  const [dreamSuccessDefinition, setDreamSuccessDefinition] = useState('');
  const [dreamTypeField, setDreamTypeField] = useState<DreamType>('LONG_TERM');
  const [dreamPriority, setDreamPriority] = useState<Priority>('HIGH');
  const [dreamTargetDate, setDreamTargetDate] = useState('');
  const [dreamStatusField, setDreamStatusField] = useState<DreamStatus>('ACTIVE');
  const [dreamMoonshot, setDreamMoonshot] = useState(false);
  const [dreamMoonshotVision, setDreamMoonshotVision] = useState('');
  const [dreamSaving, setDreamSaving] = useState(false);

  const [editingGoalId, setEditingGoalId] = useState<number | null>(null);
  const [goalTitle, setGoalTitle] = useState('');
  const [goalDescription, setGoalDescription] = useState('');
  const [goalSuccessCriteria, setGoalSuccessCriteria] = useState('');
  const [goalPriority, setGoalPriority] = useState<Priority>('HIGH');
  const [goalTargetDate, setGoalTargetDate] = useState('');
  const [goalStatus, setGoalStatus] = useState<WorkStatus>('NOT_STARTED');
  const [goalMoonshot, setGoalMoonshot] = useState(false);
  const [goalMoonshotVision, setGoalMoonshotVision] = useState('');
  const [goalSaving, setGoalSaving] = useState(false);

  const [editingStepId, setEditingStepId] = useState<number | null>(null);
  const [stepTitle, setStepTitle] = useState('');
  const [stepDescription, setStepDescription] = useState('');
  const [stepSequenceNumber, setStepSequenceNumber] = useState(1);
  const [stepComplex, setStepComplex] = useState(false);
  const [stepPriority, setStepPriority] = useState<Priority>('HIGH');
  const [stepTargetDate, setStepTargetDate] = useState('');
  const [stepStatus, setStepStatus] = useState<WorkStatus>('NOT_STARTED');
  const [stepSaving, setStepSaving] = useState(false);

  const [editingTaskId, setEditingTaskId] = useState<number | null>(null);
  const [taskTitle, setTaskTitle] = useState('');
  const [taskDescription, setTaskDescription] = useState('');
  const [taskOwner, setTaskOwner] = useState('');
  const [taskPriority, setTaskPriority] = useState<Priority>('HIGH');
  const [taskStartDate, setTaskStartDate] = useState('');
  const [taskDueDate, setTaskDueDate] = useState('');
  const [taskStatus, setTaskStatus] = useState<WorkStatus>('NOT_STARTED');
  const [taskProgressPercent, setTaskProgressPercent] = useState(0);
  const [taskEstimatedHours, setTaskEstimatedHours] = useState('');
  const [taskActualHours, setTaskActualHours] = useState('');
  const [taskBlockerReason, setTaskBlockerReason] = useState('');
  const [taskBlockerCategory, setTaskBlockerCategory] = useState<ObstacleType | ''>('');
  const [taskNextAction, setTaskNextAction] = useState('');
  const [taskSaving, setTaskSaving] = useState(false);

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

  function isRowArchived(row: RowInfo): boolean {
    if (row.kind === 'dream') {
      return dream.archived;
    }
    const id = Number(row.key.slice(1));
    if (row.kind === 'goal') {
      return goals.find((item) => item.id === id)?.archived ?? false;
    }
    if (row.kind === 'step') {
      return steps.find((item) => item.id === id)?.archived ?? false;
    }
    return tasks.find((item) => item.id === id)?.archived ?? false;
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

  // --- Dream edit / archive / restore / delete ---

  function openDreamEdit() {
    setDreamTitle(dream.title);
    setDreamDescription(dream.description ?? '');
    setDreamWhyImportant(dream.whyImportant ?? '');
    setDreamSuccessDefinition(dream.successDefinition ?? '');
    setDreamTypeField(dream.dreamType);
    setDreamPriority(dream.priority);
    setDreamTargetDate(dream.targetDate ?? '');
    setDreamStatusField(dream.status);
    setDreamMoonshot(dream.moonshot);
    setDreamMoonshotVision(dream.moonshotVision ?? '');
    setEditingDream(true);
  }

  function cancelDreamEdit() {
    setEditingDream(false);
  }

  async function submitDreamEdit(event: FormEvent) {
    event.preventDefault();
    setDreamSaving(true);
    setError('');
    try {
      const request: DreamRequest = {
        visionAreaId: dream.visionAreaId,
        title: dreamTitle,
        description: dreamDescription,
        whyImportant: dreamWhyImportant,
        successDefinition: dreamSuccessDefinition,
        dreamType: dreamTypeField,
        priority: dreamPriority,
        targetDate: dreamTargetDate || undefined,
        status: dreamStatusField,
        moonshot: dreamMoonshot,
        moonshotVision: dreamMoonshot ? dreamMoonshotVision : undefined,
      };
      await updateDream(token, dream.id, request);
      await onDataChange();
      setEditingDream(false);
      return true;
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Unable to save dream.');
      return false;
    } finally {
      setDreamSaving(false);
    }
  }

  async function dreamArchiveImpactMessage() {
    try {
      const impact = await getDreamArchiveImpact(token, dream.id);
      return `Archiving "${dream.title}" also archives ${impact.goals} goal(s), ${impact.steps} step(s), and ${impact.tasks} task(s). Everything can be restored later with "Show archived".`;
    } catch {
      return 'Archive this dream?';
    }
  }

  async function archiveDreamNode() {
    setError('');
    try {
      await archiveDream(token, dream.id);
      await onDataChange();
    } catch (archiveError) {
      setError(archiveError instanceof Error ? archiveError.message : 'Unable to archive dream.');
    }
  }

  async function restoreDreamNode() {
    setError('');
    try {
      await restoreDream(token, dream.id);
      await onDataChange();
    } catch (restoreError) {
      setError(restoreError instanceof Error ? restoreError.message : 'Unable to restore dream.');
    }
  }

  async function deleteDreamNode() {
    setError('');
    try {
      await permanentlyDeleteDream(token, dream.id);
      onDreamPermanentlyDeleted?.();
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : 'Unable to permanently delete dream.');
    }
  }

  // --- Goal edit / archive / restore / delete ---

  function openGoalEdit(goal: Goal) {
    setEditingGoalId(goal.id);
    setGoalTitle(goal.title);
    setGoalDescription(goal.description ?? '');
    setGoalSuccessCriteria(goal.successCriteria ?? '');
    setGoalPriority(goal.priority);
    setGoalTargetDate(goal.targetDate ?? '');
    setGoalStatus(goal.status);
    setGoalMoonshot(goal.moonshot);
    setGoalMoonshotVision(goal.moonshotVision ?? '');
  }

  function cancelGoalEdit() {
    setEditingGoalId(null);
  }

  async function submitGoalEdit(event: FormEvent) {
    event.preventDefault();
    if (editingGoalId === null) {
      return false;
    }
    setGoalSaving(true);
    setError('');
    try {
      const request: GoalRequest = {
        dreamId: dream.id,
        title: goalTitle,
        description: goalDescription,
        successCriteria: goalSuccessCriteria,
        priority: goalPriority,
        targetDate: goalTargetDate || undefined,
        status: goalStatus,
        moonshot: goalMoonshot,
        moonshotVision: goalMoonshot ? goalMoonshotVision : undefined,
      };
      await updateGoal(token, editingGoalId, request);
      await onDataChange();
      setEditingGoalId(null);
      return true;
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Unable to save goal.');
      return false;
    } finally {
      setGoalSaving(false);
    }
  }

  async function goalArchiveImpactMessage(goal: Goal) {
    try {
      const impact = await getGoalArchiveImpact(token, goal.id);
      return `Archiving "${goal.title}" also archives ${impact.steps} step(s) and ${impact.tasks} task(s). Everything can be restored later with "Show archived".`;
    } catch {
      return 'Archive this goal?';
    }
  }

  async function archiveGoalNode(goal: Goal) {
    setError('');
    try {
      await archiveGoal(token, goal.id);
      await onDataChange();
    } catch (archiveError) {
      setError(archiveError instanceof Error ? archiveError.message : 'Unable to archive goal.');
    }
  }

  async function restoreGoalNode(goal: Goal) {
    setError('');
    try {
      await restoreGoal(token, goal.id);
      await onDataChange();
    } catch (restoreError) {
      setError(restoreError instanceof Error ? restoreError.message : 'Unable to restore goal.');
    }
  }

  async function deleteGoalNode(goal: Goal) {
    setError('');
    try {
      await permanentlyDeleteGoal(token, goal.id);
      await onDataChange();
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : 'Unable to permanently delete goal.');
    }
  }

  // --- Step edit / archive / restore / delete ---

  function openStepEdit(step: VisionStep) {
    setEditingStepId(step.id);
    setStepTitle(step.title);
    setStepDescription(step.description ?? '');
    setStepSequenceNumber(step.sequenceNumber);
    setStepComplex(step.complex);
    setStepPriority(step.priority);
    setStepTargetDate(step.targetDate ?? '');
    setStepStatus(step.status);
  }

  function cancelStepEdit() {
    setEditingStepId(null);
  }

  async function submitStepEdit(event: FormEvent) {
    event.preventDefault();
    if (editingStepId === null) {
      return false;
    }
    setStepSaving(true);
    setError('');
    try {
      const step = steps.find((item) => item.id === editingStepId);
      const request: VisionStepRequest = {
        goalId: step?.goalId ?? 0,
        title: stepTitle,
        description: stepDescription,
        sequenceNumber: stepSequenceNumber,
        complex: stepComplex,
        priority: stepPriority,
        targetDate: stepTargetDate || undefined,
        status: stepStatus,
      };
      await updateStep(token, editingStepId, request);
      await onDataChange();
      setEditingStepId(null);
      return true;
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Unable to save step.');
      return false;
    } finally {
      setStepSaving(false);
    }
  }

  async function stepArchiveImpactMessage(step: VisionStep) {
    try {
      const impact = await getStepArchiveImpact(token, step.id);
      return `Archiving "${step.title}" also archives ${impact.tasks} task(s). Everything can be restored later with "Show archived".`;
    } catch {
      return 'Archive this step?';
    }
  }

  async function archiveStepNode(step: VisionStep) {
    setError('');
    try {
      await archiveStep(token, step.id);
      await onDataChange();
    } catch (archiveError) {
      setError(archiveError instanceof Error ? archiveError.message : 'Unable to archive step.');
    }
  }

  async function restoreStepNode(step: VisionStep) {
    setError('');
    try {
      await restoreStep(token, step.id);
      await onDataChange();
    } catch (restoreError) {
      setError(restoreError instanceof Error ? restoreError.message : 'Unable to restore step.');
    }
  }

  async function deleteStepNode(step: VisionStep) {
    setError('');
    try {
      await permanentlyDeleteStep(token, step.id);
      await onDataChange();
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : 'Unable to permanently delete step.');
    }
  }

  // --- Task edit / archive / restore / delete ---

  function openTaskEdit(task: TaskItem) {
    setEditingTaskId(task.id);
    setTaskTitle(task.title);
    setTaskDescription(task.description ?? '');
    setTaskOwner(task.owner);
    setTaskPriority(task.priority);
    setTaskStartDate(task.startDate ?? '');
    setTaskDueDate(task.dueDate);
    setTaskStatus(task.status);
    setTaskProgressPercent(task.progressPercent);
    setTaskEstimatedHours(task.estimatedHours != null ? String(task.estimatedHours) : '');
    setTaskActualHours(task.actualHours != null ? String(task.actualHours) : '');
    setTaskBlockerReason(task.blockerReason ?? '');
    setTaskBlockerCategory('');
    setTaskNextAction(task.nextAction ?? '');
  }

  function cancelTaskEdit() {
    setEditingTaskId(null);
  }

  async function submitTaskEdit(event: FormEvent) {
    event.preventDefault();
    if (editingTaskId === null) {
      return false;
    }
    setTaskSaving(true);
    setError('');
    try {
      const task = tasks.find((item) => item.id === editingTaskId);
      const request: TaskItemRequest = {
        stepId: task?.stepId ?? 0,
        title: taskTitle,
        description: taskDescription,
        owner: taskOwner,
        priority: taskPriority,
        startDate: taskStartDate || undefined,
        dueDate: taskDueDate,
        status: taskStatus,
        progressPercent: taskProgressPercent,
        estimatedHours: taskEstimatedHours ? Number(taskEstimatedHours) : undefined,
        actualHours: taskActualHours ? Number(taskActualHours) : undefined,
        blockerReason: taskBlockerReason || undefined,
        nextAction: taskNextAction,
      };
      await updateTask(token, editingTaskId, request);
      await onDataChange();
      if (taskStatus === 'COMPLETED') {
        nudgeAfterTaskComplete({
          token, completedTaskId: editingTaskId, tasks, steps, goals, showToast, onApplied: () => void onDataChange(),
        });
      }
      setEditingTaskId(null);
      return true;
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Unable to save task.');
      return false;
    } finally {
      setTaskSaving(false);
    }
  }

  async function archiveTaskNode(task: TaskItem) {
    setError('');
    try {
      await archiveTask(token, task.id);
      await onDataChange();
    } catch (archiveError) {
      setError(archiveError instanceof Error ? archiveError.message : 'Unable to archive task.');
    }
  }

  async function restoreTaskNode(task: TaskItem) {
    setError('');
    try {
      await restoreTask(token, task.id);
      await onDataChange();
    } catch (restoreError) {
      setError(restoreError instanceof Error ? restoreError.message : 'Unable to restore task.');
    }
  }

  async function deleteTaskNode(task: TaskItem) {
    setError('');
    try {
      await permanentlyDeleteTask(token, task.id);
      await onDataChange();
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : 'Unable to permanently delete task.');
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
      if (!isRowArchived(row)) {
        startEdit(row);
      }
    } else if (event.key === 'n' || event.key === 'N') {
      event.preventDefault();
      if (!isRowArchived(row)) {
        focusAdd(row);
      }
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

  // Archived rows are frozen — same as every list page, only Restore / Delete
  // permanently apply, so the plain title replaces the rename affordance.
  function titleCell(row: RowInfo, title: string, archived: boolean) {
    if (archived) {
      return <span className="map-title map-title--archived">{title}</span>;
    }
    return titleOrEdit(row, title);
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

  function statusCell(row: RowInfo, value: string, archived: boolean) {
    if (archived) {
      return <StatusBadge status={value} />;
    }
    return statusSelect(row, value);
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

      {editingDream && (
        <CrudModalForm
          editing
          createLabel="Save"
          editTitle="Edit Dream"
          saving={dreamSaving}
          onSubmit={submitDreamEdit}
          onCancelEdit={cancelDreamEdit}
        >
          <label>
            Title
            <Input value={dreamTitle} onChange={(event) => setDreamTitle(event.target.value)} required autoFocus />
          </label>
          <label>
            Type
            <FormControl fullWidth size="small">
              <Select SelectDisplayProps={{ 'aria-label': 'Type' }} value={dreamTypeField} onChange={(event) => setDreamTypeField(event.target.value as DreamType)}>
                <MenuItem value="SHORT_TERM">Short Term</MenuItem>
                <MenuItem value="LONG_TERM">Long Term</MenuItem>
                <MenuItem value="LIFETIME">Lifetime</MenuItem>
              </Select>
            </FormControl>
          </label>
          <label>
            Target Date
            <Input type="date" value={dreamTargetDate} onChange={(event) => setDreamTargetDate(event.target.value)} />
          </label>
          <label>
            Priority
            <FormControl fullWidth size="small">
              <Select SelectDisplayProps={{ 'aria-label': 'Priority' }} value={dreamPriority} onChange={(event) => setDreamPriority(event.target.value as Priority)}>
                {PRIORITY_OPTIONS.map((option) => <MenuItem value={option.value} key={option.value}>{option.label}</MenuItem>)}
              </Select>
            </FormControl>
          </label>
          <label>
            Status
            <FormControl fullWidth size="small">
              <Select SelectDisplayProps={{ 'aria-label': 'Status' }} value={dreamStatusField} onChange={(event) => setDreamStatusField(event.target.value as DreamStatus)}>
                {DREAM_STATUSES.map((option) => <MenuItem value={option.value} key={option.value}>{option.label}</MenuItem>)}
              </Select>
            </FormControl>
          </label>
          <label className="field-full">
            <span className="inline-meta">
              <Checkbox checked={dreamMoonshot} onChange={(event) => setDreamMoonshot(event.target.checked)} />
              Moonshot dream
            </span>
          </label>
          {dreamMoonshot && (
            <label className="field-full">
              Moonshot vision
              <Textarea value={dreamMoonshotVision} onChange={(event) => setDreamMoonshotVision(event.target.value)} />
            </label>
          )}
          <label className="field-full">
            Why Important
            <Textarea value={dreamWhyImportant} onChange={(event) => setDreamWhyImportant(event.target.value)} />
          </label>
          <label className="field-full">
            Success Definition
            <Textarea value={dreamSuccessDefinition} onChange={(event) => setDreamSuccessDefinition(event.target.value)} />
          </label>
          <label className="field-full">
            Description
            <Textarea value={dreamDescription} onChange={(event) => setDreamDescription(event.target.value)} />
          </label>
        </CrudModalForm>
      )}

      {editingGoalId !== null && (
        <CrudModalForm
          editing
          createLabel="Save"
          editTitle="Edit Goal"
          saving={goalSaving}
          onSubmit={submitGoalEdit}
          onCancelEdit={cancelGoalEdit}
        >
          <label>
            Title
            <Input value={goalTitle} onChange={(event) => setGoalTitle(event.target.value)} required autoFocus />
          </label>
          <label>
            Priority
            <FormControl fullWidth size="small">
              <Select SelectDisplayProps={{ 'aria-label': 'Priority' }} value={goalPriority} onChange={(event) => setGoalPriority(event.target.value as Priority)}>
                {PRIORITY_OPTIONS.map((option) => <MenuItem value={option.value} key={option.value}>{option.label}</MenuItem>)}
              </Select>
            </FormControl>
          </label>
          <label>
            Status
            <FormControl fullWidth size="small">
              <Select SelectDisplayProps={{ 'aria-label': 'Status' }} value={goalStatus} onChange={(event) => setGoalStatus(event.target.value as WorkStatus)}>
                {WORK_STATUSES.map((option) => <MenuItem value={option.value} key={option.value}>{option.label}</MenuItem>)}
              </Select>
            </FormControl>
          </label>
          <label>
            Target Date
            <Input type="date" value={goalTargetDate} onChange={(event) => setGoalTargetDate(event.target.value)} />
          </label>
          <label className="field-full">
            Success Criteria
            <Textarea value={goalSuccessCriteria} onChange={(event) => setGoalSuccessCriteria(event.target.value)} />
          </label>
          <label className="field-full">
            <span className="inline-meta">
              <Checkbox checked={goalMoonshot} onChange={(event) => setGoalMoonshot(event.target.checked)} />
              Moonshot goal
            </span>
          </label>
          {goalMoonshot && (
            <label className="field-full">
              Moonshot vision
              <Textarea value={goalMoonshotVision} onChange={(event) => setGoalMoonshotVision(event.target.value)} />
            </label>
          )}
          <label className="field-full">
            Description
            <Textarea value={goalDescription} onChange={(event) => setGoalDescription(event.target.value)} />
          </label>
        </CrudModalForm>
      )}

      {editingStepId !== null && (
        <CrudModalForm
          editing
          createLabel="Save"
          editTitle="Edit Step"
          saving={stepSaving}
          onSubmit={submitStepEdit}
          onCancelEdit={cancelStepEdit}
        >
          <label>
            Title
            <Input value={stepTitle} onChange={(event) => setStepTitle(event.target.value)} required autoFocus />
          </label>
          <label>
            Sequence
            <Input type="number" min="0" value={stepSequenceNumber} onChange={(event) => setStepSequenceNumber(Number(event.target.value))} required />
          </label>
          <label>
            Target Date
            <Input type="date" value={stepTargetDate} onChange={(event) => setStepTargetDate(event.target.value)} />
          </label>
          <label>
            Priority
            <FormControl fullWidth size="small">
              <Select SelectDisplayProps={{ 'aria-label': 'Priority' }} value={stepPriority} onChange={(event) => setStepPriority(event.target.value as Priority)}>
                {PRIORITY_OPTIONS.map((option) => <MenuItem value={option.value} key={option.value}>{option.label}</MenuItem>)}
              </Select>
            </FormControl>
          </label>
          <label>
            Status
            <FormControl fullWidth size="small">
              <Select SelectDisplayProps={{ 'aria-label': 'Status' }} value={stepStatus} onChange={(event) => setStepStatus(event.target.value as WorkStatus)}>
                {WORK_STATUSES.map((option) => <MenuItem value={option.value} key={option.value}>{option.label}</MenuItem>)}
              </Select>
            </FormControl>
          </label>
          <label className="field-full">
            <span className="inline-meta">
              <Checkbox checked={stepComplex} onChange={(event) => setStepComplex(event.target.checked)} />
              Complex step
            </span>
          </label>
          <label className="field-full">
            Description
            <Textarea value={stepDescription} onChange={(event) => setStepDescription(event.target.value)} />
          </label>
        </CrudModalForm>
      )}

      {editingTaskId !== null && (
        <CrudModalForm
          editing
          createLabel="Save"
          editTitle="Edit Task"
          saving={taskSaving}
          onSubmit={submitTaskEdit}
          onCancelEdit={cancelTaskEdit}
        >
          <label>
            Title
            <Input value={taskTitle} onChange={(event) => setTaskTitle(event.target.value)} required autoFocus />
          </label>
          <label>
            Owner
            <Input value={taskOwner} onChange={(event) => setTaskOwner(event.target.value)} required />
          </label>
          <label>
            Priority
            <FormControl fullWidth size="small">
              <Select SelectDisplayProps={{ 'aria-label': 'Priority' }} value={taskPriority} onChange={(event) => setTaskPriority(event.target.value as Priority)}>
                {PRIORITY_OPTIONS.map((option) => <MenuItem value={option.value} key={option.value}>{option.label}</MenuItem>)}
              </Select>
            </FormControl>
          </label>
          <label>
            Status
            <FormControl fullWidth size="small">
              <Select SelectDisplayProps={{ 'aria-label': 'Status' }} value={taskStatus} onChange={(event) => setTaskStatus(event.target.value as WorkStatus)}>
                {WORK_STATUSES.map((option) => <MenuItem value={option.value} key={option.value}>{option.label}</MenuItem>)}
              </Select>
            </FormControl>
          </label>
          {taskStatus === 'BLOCKED' && (
            <label>
              What's missing?
              <FormControl fullWidth size="small">
                <Select SelectDisplayProps={{ 'aria-label': "What's missing?" }} displayEmpty value={taskBlockerCategory} onChange={(event) => setTaskBlockerCategory(event.target.value as ObstacleType)}>
                  <MenuItem value="" disabled><em>Select a category...</em></MenuItem>
                  {BLOCKER_CATEGORIES.map((category) => <MenuItem value={category} key={category}>{obstacleTypeLabels[category]}</MenuItem>)}
                </Select>
              </FormControl>
              {taskBlockerCategory && (
                <span className="field-hint">
                  {suggestPartnerFor(taskBlockerCategory)?.label ?? 'Look for a partner or connector who can help directly.'}
                </span>
              )}
            </label>
          )}
          {taskStatus === 'BLOCKED' && (
            <label className="field-full">
              Blocker Reason
              <Textarea value={taskBlockerReason} onChange={(event) => setTaskBlockerReason(event.target.value)} required />
            </label>
          )}
          <label>
            Start Date
            <Input type="date" value={taskStartDate} onChange={(event) => setTaskStartDate(event.target.value)} />
          </label>
          <label>
            Due Date
            <Input type="date" value={taskDueDate} onChange={(event) => setTaskDueDate(event.target.value)} required />
          </label>
          <label>
            Progress
            <Input type="number" min="0" max="100" value={taskProgressPercent} onChange={(event) => setTaskProgressPercent(Number(event.target.value))} required />
          </label>
          <label>
            Estimated Hours
            <Input type="number" min="0" step="0.5" value={taskEstimatedHours} onChange={(event) => setTaskEstimatedHours(event.target.value)} />
          </label>
          <label>
            Actual Hours
            <Input type="number" min="0" step="0.5" value={taskActualHours} onChange={(event) => setTaskActualHours(event.target.value)} />
          </label>
          <label className="field-full">
            Next Action
            <Textarea value={taskNextAction} onChange={(event) => setTaskNextAction(event.target.value)} />
          </label>
          <label className="field-full">
            Description
            <Textarea value={taskDescription} onChange={(event) => setTaskDescription(event.target.value)} />
          </label>
        </CrudModalForm>
      )}

    <div className="map-tree" role="tree" aria-label={`Vision map for ${dream.title}`} onKeyDown={handleTreeKeyDown}>
      <div className="map-node map-node--dream" role="none">
        <div className={`map-row${dream.archived ? ' map-row--archived' : ''}`} {...rowShellProps(dreamRow)}>
          {chevron(dreamRow)}
          <div className="map-main">
            <p className="map-area"><span className="area-dot" style={{ backgroundColor: visionAreaDotColor(dream.visionAreaId) }} aria-hidden="true" /> {visionAreaName}</p>
            <div className="map-line">
              <span className="map-code">{dream.code}</span>
              {titleCell(dreamRow, dream.title, dream.archived)}
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
          {statusCell(dreamRow, dream.status, dream.archived)}
          {rail(dreamProgress)}
          <RowActionsMenu
            onEdit={openDreamEdit}
            onArchive={() => void archiveDreamNode()}
            onRestore={() => void restoreDreamNode()}
            onDeletePermanently={() => void deleteDreamNode()}
            archived={dream.archived}
            confirmArchive={dreamArchiveImpactMessage}
            label="Dream actions"
          />
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
                  <div className={`map-row${goal.archived ? ' map-row--archived' : ''}`} {...rowShellProps(goalRow)}>
                    {chevron(goalRow)}
                    <div className="map-main">
                      <div className="map-line">
                        <span className="map-code">{goal.code}</span>
                        {titleCell(goalRow, goal.title, goal.archived)}
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
                    {statusCell(goalRow, goal.status, goal.archived)}
                    {rail(Number(goal.progressPercent))}
                    <RowActionsMenu
                      onEdit={() => openGoalEdit(goal)}
                      onArchive={() => void archiveGoalNode(goal)}
                      onRestore={() => void restoreGoalNode(goal)}
                      onDeletePermanently={() => void deleteGoalNode(goal)}
                      archived={goal.archived}
                      confirmArchive={() => goalArchiveImpactMessage(goal)}
                      label="Goal actions"
                    />
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
                            <div className={`map-row${step.archived ? ' map-row--archived' : ''}`} {...rowShellProps(stepRow)}>
                              {chevron(stepRow)}
                              <div className="map-main">
                                <div className="map-line">
                                  <span className="map-code">{step.code}</span>
                                  {titleCell(stepRow, step.title, step.archived)}
                                  {step.complex && <Chip variant="outlined" size="small" label="Complex" />}
                                  <PriorityBadge priority={step.priority} />
                                </div>
                              </div>
                              {statusCell(stepRow, step.status, step.archived)}
                              {rail(Number(step.progressPercent))}
                              <RowActionsMenu
                                onEdit={() => openStepEdit(step)}
                                onArchive={() => void archiveStepNode(step)}
                                onRestore={() => void restoreStepNode(step)}
                                onDeletePermanently={() => void deleteStepNode(step)}
                                archived={step.archived}
                                confirmArchive={() => stepArchiveImpactMessage(step)}
                                label="Step actions"
                              />
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
                                      <div
                                        className={`map-row${task.status === 'COMPLETED' ? ' map-row--done' : ''}${task.archived ? ' map-row--archived' : ''}`}
                                        {...rowShellProps(taskRow)}
                                      >
                                        <span className="map-chevron map-chevron--spacer" />
                                        <div className="map-main">
                                          <div className="map-line">
                                            <span className="map-code">{task.code}</span>
                                            {titleCell(taskRow, task.title, task.archived)}
                                            <PriorityBadge priority={task.priority} />
                                          </div>
                                          <p className="map-meta">{task.owner} · Due <RelativeDate date={task.dueDate} completed={task.status === 'COMPLETED'} /></p>
                                          {task.blockerReason && <p className="map-blocker">{task.blockerReason}</p>}
                                        </div>
                                        {statusCell(taskRow, task.status, task.archived)}
                                        {rail(Number(task.progressPercent))}
                                        <RowActionsMenu
                                          onEdit={() => openTaskEdit(task)}
                                          onArchive={() => void archiveTaskNode(task)}
                                          onRestore={() => void restoreTaskNode(task)}
                                          onDeletePermanently={() => void deleteTaskNode(task)}
                                          archived={task.archived}
                                          label="Task actions"
                                        />
                                      </div>
                                    </div>
                                  );
                                })}
                                {!step.archived && (
                                  <TaskAddRow
                                    stepId={step.id}
                                    token={token}
                                    defaultOwner={user?.fullName ?? ''}
                                    onDataChange={onDataChange}
                                    inputRef={registerAdd(stepKey)}
                                  />
                                )}
                              </div>
                            )}
                          </div>
                        );
                      })}
                      {!goal.archived && (
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
                      )}
                    </div>
                  )}
                </div>
              );
            })}
            {!dream.archived && (
              <TitleAddRow
                placeholder="New goal title"
                level={2}
                onAdd={async (title) => {
                  await createGoal(token, { dreamId: dream.id, title, priority: 'MEDIUM', status: 'NOT_STARTED', moonshot: false });
                  await onDataChange();
                }}
                inputRef={registerAdd('dream')}
              />
            )}
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
