import { FormEvent, useEffect, useState } from 'react';
import { CheckSquare, GripVertical } from 'lucide-react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { listDreams } from '../api/dreamApi';
import { listGoals } from '../api/goalApi';
import { listIdealPartnerProfiles } from '../api/idealPartnerProfileApi';
import { listSteps } from '../api/stepApi';
import { listVisionAreas } from '../api/visionAreaApi';
import { archiveTask, permanentlyDeleteTask, createTask, listTasks, reorderTaskList, restoreTask, updateTask, updateTaskStatus } from '../api/taskApi';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Checkbox from '@mui/material/Checkbox';
import FormControl from '@mui/material/FormControl';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import { Breadcrumbs } from '../components/common/Breadcrumbs';
import { BulkArchiveAction } from '../components/common/BulkArchiveAction';
import { Button } from '../components/common/Button';
import { CrudModalForm } from '../components/common/CrudModalForm';
import { DataTable, type DataTableColumn } from '../components/common/DataTable';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { FilterPanel } from '../components/common/FilterPanel';
import { Input } from '../components/common/Input';
import { SearchBar } from '../components/common/SearchBar';
import { Loading } from '../components/common/Loading';
import { PriorityBadge } from '../components/common/PriorityBadge';
import { ProgressBar } from '../components/common/ProgressBar';
import { QuickAddRow } from '../components/common/QuickAddRow';
import { RowActionsMenu } from '../components/common/RowActionsMenu';
import { ShowArchivedToggle } from '../components/common/ShowArchivedToggle';
import { RelativeDate } from '../components/common/RelativeDate';
import { StatusBadge } from '../components/common/StatusBadge';
import { SummaryStrip } from '../components/common/SummaryStrip';
import { StatusBoard } from '../components/common/StatusBoard';
import { ViewToggle, type ViewMode } from '../components/common/ViewToggle';
import { Textarea } from '../components/common/Textarea';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { useCrudEntity } from '../hooks/useCrudEntity';
import { useEnergyOvercommitNudge } from '../hooks/useEnergyOvercommitNudge';
import { useStoredState } from '../hooks/useStoredState';
import { FilterSelect, optionsFromEntities, optionsFromLabels } from '../components/common/FilterSelect';
import { useUrlFilter, useUrlFilterBatch, useUrlFlag } from '../hooks/useUrlFilter';
import type { Dream, EnergyDemand, Goal, IdealPartnerProfile, ObstacleType, Priority, TaskItem, TaskItemRequest, VisionArea, VisionStep, WorkStatus } from '../types/vision';
import { nudgeAfterTaskComplete } from '../utils/completionNudge';
import { taskRequest } from '../utils/entityRequests';
import { isOverdue } from '../utils/overdue';
import { suggestPartnerFor } from '../utils/partnerSuggestion';
import { energyDemandLabels, obstacleTypeLabels, priorityLabels, workStatusLabels } from '../utils/enumLabels';
import { priorityRank, workStatusRank, letterRankFilterOptions } from '../utils/sortRank';
import { PageSection } from './PageSection';
import { tr } from '../i18n/translate';

const columns: WorkStatus[] = ['NOT_STARTED', 'IN_PROGRESS', 'WAITING', 'BLOCKED', 'COMPLETED', 'PAUSED'];
const blockerCategories: ObstacleType[] = ['KNOWLEDGE', 'SKILL', 'TIME', 'MONEY', 'DECISION', 'PARTNER', 'MOTIVATION'];

export function TasksBoardPage() {
  const { token, user } = useAuth();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const energyNudge = useEnergyOvercommitNudge();
  const [searchParams, setSearchParams] = useSearchParams();
  const filterStepId = searchParams.get('stepId');
  const [autoOpenCreate, setAutoOpenCreate] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  // The dashboard's "New Task" button lands here with ?new=true — open the
  // create form once and drop the flag, so refresh or back doesn't reopen it.
  const [openCreateFromUrl, setOpenCreateFromUrl] = useUrlFlag('new');
  useEffect(() => {
    if (openCreateFromUrl) {
      setCreateOpen(true);
      setOpenCreateFromUrl(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [openCreateFromUrl]);
  const crud = useCrudEntity<TaskItem, TaskItemRequest>({
    token,
    entityLabel: 'tasks',
    list: listTasks,
    create: createTask,
    update: updateTask,
    archive: archiveTask,
    permanentlyDelete: permanentlyDeleteTask,
    restore: restoreTask,
    undoableArchive: true,
  });
  const [steps, setSteps] = useState<VisionStep[]>([]);
  const [goals, setGoals] = useState<Goal[]>([]);
  const [dreams, setDreams] = useState<Dream[]>([]);
  const [visionAreas, setVisionAreas] = useState<VisionArea[]>([]);
  // Ideal partner profiles by step (FR-15.5): a blocked task whose step has one
  // points at it, so the fix is one click away instead of a memory exercise.
  const [profiles, setProfiles] = useState<IdealPartnerProfile[]>([]);
  const [stepId, setStepId] = useState('');
  const [quickParentId, setQuickParentId] = useState('');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  // FR-22.2: owner defaults to the signed-in user; still editable.
  const [owner, setOwner] = useState(user?.fullName ?? '');
  const [priority, setPriority] = useState<Priority>('HIGH');
  const [letterRank, setLetterRank] = useState('');
  const [startDate, setStartDate] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [status, setStatus] = useState<WorkStatus>('NOT_STARTED');
  const [progressPercent, setProgressPercent] = useState(0);
  // Kept as strings so the inputs can be empty (hours are optional).
  const [estimatedHours, setEstimatedHours] = useState('');
  const [actualHours, setActualHours] = useState('');
  const [blockerReason, setBlockerReason] = useState('');
  const [blockerCategory, setBlockerCategory] = useState<ObstacleType | ''>('');
  const [nextAction, setNextAction] = useState('');
  // FR-34.1: optional energy tag; defaults to NEUTRAL for a new task.
  const [energyDemand, setEnergyDemand] = useState<EnergyDemand>('NEUTRAL');
  // In the URL, not component state: the dashboard links straight into a
  // filtered board, and a filtered board stays shareable and bookmarkable.
  const [filterOwner, setFilterOwner] = useUrlFilter('owner');
  const [filterPriority, setFilterPriority] = useUrlFilter('priority');
  const [filterLetterRank, setFilterLetterRank] = useUrlFilter('letterRank');
  const [filterVisionAreaId] = useUrlFilter('visionAreaId');
  const [filterDreamId] = useUrlFilter('dreamId');
  const [filterGoalId, setFilterGoalId] = useUrlFilter('goalId');
  // One navigation for multi-key changes — chained setters undo each other.
  const setFilterBatch = useUrlFilterBatch();
  const [filterOverdueOnly, setFilterOverdueOnly] = useUrlFlag('overdue');
  // Due-date range (BRD C-6). Inclusive on both ends; either bound may be empty.
  // This is what makes the dashboard's "Due This Week" tile a link.
  const [filterDueFrom, setFilterDueFrom] = useUrlFilter('dueFrom');
  const [filterDueTo, setFilterDueTo] = useUrlFilter('dueTo');
  // In board view this narrows the board to a single column ("show me the
  // blocked ones" means showing one column); in list view it filters rows, so
  // a dashboard drill-down lands on the same set either way.
  const [filterStatus, setFilterStatus] = useUrlFilter('status');
  const visibleColumns = filterStatus
    ? columns.filter((column) => column === filterStatus)
    : columns;
  const [searchTerm, setSearchTerm] = useState('');
  const [viewMode, setViewMode] = useStoredState<ViewMode>('vms-view-tasks', 'list');
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [bulkStatus, setBulkStatus] = useState<WorkStatus>('IN_PROGRESS');
  const [bulkPriority, setBulkPriority] = useState<Priority>('HIGH');
  const [bulkApplying, setBulkApplying] = useState(false);

  // FR-29.4 bulk edits. Status uses the PATCH endpoint; priority has no
  // PATCH, so each task is sent back whole with only priority changed.
  async function applyBulk(kind: 'status' | 'priority') {
    if (!token || selectedIds.size === 0) {
      return;
    }
    setBulkApplying(true);
    try {
      await Promise.all([...selectedIds].map((id) => {
        if (kind === 'status') {
          return updateTaskStatus(token, id, bulkStatus);
        }
        const task = crud.items.find((item) => item.id === id);
        return task ? updateTask(token, task.id, { ...taskRequest(task), priority: bulkPriority }) : Promise.resolve();
      }));
      await crud.reload();
      showToast(tr(selectedIds.size === 1 ? tr('Updated 1 task.') : 'Updated {count} tasks.', { count: selectedIds.size }));
      setSelectedIds(new Set());
    } catch (bulkError) {
      crud.setError(bulkError instanceof Error ? bulkError.message : tr('Unable to update selected tasks.'));
    } finally {
      setBulkApplying(false);
    }
  }

  // FR-29.4 preset filters: one tap to the two triage views used daily.
  function applyMyOverdue() {
    setFilterOwner(user?.fullName ?? '');
    if (!filterOverdueOnly) {
      setFilterOverdueOnly(true);
    }
  }

  function applyDueThisWeek() {
    const today = new Date();
    const iso = (date: Date) => date.toISOString().slice(0, 10);
    const end = new Date(today);
    end.setDate(today.getDate() + 7);
    setFilterBatch({ dueFrom: iso(today), dueTo: iso(end) });
  }

  // Arrived from a step's "Add task" shortcut (or FR-60.2's "turn into a
  // task" from an obstacle's criticism triage): pre-select that step and
  // open the create form, optionally pre-filling the title, then strip the
  // params so a refresh doesn't reopen it.
  useEffect(() => {
    if (searchParams.get('create') !== 'task') {
      return;
    }
    const parent = searchParams.get('parent');
    if (parent) {
      setStepId(parent);
    }
    const prefillTitle = searchParams.get('title');
    if (prefillTitle) {
      setTitle(prefillTitle);
    }
    // V15 (AH-FR-37): an obstacle's suggested next task also brings its description, owner, due date and priority.
    const prefillDescription = searchParams.get('description');
    if (prefillDescription) {
      setDescription(prefillDescription);
    }
    const prefillOwner = searchParams.get('owner');
    if (prefillOwner) {
      setOwner(prefillOwner);
    }
    const prefillDue = searchParams.get('dueDate');
    if (prefillDue && /^\d{4}-\d{2}-\d{2}$/.test(prefillDue)) {
      setDueDate(prefillDue);
    }
    const prefillPriority = searchParams.get('priority');
    if (prefillPriority && ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].includes(prefillPriority)) {
      setPriority(prefillPriority as Priority);
    }
    setAutoOpenCreate(true);
    const next = new URLSearchParams(searchParams);
    for (const key of ['create', 'parent', 'title', 'description', 'owner', 'dueDate', 'priority']) {
      next.delete(key);
    }
    setSearchParams(next, { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  useEffect(() => {
    if (!token) {
      return;
    }
    void crud.reload();
    void listSteps(token).then((stepData) => {
      setSteps(stepData);
      setStepId((current) => current || filterStepId || String(stepData[0]?.id ?? ''));
      setQuickParentId((current) => current || filterStepId || String(stepData[0]?.id ?? ''));
    });
    void listGoals(token).then(setGoals);
    void listDreams(token).then(setDreams);
    void listVisionAreas(token).then(setVisionAreas);
    void listIdealPartnerProfiles(token).then(setProfiles);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  // An active step filter is a strong hint of which step the next task
  // belongs under.
  function openCreateTask() {
    if (filterStepId) {
      setStepId(filterStepId);
    }
    setCreateOpen(true);
  }

  // FR-22.1/22.2 quick-add: owner defaults to the signed-in user; the due
  // date — which no default can supply — is asked inline (BR-16).
  async function handleQuickAdd(title: string, dueDate?: string) {
    if (!token || !quickParentId || !dueDate) {
      return;
    }
    // As on Goals and Steps: a failed add says why, instead of failing silently.
    try {
      await createTask(token, {
        stepId: Number(quickParentId),
        title,
        owner: user?.fullName ?? 'Me',
        priority: 'MEDIUM',
        dueDate,
        status: 'NOT_STARTED',
        progressPercent: 0,
      });
      await crud.reload();
    } catch (addError) {
      crud.setError(addError instanceof Error ? addError.message : tr('Unable to add task.'));
    }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!stepId) {
      return false;
    }
    const success = await crud.save({
      stepId: Number(stepId),
      title,
      description,
      owner,
      priority,
      letterRank: letterRank || undefined,
      startDate: startDate || undefined,
      dueDate,
      status,
      progressPercent,
      estimatedHours: estimatedHours ? Number(estimatedHours) : undefined,
      actualHours: actualHours ? Number(actualHours) : undefined,
      blockerReason: blockerReason || undefined,
      nextAction,
      energyDemand,
    });
    if (success) {
      if (token && crud.editingId !== null && status === 'COMPLETED') {
        nudgeAfterTaskComplete({
          token,
          completedTaskId: crud.editingId,
          tasks: crud.items,
          steps,
          goals,
          showToast,
          onApplied: () => void crud.reload(),
        });
      }
      // FR-34.4: coaching prompt when this DRAIN task tips its week over the
      // threshold. Fires whether creating or editing; never blocks the save.
      energyNudge.maybeNudge(dueDate, energyDemand, crud.editingId, crud.items);
      setTitle('');
      setDescription('');
      setLetterRank('');
      setBlockerReason('');
      setBlockerCategory('');
      setNextAction('');
      setProgressPercent(0);
      setEstimatedHours('');
      setActualHours('');
    }
    return success;
  }

  async function handleMove(id: number, nextStatus: WorkStatus) {
    if (!token) {
      return;
    }
    try {
      await updateTaskStatus(token, id, nextStatus);
      await crud.reload();
      if (nextStatus === 'COMPLETED') {
        nudgeAfterTaskComplete({
          token,
          completedTaskId: id,
          tasks: crud.items,
          steps,
          goals,
          showToast,
          onApplied: () => void crud.reload(),
        });
      }
    } catch (moveError) {
      crud.setError(moveError instanceof Error ? moveError.message : tr('Unable to update task status.'));
    }
  }

  function startEdit(task: TaskItem) {
    crud.startEdit(task.id);
    setStepId(String(task.stepId));
    setTitle(task.title);
    setDescription(task.description ?? '');
    setOwner(task.owner);
    setPriority(task.priority);
    setLetterRank(task.letterRank ?? '');
    setStartDate(task.startDate ?? '');
    setDueDate(task.dueDate);
    setStatus(task.status);
    setProgressPercent(task.progressPercent);
    setEstimatedHours(task.estimatedHours != null ? String(task.estimatedHours) : '');
    setActualHours(task.actualHours != null ? String(task.actualHours) : '');
    setBlockerReason(task.blockerReason ?? '');
    setBlockerCategory('');
    setNextAction(task.nextAction ?? '');
    setEnergyDemand(task.energyDemand ?? 'NEUTRAL');
  }

  function cancelEdit() {
    crud.cancelEdit();
    setTitle('');
    setDescription('');
    setOwner(user?.fullName ?? '');
    setPriority('HIGH');
    setLetterRank('');
    setStartDate('');
    setDueDate('');
    setStatus('NOT_STARTED');
    setProgressPercent(0);
    setEstimatedHours('');
    setActualHours('');
    setBlockerReason('');
    setBlockerCategory('');
    setNextAction('');
    setEnergyDemand('NEUTRAL');
  }

  const stepById = new Map(steps.map((step) => [step.id, step]));
  const goalById = new Map(goals.map((goal) => [goal.id, goal]));
  const profileByStepId = new Map(profiles.map((profile) => [profile.stepId, profile]));
  const dreamById = new Map(dreams.map((dream) => [dream.id, dream]));
  const dreamsForFilter = filterVisionAreaId
    ? dreams.filter((dream) => String(dream.visionAreaId) === filterVisionAreaId)
    : dreams;
  const goalsForFilter = filterDreamId
    ? goals.filter((goal) => String(goal.dreamId) === filterDreamId)
    : goals;

  const visibleTasks = crud.items.filter((task) => {
    if (filterStepId && String(task.stepId) !== filterStepId) {
      return false;
    }
    if (filterOwner && !task.owner.toLowerCase().includes(filterOwner.toLowerCase())) {
      return false;
    }
    if (filterPriority && task.priority !== filterPriority) {
      return false;
    }
    if (filterLetterRank && task.letterRank !== filterLetterRank) {
      return false;
    }
    if (filterStatus && task.status !== filterStatus) {
      return false;
    }
    if (filterOverdueOnly && !isOverdue(task.dueDate, task.status)) {
      return false;
    }
    // Due-date range: string compare is safe because dueDate is ISO yyyy-MM-dd.
    if (filterDueFrom && task.dueDate < filterDueFrom) {
      return false;
    }
    if (filterDueTo && task.dueDate > filterDueTo) {
      return false;
    }
    if (searchTerm) {
      const haystack = `${task.title} ${task.owner} ${task.description ?? ''} ${task.nextAction ?? ''} ${task.blockerReason ?? ''}`.toLowerCase();
      if (!haystack.includes(searchTerm.toLowerCase())) {
        return false;
      }
    }
    const step = stepById.get(task.stepId);
    if (filterGoalId && String(step?.goalId ?? '') !== filterGoalId) {
      return false;
    }
    if (filterDreamId) {
      const goal = step ? goalById.get(step.goalId) : undefined;
      if (String(goal?.dreamId ?? '') !== filterDreamId) {
        return false;
      }
    }
    if (filterVisionAreaId) {
      const goal = step ? goalById.get(step.goalId) : undefined;
      const dream = goal ? dreamById.get(goal.dreamId) : undefined;
      if (String(dream?.visionAreaId ?? '') !== filterVisionAreaId) {
        return false;
      }
    }
    return true;
  }).sort(byListOrder);

  // "#1, #2…": each active task's place in the overall order, so numbers run
  // 1..N even before the first drag (unordered tasks follow, oldest first).
  const orderNumber = new Map(
    crud.items.filter((task) => !task.archived).sort(byListOrder).map((task, index) => [task.id, index + 1]),
  );

  // FR-57.4-style: only letters actually in use are offered, so the dropdown
  // doesn't show 26 mostly-empty options.
  const letterRankOptions = letterRankFilterOptions(crud.items);

  // Drag-and-drop reorder (List view, see DataTableReorder): the user's own
  // order across every task, whatever its step — drop a task onto another
  // and it takes that task's place. Separate from the per-step order the
  // Vision Map uses, so this never reshuffles a step there. Used by the List
  // view and the Board (a card dropped onto another card; see StatusBoard).
  async function handleReorderTasks(draggedId: number, targetId: number) {
    if (!token || draggedId === targetId) {
      return;
    }
    const orderedIds = crud.items
      .filter((item) => !item.archived)
      .sort(byListOrder)
      .map((item) => item.id);
    const fromIndex = orderedIds.indexOf(draggedId);
    const toIndex = orderedIds.indexOf(targetId);
    if (fromIndex === -1 || toIndex === -1) {
      return;
    }
    const nextOrder = [...orderedIds];
    nextOrder.splice(fromIndex, 1);
    nextOrder.splice(toIndex, 0, draggedId);
    try {
      await reorderTaskList(token, nextOrder);
      await crud.reload();
    } catch (reorderError) {
      crud.setError(reorderError instanceof Error ? reorderError.message : tr('Unable to reorder tasks.'));
    }
  }

  // FR-23.1 acceptance: from any task row, its step, goal, dream, and area
  // are each one click away.
  function taskCrumbs(task: TaskItem) {
    const step = steps.find((item) => item.id === task.stepId);
    const goal = goals.find((item) => item.id === step?.goalId);
    const dream = dreams.find((item) => item.id === goal?.dreamId);
    const area = visionAreas.find((item) => item.id === dream?.visionAreaId);
    return [
      area && { label: area.name, to: `/dreams?visionAreaId=${area.id}` },
      dream && { label: dream.title, to: `/dreams/${dream.id}` },
      goal && { label: goal.title, to: `/goals?dreamId=${goal.dreamId}` },
      step && { label: step.title, to: `/steps?goalId=${step.goalId}` },
    ].filter(Boolean) as { label: string; to: string }[];
  }

  const taskColumns: DataTableColumn<TaskItem>[] = [
    {
      key: 'position',
      label: tr('Order'),
      sortValue: (task) => orderNumber.get(task.id) ?? Number.MAX_SAFE_INTEGER,
      // A grip beside the number says "drag this row" at a glance.
      render: (task) => (
        <span className="order-cell">
          {!task.archived && <GripVertical size={16} aria-hidden="true" className="order-grip" />}
          {orderNumber.get(task.id) ?? '—'}
        </span>
      ),
    },
    {
      key: 'title',
      label: tr('Task'),
      sortValue: (task) => task.title,
      // Wide enough that the dream › goal › step path stays on a line or
      // two; a squeezed column made each row a screen tall, too tall to drag.
      sx: { fontWeight: 500, minWidth: 280 },
      render: (task) => (
        <>
          {task.title}
          <Breadcrumbs crumbs={taskCrumbs(task)} />
        </>
      ),
    },
    { key: 'owner', label: tr('Owner'), sortValue: (task) => task.owner, render: (task) => task.owner },
    { key: 'dueDate', label: tr('Due'), sortValue: (task) => task.dueDate, render: (task) => <RelativeDate date={task.dueDate} completed={task.status === 'COMPLETED'} /> },
    {
      key: 'priority',
      label: tr('Priority'),
      sortValue: (task) => priorityRank(task.priority),
      render: (task) => <PriorityBadge priority={task.priority} />,
    },
    {
      key: 'letterRank',
      label: tr('Rank'),
      sortValue: (task) => task.letterRank ?? '',
      render: (task) => task.letterRank ?? '—',
    },
    {
      key: 'status',
      label: tr('Status'),
      sortValue: (task) => workStatusRank(task.status),
      render: (task) => <StatusBadge status={task.status} />,
    },
    {
      key: 'progress',
      label: tr('Progress'),
      sortValue: (task) => Number(task.progressPercent),
      sx: { width: 160 },
      render: (task) => <ProgressBar value={Number(task.progressPercent)} />,
    },
    {
      key: 'hours',
      label: tr('Hours'),
      sortValue: (task) => Number(task.actualHours ?? 0),
      render: (task) => formatHours(task),
    },
    {
      key: 'actions',
      label: tr('Action'),
      className: 'row-actions',
      render: (task) => (
        <RowActionsMenu
          onEdit={() => startEdit(task)}
          onArchive={() => void crud.archive(task.id)}
          onRestore={() => void crud.restore(task.id)}
          onDeletePermanently={() => void crud.permanentlyDelete(task.id)}
          archived={task.archived}
          label={tr('Task actions')}
        />
      ),
    },
  ];

  // The list view's card on a phone: just what you act on. Title and menu,
  // where it sits (goal › step, one line), who and when, badges, progress,
  // and a status dropdown so a task can be moved or ticked off in one tap
  // without opening the full form.
  function taskMobileCard(task: TaskItem) {
    const step = steps.find((item) => item.id === task.stepId);
    const goal = goals.find((item) => item.id === step?.goalId);
    const place = [goal?.title, step?.title].filter(Boolean).join(' › ');
    return (
      <>
        <div className="task-card-head">
          <strong className="task-card-title">{task.title}</strong>
          {taskColumns.find((column) => column.key === 'actions')?.render(task)}
        </div>
        <p className="task-card-place">
          {orderNumber.has(task.id) && <span className="task-card-order" title={tr('Your order')}>#{orderNumber.get(task.id)}</span>}
          {place}
        </p>
        <p className="task-card-meta">
          {task.owner} · {tr('Due')} <RelativeDate date={task.dueDate} completed={task.status === 'COMPLETED'} />
        </p>
        {task.blockerReason && <p className="task-card-blocker">{task.blockerReason}</p>}
        <div className="task-card-progress">
          <ProgressBar value={Number(task.progressPercent)} />
          <span>{Math.round(Number(task.progressPercent))}%</span>
        </div>
        <div className="task-card-foot">
          <PriorityBadge priority={task.priority} />
          {task.archived ? (
            <StatusBadge status={task.status} />
          ) : (
            <FormControl size="small" className="task-card-status">
              <Select
                SelectDisplayProps={{ 'aria-label': tr('Status of {title}', { title: task.title }) }}
                value={task.status}
                onChange={(event) => void handleMove(task.id, event.target.value as WorkStatus)}
              >
                {columns.map((column) => <MenuItem value={column} key={column}>{workStatusLabels[column]}</MenuItem>)}
              </Select>
            </FormControl>
          )}
        </div>
      </>
    );
  }

  // FR-22.4 field order: identity -> classification -> dates -> details,
  // with labeled dividers so the 14-field form scans as four short groups.
  const formFields = (
    <>
      <label>
        {tr('Step')}
        <FormControl fullWidth size="small" required>
          <Select SelectDisplayProps={{ 'aria-label': "Step" }} value={stepId} onChange={(event) => setStepId(event.target.value)}>
            {steps.map((step) => <MenuItem value={String(step.id)} key={step.id}>{step.title}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      <label>
        {tr('Title')}
        <Input value={title} onChange={(event) => setTitle(event.target.value)} required autoFocus />
      </label>
      <div className="field-full form-section">{tr('Ownership & priority')}</div>
      <label>
        {tr('Owner')}
        <Input value={owner} onChange={(event) => setOwner(event.target.value)} required />
      </label>
      <label>
        {tr('Priority')}
        <FormControl fullWidth size="small">
          <Select SelectDisplayProps={{ 'aria-label': "Priority" }} value={priority} onChange={(event) => setPriority(event.target.value as Priority)}>
            <MenuItem value="LOW">{tr('Low')}</MenuItem>
            <MenuItem value="MEDIUM">{tr('Medium')}</MenuItem>
            <MenuItem value="HIGH">{tr('High')}</MenuItem>
            <MenuItem value="CRITICAL">{tr('Critical')}</MenuItem>
          </Select>
        </FormControl>
      </label>
      <label>
        {tr('Rank')}
        <Input
          value={letterRank}
          onChange={(event) => setLetterRank(event.target.value.slice(-1).toUpperCase().replace(/[^A-Z]/, ''))}
          placeholder={tr('e.g. A')}
          maxLength={1}
        />
        <span className="field-hint">
          {tr('Optional. A, B, C… — assigned after listing everything, to mark which tasks under this step come first. Ties are fine; nothing enforces a unique order.')}
        </span>
      </label>
      <label>
        {tr('Status')}
        <FormControl fullWidth size="small">
          <Select SelectDisplayProps={{ 'aria-label': "Status" }} value={status} onChange={(event) => setStatus(event.target.value as WorkStatus)}>
            {columns.map((column) => <MenuItem value={column} key={column}>{workStatusLabels[column]}</MenuItem>)}
          </Select>
        </FormControl>
      </label>
      {status === 'BLOCKED' && (
        <label>
          {tr('What\'s missing?')}
          <FormControl fullWidth size="small">
            <Select SelectDisplayProps={{ 'aria-label': "What's missing?" }} displayEmpty value={blockerCategory} onChange={(event) => setBlockerCategory(event.target.value as ObstacleType)}>
              <MenuItem value="" disabled><em>{tr('Select a category...')}</em></MenuItem>
              {blockerCategories.map((category) => <MenuItem value={category} key={category}>{obstacleTypeLabels[category]}</MenuItem>)}
            </Select>
          </FormControl>
          {blockerCategory && (
            <span className="field-hint">
              {suggestPartnerFor(blockerCategory)?.label ?? tr('Look for a partner or connector who can help directly.')}
            </span>
          )}
          {profileByStepId.has(Number(stepId)) && (
            <span className="field-hint">
              {tr('This step already has an ideal partner profile — see the Partners page for who to recruit.')}
            </span>
          )}
        </label>
      )}
      {status === 'BLOCKED' && (
        <label className="field-full">
          {tr('Blocker Reason')}
          <Textarea value={blockerReason} onChange={(event) => setBlockerReason(event.target.value)} required />
        </label>
      )}
      <div className="field-full form-section">{tr('Dates')}</div>
      <label>
        {tr('Start Date')}
        <Input type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} />
      </label>
      <label>
        {tr('Due Date')}
        <Input type="date" value={dueDate} onChange={(event) => setDueDate(event.target.value)} required />
      </label>
      <div className="field-full form-section">{tr('Details')}</div>
      <label>
        {tr('Progress')}
        <Input type="number" min="0" max="100" value={progressPercent} onChange={(event) => setProgressPercent(Number(event.target.value))} required />
      </label>
      <label>
        {tr('Estimated Hours')}
        <Input type="number" min="0" step="0.5" value={estimatedHours} onChange={(event) => setEstimatedHours(event.target.value)} />
      </label>
      <label>
        {tr('Actual Hours')}
        <Input type="number" min="0" step="0.5" value={actualHours} onChange={(event) => setActualHours(event.target.value)} />
      </label>
      <label>
        {tr('Energy')}
        <FormControl fullWidth size="small">
          <Select SelectDisplayProps={{ 'aria-label': 'Energy' }} value={energyDemand} onChange={(event) => setEnergyDemand(event.target.value as EnergyDemand)}>
            {(Object.keys(energyDemandLabels) as EnergyDemand[]).map((value) => (
              <MenuItem value={value} key={value}>{energyDemandLabels[value]}</MenuItem>
            ))}
          </Select>
        </FormControl>
      </label>
      <label className="field-full">
        {tr('Next Action')}
        <Textarea value={nextAction} onChange={(event) => setNextAction(event.target.value)} />
      </label>
      <label className="field-full">
        Description
        <Textarea value={description} onChange={(event) => setDescription(event.target.value)} />
      </label>
    </>
  );

  return (
    <PageSection
      title={tr('Tasks Board')}
      subtitle={tr('Manage executable work by status.')}
      actions={<Button type="button" onClick={openCreateTask} disabled={steps.length === 0}>{tr('Create task')}</Button>}
    >
      <CrudModalForm
        editing={crud.editingId !== null}
        createLabel={tr('Create task')}
        editTitle="Edit Task"
        saving={crud.saving}
        disabled={steps.length === 0}
        autoOpenCreate={autoOpenCreate}
        creating={createOpen}
        onCreatingChange={setCreateOpen}
        hideTrigger
        onSubmit={handleSubmit}
        onCancelEdit={cancelEdit}
      >
        {formFields}
      </CrudModalForm>
      {energyNudge.dialog}
      {crud.loading && <Loading variant="cards" />}
      {crud.error && <ErrorMessage message={crud.error} onRetry={() => void crud.reload()} />}
      <SummaryStrip
        chips={[
          { key: 'total', label: crud.items.length === 1 ? 'task' : 'tasks', count: crud.items.length },
          { key: 'overdue', label: 'overdue', count: crud.items.filter((task) => isOverdue(task.dueDate, task.status)).length, tone: 'critical', active: filterOverdueOnly, onClick: () => setFilterOverdueOnly(!filterOverdueOnly) },
          { key: 'blocked', label: 'blocked', count: crud.items.filter((task) => task.status === 'BLOCKED').length, tone: 'warning', active: filterStatus === 'BLOCKED', onClick: () => setFilterStatus(filterStatus === 'BLOCKED' ? '' : 'BLOCKED') },
          { key: 'completed', label: 'completed', count: crud.items.filter((task) => task.status === 'COMPLETED').length, tone: 'positive', active: filterStatus === 'COMPLETED', onClick: () => setFilterStatus(filterStatus === 'COMPLETED' ? '' : 'COMPLETED') },
          { key: 'preset-mine', label: tr('· My overdue'), count: crud.items.filter((task) => task.owner === (user?.fullName ?? '') && isOverdue(task.dueDate, task.status)).length, active: Boolean(filterOwner) && filterOverdueOnly, onClick: applyMyOverdue },
          { key: 'preset-week', label: tr('· Due this week'), count: crud.items.filter((task) => { const d = new Date(`${task.dueDate}T00:00:00`); const now = new Date(); const end = new Date(now); end.setDate(now.getDate() + 7); return d >= new Date(now.getFullYear(), now.getMonth(), now.getDate()) && d <= end && task.status !== 'COMPLETED'; }).length, active: Boolean(filterDueFrom && filterDueTo), onClick: applyDueThisWeek },
        ]}
      />
      <FilterPanel activeCount={[searchTerm, filterOwner, filterPriority, filterLetterRank, filterStatus, filterVisionAreaId, filterDreamId, filterGoalId, filterDueFrom, filterDueTo, filterOverdueOnly].filter(Boolean).length}>
        <SearchBar value={searchTerm} onChange={setSearchTerm} entityLabel="tasks" />
        <label>
          {tr('Owner')}
          <Input value={filterOwner} onChange={(event) => setFilterOwner(event.target.value)} placeholder={tr('Any owner')} />
        </label>
        <FilterSelect
          label={tr('Priority')}
          value={filterPriority}
          onChange={setFilterPriority}
          options={optionsFromLabels(priorityLabels)}
        />
        {letterRankOptions.length > 0 && (
          <FilterSelect
            label={tr('Rank')}
            value={filterLetterRank}
            onChange={setFilterLetterRank}
            options={letterRankOptions}
          />
        )}
        <FilterSelect
          label={tr('Status')}
          value={filterStatus}
          onChange={setFilterStatus}
          options={optionsFromLabels(workStatusLabels)}
        />
        <FilterSelect
          label={tr('Vision Area')}
          value={filterVisionAreaId}
          onChange={(value) => setFilterBatch({ visionAreaId: value, dreamId: '', goalId: '' })}
          options={optionsFromEntities(visionAreas, (area) => area.name)}
        />
        <FilterSelect
          label={tr('Dream')}
          value={filterDreamId}
          onChange={(value) => setFilterBatch({ dreamId: value, goalId: '' })}
          options={optionsFromEntities(dreamsForFilter, (dream) => dream.title)}
        />
        <FilterSelect
          label={tr('Goal')}
          value={filterGoalId}
          onChange={setFilterGoalId}
          options={optionsFromEntities(goalsForFilter, (goal) => goal.title)}
        />
        <label>
          {tr('Due from')}
          <Input type="date" value={filterDueFrom} onChange={(event) => setFilterDueFrom(event.target.value)} />
        </label>
        <label>
          {tr('Due to')}
          <Input type="date" value={filterDueTo} onChange={(event) => setFilterDueTo(event.target.value)} />
        </label>
        <label className="checkbox-field">
          <Checkbox checked={filterOverdueOnly} onChange={(event) => setFilterOverdueOnly(event.target.checked)} />
          {tr('Overdue only')}
        </label>
        <ShowArchivedToggle checked={crud.showArchived} onToggle={crud.toggleShowArchived} />
      </FilterPanel>
      {filterStepId && (
        <Card className="filter-banner flex-row">
          <span>{tr('Showing tasks for step:')} <strong>{steps.find((step) => String(step.id) === filterStepId)?.title ?? filterStepId}</strong></span>
          <Link to="/tasks">{tr('Clear filter')}</Link>
        </Card>
      )}
      <div className="list-controls">
        {crud.items.length > 0 && steps.length > 0 && (
          <QuickAddRow
            parentLabel={tr('Step')}
            parents={steps.map((step) => ({ value: String(step.id), label: step.title }))}
            parentValue={quickParentId}
            onParentChange={setQuickParentId}
            placeholder={tr('New task title')}
            withDueDate
            onAdd={handleQuickAdd}
          />
        )}
        <ViewToggle value={viewMode} onChange={setViewMode} label={tr('Task view')} />
      </div>
      {!crud.loading && crud.items.length === 0 ? (
        <EmptyState
          headline={tr('No tasks yet')}
          icon={CheckSquare}
          action={
            steps.length === 0 ? (
              <Button type="button" onClick={() => navigate('/steps?create=step')}>{tr('Create a step first')}</Button>
            ) : (
              <Button type="button" onClick={() => setCreateOpen(true)}>{tr('Create your first task')}</Button>
            )
          }
        >
          {tr('Tasks are the small executable actions that move a step forward — each with an owner, due date, and priority.')}
        </EmptyState>
      ) : null}
      {crud.items.length > 0 && (viewMode === 'list' ? (
        <Card className="tasks-list-card">
          <CardContent>
            <DataTable
              storageKey="tasks"
              rows={visibleTasks}
              columns={taskColumns}
              emptyMessage={tr('No tasks match these filters.')}
              defaultSortKey="position"
              defaultSortDirection="asc"
              reorder={{
                columnKey: 'position',
                onReorder: (draggedId, targetId) => void handleReorderTasks(draggedId, targetId),
                // Any task can go anywhere; archived tasks keep no place in the order.
                canDrag: (id) => !crud.items.find((item) => item.id === id)?.archived,
                canDrop: (_draggedId, targetId) => !crud.items.find((item) => item.id === targetId)?.archived,
              }}
              pageResetKey={`${searchTerm}|${filterOwner}|${filterPriority}|${filterLetterRank}|${filterStatus}|${filterVisionAreaId}|${filterDreamId}|${filterGoalId}|${filterDueFrom}|${filterDueTo}|${filterOverdueOnly}|${filterStepId ?? ''}`}
              rowClassName={(task) => (task.archived ? 'row-archived' : isOverdue(task.dueDate, task.status) ? 'row-overdue' : task.status === 'COMPLETED' ? 'row-done' : '')}
              mobileCard={taskMobileCard}
              selection={{
                selectedIds,
                onChange: setSelectedIds,
                rowLabel: (task) => task.title,
                actions: (
                  <>
                    <label>
                      {tr('Set status')}
                      <FormControl fullWidth size="small">
                        <Select SelectDisplayProps={{ 'aria-label': 'Set status' }} value={bulkStatus} onChange={(event) => setBulkStatus(event.target.value as WorkStatus)}>
                          {columns.map((column) => <MenuItem value={column} key={column}>{workStatusLabels[column]}</MenuItem>)}
                        </Select>
                      </FormControl>
                    </label>
                    <Button type="button" onClick={() => void applyBulk('status')} disabled={bulkApplying}>{tr('Apply')}</Button>
                    <label>
                      {tr('Set priority')}
                      <FormControl fullWidth size="small">
                        <Select SelectDisplayProps={{ 'aria-label': 'Set priority' }} value={bulkPriority} onChange={(event) => setBulkPriority(event.target.value as Priority)}>
                          <MenuItem value="LOW">{tr('Low')}</MenuItem>
                          <MenuItem value="MEDIUM">{tr('Medium')}</MenuItem>
                          <MenuItem value="HIGH">{tr('High')}</MenuItem>
                          <MenuItem value="CRITICAL">{tr('Critical')}</MenuItem>
                        </Select>
                      </FormControl>
                    </label>
                    <Button type="button" onClick={() => void applyBulk('priority')} disabled={bulkApplying}>{tr('Apply')}</Button>
                    <BulkArchiveAction
                      selectedIds={selectedIds}
                      items={crud.items}
                      entityLabel={tr('task(s)')}
                      onArchive={async (ids) => {
                        await crud.archiveMany(ids);
                        setSelectedIds(new Set());
                      }}
                      onRestore={async (ids) => {
                        await crud.restoreMany(ids);
                        setSelectedIds(new Set());
                      }}
                      onDeletePermanently={async (ids) => {
                        await crud.permanentlyDeleteMany(ids);
                        setSelectedIds(new Set());
                      }}
                    />
                  </>
                ),
              }}
            />
          </CardContent>
        </Card>
      ) : (
        <StatusBoard
          items={visibleTasks}
          columns={visibleColumns.map((column) => ({ value: column, label: workStatusLabels[column] }))}
          moveOptions={columns.map((column) => ({ value: column, label: workStatusLabels[column] }))}
          statusOf={(task) => task.status}
          entityLabel="tasks"
          onMove={(task, nextStatus) => void handleMove(task.id, nextStatus)}
          onReorder={(draggedId, targetId) => void handleReorderTasks(draggedId, targetId)}
          cardClassName={taskHighlightClass}
          renderCard={(task) => (
            <>
              <strong>{task.title}</strong>
              <p>{task.owner} · Due <RelativeDate date={task.dueDate} completed={task.status === 'COMPLETED'} /></p>
              <div className="inline-meta">
                <PriorityBadge priority={task.priority} />
                <span>{task.progressPercent}%</span>
              </div>
              <ProgressBar value={Number(task.progressPercent)} />
              {task.blockerReason && <p className="task-card-blocker">{task.blockerReason}</p>}
              {task.status === 'BLOCKED' && profileByStepId.has(task.stepId) && (
                <p><Link to="/partners">{tr('Ideal partner profile defined for this step →')}</Link></p>
              )}
            </>
          )}
          cardActions={(task) => (
            <RowActionsMenu
              onEdit={() => startEdit(task)}
              onArchive={() => void crud.archive(task.id)}
              onRestore={() => void crud.restore(task.id)}
              onDeletePermanently={() => void crud.permanentlyDelete(task.id)}
              archived={task.archived}
              label={tr('Task actions')}
            />
          )}
        />
      ))}
    </PageSection>
  );
}

// "actual / estimated h" — either side may be missing.
function formatHours(task: TaskItem) {
  if (task.estimatedHours == null && task.actualHours == null) {
    return '-';
  }
  return `${task.actualHours ?? 0} / ${task.estimatedHours ?? '?'} h`;
}

// The user's overall order (listOrder), unordered tasks last, oldest first.
function byListOrder(left: TaskItem, right: TaskItem) {
  return (left.listOrder ?? Number.MAX_SAFE_INTEGER) - (right.listOrder ?? Number.MAX_SAFE_INTEGER) || left.id - right.id;
}

function taskHighlightClass(task: TaskItem) {
  if (isOverdue(task.dueDate, task.status)) {
    return 'list-card--overdue';
  }
  if (task.status === 'BLOCKED') {
    return 'list-card--blocked';
  }
  return '';
}
